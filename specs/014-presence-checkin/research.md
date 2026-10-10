# Research: On-Site Presence Check-In

**Feature**: `014-presence-checkin` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

The Technical Context has no open `NEEDS CLARIFICATION` items. The decisions below settle the design choices the spec deliberately left to planning.

## Decision 1: One room-scoped check-in endpoint for QR code and NFC

**Decision**: Add `POST /api/rooms/{roomId}/check-in` with the body `{ "method": "QR" | "NFC" }`. The server picks the booking itself: the `RESERVED` reservation for that room whose check-in window (`startTime <= now <= startTime + 5 min` and `now < endTime`) is open. For a non-admin caller, the reservation must also be owned by the caller (`createdByUserId`). Administrators may confirm whichever reservation is in its window. The QR code and NFC link both open the SPA route `/rooms/{roomId}/check-in?method=qr|nfc`. That page shows the matching booking and confirms it with a single button press.

**Rationale**: The QR code and NFC tag only know the room (FR-002), not the reservation, so the endpoint has to be room-scoped. Because conflict detection forbids overlapping `RESERVED`/`ACTIVE` reservations in a room, at most one reservation can be in its window at any moment. Selection is therefore unambiguous. Requiring a button press instead of checking in automatically on page load stops link previews and prefetching from checking a booking in by accident. It still fits SC-001 (under 15 s).

**Alternatives considered**: Encoding the reservation id in the code was rejected because a posted code must stay valid for every booking. Reusing `POST /api/reservations/{id}/activate` from the check-in page was rejected because the client would first have to find the reservation id, and that endpoint does not enforce the on-site window or record the method. Checking in automatically on page load was rejected (see above).

## Decision 2: One check-in window for every method (revised 2026-10-10)

**Decision**: QR code, NFC and the existing manual `POST /api/reservations/{id}/activate` all use the same window, implemented once in `CheckInWindow`: from 10 minutes before the start (`EARLY_CHECK_IN_PERIOD`) to 5 minutes after it, before the end, and before the start only while no other reservation of the room is ongoing. An `ACTIVE` reservation counts as in use from `coalesce(checked_in_at, start_time)`.

**Rationale**: The first version kept manual activation unrestricted. That let users check in hours early, which left the booking `ACTIVE` while the room display, the room status and device control (all keyed to the start time) still treated it as not started — the bug reported on 2026-10-10. One shared window plus "in use from check-in" keeps every view consistent.

**Alternatives considered**: Keeping manual activation unrestricted but moving the start time to the check-in time was rejected because it rewrites the booked interval and affects conflict detection and statistics.

## Decision 3: Room automation as a direct service call, not events

**Decision**: Add a new `RoomAutomationService` with `prepare(Reservation)` and `release(Reservation)`. `ReservationService` calls `prepare` after a successful `RESERVED → ACTIVE` transition (check-in and manual activation). It calls `release` after every `ACTIVE → COMPLETED|CANCELLED` transition (sweep, manual completion, cancellation). Each device command runs separately: a failure is caught, logged as `room_automation_device_failed roomId=… kind=… phase=prepare|release`, and returned in an `AutomationResult`. The booking status change is never rolled back because of a device (FR-010). `release` does nothing when another reservation for the same room is already `ACTIVE` (FR-008). `prepare` and `release` set only the target state and skip the gateway call when the stored state already matches, so repeated triggers are idempotent (FR-011).

**Rationale**: A direct call is the simplest option (constitution V). It is easy to test with the existing `Clock` and a fake gateway, and it lets the check-in response tell the user which device failed (US2 scenario 3). With the stub gateway, "after commit" semantics add no value.

**Alternatives considered**: Spring `ApplicationEvent` plus `@TransactionalEventListener(AFTER_COMMIT)` was rejected. It adds indirection, and the device state writes would need their own transaction. It would also make reporting failed devices back in the same response much harder. It is the natural evolution once a real hardware adapter with network latency exists.

## Decision 4: Door as a fourth device kind on the existing stub

**Decision**: Add `DOOR` to `RoomDeviceKind`, plus migration `V14` to extend the `room_device_state.kind` check constraint. The existing boolean `state` stores `true = unlocked`, `false = locked`. A door without a stored row counts as locked. The door appears in the existing device controls for the booking user, as lighting and ventilation do. Automation goes through a new internal method `RoomDeviceService.apply(roomId, kind, state)`, which skips the user authorization but uses the same gateway and saves the state only after acknowledgement. `setState` (the user command) delegates to it after authorizing.

**Rationale**: This reuses the whole feature 006 stub (`RoomDeviceGateway` → `PersistedRoomDeviceGateway`) as required by FR-018. A boolean keeps the table unchanged; only the API translates it into `LOCKED`/`UNLOCKED` for readability.

**Alternatives considered**: A separate `door_state` table or a new gateway was rejected as duplicating the existing mechanism. A string `state` column was rejected because it would need a data migration for existing rows with no benefit.

## Decision 5: Read-only room status endpoint that never writes

**Decision**: Add `GET /api/rooms/{roomId}/status`, available to every signed-in user. It returns:
- `status`: `AVAILABLE | RESERVED | OCCUPIED`, using the same rule as `deriveRoomDisplayStatus` from feature 009: `OCCUPIED` if an `ACTIVE` reservation covers now, `RESERVED` if a `RESERVED` one does, otherwise `AVAILABLE`.
- `lighting` and `ventilation` as on/off.
- `door` as `LOCKED`/`UNLOCKED`.
- `lastPresenceAt`, filled only for administrators.

It contains no names or booking details. Missing device rows are reported with their defaults (off/locked) and are **not** created. This avoids the readOnly-write problem noted as F8 in `docs/SYSTEMDOKUMENTATION.md` §11. The display badge keeps its existing client-side derivation; a backend test pins the same rule table, so both stay consistent.

**Rationale**: This implements FR-017 and FR-020 with a single endpoint that a future e-ink display or LED controller can poll, without any change to the backend. Sign-in follows the clarified answer (signed-in users only).

**Alternatives considered**: Extending `GET /device-controls` was rejected because it is authorized for the booking user only and lists controls rather than status. Switching the display badge to the backend status was rejected because it would re-open the feature 009 logic that was just fixed (#35).

## Decision 6: Presence data stored on the reservation; rejections only logged

**Decision**: Add nullable columns to `reservation`:
- `check_in_method` (`QR | NFC | MANUAL`)
- `checked_in_at`
- `checked_in_by_user_id`
- `last_presence_at`

Accepted check-ins fill the first three. Rejected check-in attempts are logged in structured form only (`check_in_rejected roomId=… reason=TOO_EARLY|EXPIRED|ALREADY_ACTIVE|NO_MATCH method=…`), with no user identifiers beyond the existing `AccessDeniedLog` conventions.

**Rationale**: FR-013 asks for method, actor and time of the accepted check-in, and one row per reservation covers it. The spec's Check-In Record lives on the reservation; rejected attempts are only logged, which fits constitution V and stays greppable from `docker compose logs`.

**Alternatives considered**: A `check_in_attempt` table was rejected under YAGNI and can be added when statistics need it. Storing presence as an event history was rejected because only the latest time is required (FR-016).

## Decision 7: Simulated motion sensor via an admin-area endpoint

**Decision**: Add `POST /api/admin/rooms/{roomId}/presence-events`. It lives in the admin area, so `RoleAccessFilter` already requires the ADMIN role (like every other `/api/admin/**` route; administration mode is enforced only in the frontend, see feature 013). The frontend shows the button only while administration mode is on. While a reservation for the room is `ACTIVE` and covers now, the endpoint sets `last_presence_at = now`. Otherwise it records nothing. It never changes a status or a device. The response says whether a presence was recorded and returns `lastPresenceAt`. The frontend shows "Bewegung simulieren" on the room display and room detail page only while administration mode is on.

**Rationale**: This satisfies FR-015, FR-016 and FR-019 with no new authorization mechanism. A later real sensor adapter will call the same service method from its own device-authenticated entry point.

**Alternatives considered**: A user-area endpoint guarded by a role check inside the service was rejected because the admin area already provides the right guard and the right log behaviour.

## Decision 8: QR code generated in the browser with `qrcode`

**Decision**: Add the npm package `qrcode` (MIT, no runtime dependencies besides small helpers) to the frontend. It renders an SVG of the check-in link on the room display and on an admin print section of the room detail page. The NFC link (`…/check-in?method=nfc`) is shown as copyable text next to it. Links are built from `window.location.origin`, so they work in every environment without backend configuration.

**Rationale**: A real, scannable code is required (FR-014), and generating it on the client keeps the backend unchanged. Under the constitution's stack rules this is a compatible addition within the frontend layer, so it must be named in the PR description.

**Alternatives considered**: Backend generation with ZXing was rejected because it adds a Java dependency, an image endpoint and a configured public base URL. Hand-writing a QR encoder was rejected as error-prone. A third-party QR web service was rejected because it leaks room ids to an external service.

## Decision 9: Login returns to the check-in link

**Decision**: `RequireAuth` stores `pathname + search` in `state.from`. After a successful login, `LoginPage` navigates to `from` when it is a same-app path (starts with `/`, not `//`); otherwise it navigates to `/`.

**Rationale**: US1 scenario 2 requires users to land back in the check-in flow after signing in. Today `LoginPage` always navigates to `/`, and `RequireAuth` drops the query string, which would lose `?method=nfc`. Restricting the target to same-app paths prevents open redirects.

**Alternatives considered**: Storing the target in `sessionStorage` was rejected because router state already exists for this purpose.

## Decision 10: Display refresh stays at 30 s

**Decision**: The room display fetches `/status` together with its existing 30 s refresh. After a check-in, the check-in page shows the new device states from its own response immediately. SC-002 is measured against "one display refresh interval" (≤ 30 s).

**Rationale**: This matches the clarified success criterion and the existing polling. It is also a realistic cadence for a future e-ink panel.

**Alternatives considered**: Server push (SSE or WebSocket) was rejected as unnecessary for the concept demo and incompatible with simple polling devices.

**Addendum (implementation)**: A failed `/status` request does not make the room display "unavailable": the display keeps showing the room, its reservations and the QR code, and only omits the device icons until the next refresh succeeds (`RoomDisplayPage` catches the status request on its own). The device icons are optional information on top of the existing display (feature 009), so an error in the newer endpoint must not take down a page that worked before. Room and reservation errors still mark the display unavailable as before.
