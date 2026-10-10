# Tasks: On-Site Presence Check-In

**Input**: Design documents from `/specs/014-presence-checkin/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [API contract](./contracts/presence-checkin-api.yaml), [quickstart.md](./quickstart.md)

**Tests**: Required by the project constitution (Principle I, non-negotiable). Write every test task first, watch it fail, and only then do the matching implementation task (Red-Green-Refactor).

**Conventions used below**
- Backend root: `backend/src/main/java/at/mci/igp/raumlotse/` (abbreviated `B/`); backend tests: `backend/src/test/java/at/mci/igp/raumlotse/` (abbreviated `T/`). Write full paths in code.
- Use the injected `java.time.Clock` (see `ClockConfig`) for every "now". Tests pin time with `Clock.fixed(...)`.
- Check-in window (data-model.md): `startTime <= now <= startTime + ReservationPolicyConstants.CHECK_IN_GRACE_PERIOD` **and** `now < endTime`.
- Device semantics (data-model.md): `state = true` means on for LIGHTING/VENTILATION/PROJECTOR and **unlocked** for DOOR. A missing `room_device_state` row means off or locked.
- German user-facing messages, matching existing services.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US5 from spec.md)

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add the npm dependency `qrcode` and dev dependency `@types/qrcode` in `frontend/package.json` and `frontend/package-lock.json` (`npm install qrcode && npm install -D @types/qrcode`). Note in the PR description that this is a compatible frontend-layer addition (research Decision 8)
- [X] T002 [P] Validate `specs/014-presence-checkin/contracts/presence-checkin-api.yaml` as OpenAPI 3.0.3 (for example with `npx @redocly/cli lint`) and fix any structural errors before implementing endpoints

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, enums and entity fields that every story needs.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Add failing Testcontainers test `T/PresenceCheckInMigrationIntegrationTest.java` (extend `AbstractIntegrationTest`) asserting that after migration:
  - `reservation` has nullable columns `check_in_method` (`TEXT`, CHECK `IN ('QR','NFC','MANUAL')`), `checked_in_at` (`TIMESTAMPTZ`), `checked_in_by_user_id` (`UUID`) and `last_presence_at` (`TIMESTAMPTZ`).
  - Inserting `room_device_state.kind = 'DOOR'` succeeds, while `'WINDOW'` is still rejected.
  - Existing reservation rows keep `NULL` in all four columns.
- [X] T004 [P] Check `T/MigrationVersionTest.java` for assertions on the latest migration version or the number of migrations, and update them to include `V14`; if it asserts neither, leave it unchanged
- [X] T005 Create `backend/src/main/resources/db/migration/V14__presence_checkin.sql`:
  - `ALTER TABLE reservation ADD COLUMN check_in_method TEXT NULL CHECK (check_in_method IN ('QR','NFC','MANUAL'))`, `ADD COLUMN checked_in_at TIMESTAMPTZ NULL`, `ADD COLUMN checked_in_by_user_id UUID NULL` and `ADD COLUMN last_presence_at TIMESTAMPTZ NULL`.
  - Drop and recreate the `room_device_state` kind check constraint (look up its actual name from `V11__create_room_device_control.sql`, or from `pg_constraint` if it is unnamed) as `CHECK (kind IN ('LIGHTING','VENTILATION','PROJECTOR','DOOR'))`.
- [X] T006 [P] Create enum `B/domain/CheckInMethod.java` with values `QR`, `NFC`, `MANUAL`
- [X] T007 [P] Add `DOOR` to `B/domain/RoomDeviceKind.java` (after `PROJECTOR`); add `'DOOR'` to `RoomDeviceKind` in `frontend/src/types/roomDevice.ts`
- [X] T008 [P] Extend the device-kind lists in `specs/006-room-device-control/contracts/room-device-control-api.yaml` (both occurrences) with `DOOR`, and document that `state = true` means unlocked for DOOR. Additive change, no version bump (constitution III)
- [X] T009 Extend `B/domain/Reservation.java` with mapped fields:
  - `@Enumerated(EnumType.STRING) @Column(name = "check_in_method") CheckInMethod checkInMethod`
  - `@Column(name = "checked_in_at") Instant checkedInAt`
  - `@Column(name = "checked_in_by_user_id") UUID checkedInByUserId`
  - `@Column(name = "last_presence_at") Instant lastPresenceAt`

  Add getters, plus a single method `recordCheckIn(CheckInMethod method, UUID actorUserId, Instant at)` that sets all three check-in fields together. Add a setter for `lastPresenceAt`.
- [X] T010 Add repository queries to `B/repository/ReservationRepository.java` (JPQL with `@Param`, no string building):
  - `findCheckInCandidates(roomId, dayStart, dayEnd)`: reservations of the room with status in (`RESERVED`, `ACTIVE`, `EXPIRED`) and `startTime >= :dayStart and startTime < :dayEnd`, ordered by `startTime`. Used to classify outcomes for the current day.
  - `findActiveCovering(roomId, now)`: status `ACTIVE` and `startTime <= now < endTime`.
  - `existsByRoomIdAndStatusAndIdNot(roomId, ACTIVE, excludedId)`, or an equivalent JPQL query.

  Cover each with a failing test first in `T/repository/ReservationRepositoryPresenceTest.java` (Testcontainers).

**Checkpoint**: Migration applies, entities map, queries return the expected rows.

---

## Phase 3: User Story 1 – Confirm Presence by Scanning the Room's QR Code (Priority: P1) 🎯 MVP

**Goal**: A signed-in owner (or administrator) opens the room's QR link, sees the matching booking and confirms it. The booking becomes `ACTIVE` with method `QR` recorded.

**Independent Test**: Book room R starting now as user U, open `/rooms/R/check-in?method=qr`, and press "Anwesenheit bestätigen". The reservation becomes `ACTIVE`, with `checkInMethod = QR` and `checkedInByUserId = U`. Another non-admin user gets "Keine passende Buchung" and nothing changes (quickstart Scenarios 1 and 3).

### Tests for User Story 1 ⚠️ write first, watch them fail

- [X] T011 [P] [US1] Failing unit tests in `T/service/CheckInServiceTest.java` (Mockito repositories, fixed `Clock`) covering:
  - (a) Owner within window: the booking becomes `ACTIVE`, and `recordCheckIn(QR, ownerId, now)` is called.
  - (b) Exact boundaries: `now == startTime` is accepted, `now == startTime + 5 min` is accepted, `now == startTime + 5 min + 1 ns` gives `EXPIRED`, and `now >= endTime` gives `EXPIRED`.
  - (c) `now < startTime` gives `TOO_EARLY`, with `checkInOpensAt = startTime`.
  - (d) A non-owner non-admin gives `NO_MATCH`, the booking stays `RESERVED`, and `save` is never called.
  - (e) An admin confirms another user's booking, and `checkedInByUserId = adminId`.
  - (f) An already `ACTIVE` booking gives `alreadyActive = true`, with no save and no second `recordCheckIn`.
  - (g) An unknown or inactive room gives `NotFoundException`.
  - (h) Preview returns `READY`, `ALREADY_ACTIVE`, `TOO_EARLY`, `EXPIRED` or `NO_MATCH` without saving.
  - (i) The owner's only booking is tomorrow at the same time gives `NO_MATCH`; the owner's booking expired yesterday gives `NO_MATCH`.
- [X] T012 [P] [US1] Failing MockMvc tests in `T/controller/CheckInControllerTest.java`:
  - `GET /api/rooms/{id}/check-in` returns `200 CheckInPreview`.
  - `POST` with `{"method":"QR"}` and CSRF returns `200 CheckInResult`.
  - A missing or unknown `method` returns `400`.
  - Anonymous requests return `401`.
  - `TOO_EARLY`, `EXPIRED` and `NO_MATCH` return `409` with `Problem.code` equal to the reason.
  - Shapes must match `specs/014-presence-checkin/contracts/presence-checkin-api.yaml`.
- [X] T013 [P] [US1] Update `T/security/AuthorizationMatrixTest.java` and/or `T/security/ProtectedBusinessRouteTest.java` with failing expectations: `POST /api/rooms/{id}/check-in` is allowed for a plain signed-in user (non-admin, admin mode off) and returns `401` when anonymous
- [X] T014 [P] [US1] Failing test in `T/service/ReservationServiceTest.java`: `activateReservation` records `checkInMethod = MANUAL`, `checkedInByUserId = actor.userId()` and `checkedInAt = now`
- [X] T015 [P] [US1] Failing frontend tests:
  - `frontend/src/utils/checkInLink.test.ts`: `checkInLink(roomId, 'qr')` returns `${origin}/rooms/${roomId}/check-in?method=qr`, and `'nfc'` likewise.
  - `frontend/src/API/checkIn.test.ts`: GET preview and POST `{method}` to the contract paths.
- [X] T016 [P] [US1] Failing component tests in `frontend/src/pages/CheckInPage.test.tsx`:
  - `READY` shows the room name, start–end time and an "Anwesenheit bestätigen" button.
  - Clicking it POSTs `{method:'QR'}` and shows "Anwesenheit bestätigt".
  - `TOO_EARLY` shows "Check-in ab HH:MM möglich".
  - `EXPIRED` and `NO_MATCH` show explanatory texts.
  - `ALREADY_ACTIVE` shows "Bereits in Nutzung".
  - `?method=nfc` makes the POST send `NFC`; a missing or unknown `method` defaults to `QR`.
  - No POST happens on page load.
- [X] T017 [P] [US1] Failing tests for returning after login:
  - In `frontend/src/pages/LoginPage.test.tsx`: after a successful login with `state.from = '/rooms/r1/check-in?method=nfc'`, the app navigates there.
  - With `from = '//evil.example'`, `'https://x'` or `undefined`, it navigates to `/`.
  - In `frontend/src/auth/RequireAuth` tests (create `frontend/src/auth/RequireAuth.test.tsx` if absent): `from` includes `location.search`.
- [X] T018 [P] [US1] Failing component test in `frontend/src/components/CheckInQrCode/CheckInQrCode.test.tsx`: renders an `<svg>` (or `img` with an SVG data URL) with an accessible label "QR-Code für den Check-in in {roomName}" for the given link

### Implementation for User Story 1

- [X] T019 [P] [US1] Create DTO records in `B/dto/`:
  - `CheckInRequest` (`@NotNull CheckInMethod method`; reject `MANUAL` with `400`, since only `QR`/`NFC` are allowed).
  - `CheckInPreviewResponse` (`roomId`, `roomName`, `outcome`, nullable `reservation{id,startTime,endTime,reservedFor}`, nullable `checkInOpensAt`).
  - `CheckInResultResponse` (`reservationId`, `status`, `alreadyActive`, `checkInMethod`, `checkedInAt`, `devices`, `failedDevices`).

  For US1, return `devices` as currently stored and `failedDevices = []`; US2 fills them.
- [X] T020 [US1] Implement `B/service/CheckInService.java` (constructor injection: `RoomRepository`, `ReservationRepository`, `Clock`).
  - **`preview(roomId, Actor)` and `checkIn(roomId, CheckInMethod, Actor)`**: load the active room (else `NotFoundException`). Get candidates via `findCheckInCandidates(roomId, dayStart, dayEnd)`, using the start and end of the current day in `Europe/Berlin` (reuse `AdminStatisticsPeriod.ZONE`) derived from the injected `Clock`, filtered to those the actor may manage (`ReservationAccessPolicy.canManage`).
  - **Classify in this order:**
    1. An `ACTIVE` reservation covering now gives `ALREADY_ACTIVE`.
    2. A `RESERVED` reservation in the window gives `READY`.
    3. A `RESERVED` reservation with `startTime > now` (the nearest one) gives `TOO_EARLY`.
    4. A matching `EXPIRED` reservation, or a `RESERVED` one past its window, gives `EXPIRED`.
    5. Otherwise, `NO_MATCH`.
  - **On `READY`:** set status `ACTIVE`, call `recordCheckIn(method, actor.userId(), now)` and save. On `OptimisticLockingFailureException`, re-read and reclassify.
  - **Logs:** `check_in_accepted roomId={} reservationId={} method={}`, and `check_in_rejected roomId={} method={} reason={}` (no names or e-mail).
  - **Rejections:** throw a new `CheckInRejectedException(reason, germanDetail)`.
- [X] T021 [US1] Add `B/exception/CheckInRejectedException.java` and map it in `B/exception/GlobalExceptionHandler.java` to `409` via `Problem.of(409, "Check-in nicht möglich", detail, reason.name())`
- [X] T022 [US1] Create `B/controller/CheckInController.java` with `GET` and `POST /api/rooms/{roomId}/check-in`. Resolve `Actor` the same way `ReservationController` does (`actor(authentication)`), use `@Valid @RequestBody CheckInRequest`, and delegate to `CheckInService`
- [X] T023 [US1] Add `UserAction.of("POST", "/api/rooms/[^/]+/check-in")` to `USER_ACTIONS` in `B/service/RoleAccessFilter.java`
- [X] T024 [US1] In `B/service/ReservationService.java#activateReservation`, call `reservation.recordCheckIn(CheckInMethod.MANUAL, actor.userId(), now)` before saving
- [X] T025 [P] [US1] Create `frontend/src/utils/checkInLink.ts` (`checkInLink(roomId: string, method: 'qr' | 'nfc'): string` using `window.location.origin`), `frontend/src/types/checkIn.ts` (types mirroring the contract schemas) and `frontend/src/API/checkIn.ts` (`getCheckInPreview(roomId)`, `checkIn(roomId, method: 'QR' | 'NFC')` via `apiRequest`)
- [X] T026 [US1] Create `frontend/src/pages/CheckInPage.tsx`:
  - Read `roomId` from params and `method` from the query (`nfc` gives `NFC`, everything else `QR`).
  - Load the preview on mount and render each outcome as in T016.
  - The confirm button calls `checkIn`, is disabled while busy, and shows `formatApiError` messages on failure.
  - Register the route `/rooms/:roomId/check-in` inside `<RequireAuth>` in `frontend/src/App.tsx`.
- [X] T027 [US1] Return to the original page after login:
  - In `frontend/src/auth/RequireAuth.tsx`, set `state={{ from: location.pathname + location.search }}`.
  - In `frontend/src/pages/LoginPage.tsx`, after a successful login navigate to `from` when `typeof from === 'string' && from.startsWith('/') && !from.startsWith('//')`, otherwise to `/`. Apply this to both navigate calls (lines ~50 and ~75) where they handle a completed login.
- [X] T028 [P] [US1] Create `frontend/src/components/CheckInQrCode/CheckInQrCode.tsx`. Props: `link` and `roomName`. Generate an SVG string with `QRCode.toString(link, { type: 'svg', margin: 1 })` from `qrcode` inside an effect, and render it in a `role="img"` container with an `aria-label`. Show nothing until it is ready.
- [X] T029 [US1] Show the QR code on the room display: add an optional `checkInLink` prop to `frontend/src/components/RoomDisplay/RoomDisplay.tsx` (type in `roomDisplayTypes.ts`). Render `<CheckInQrCode>` with the caption "Zum Einchecken scannen" when it is set and `state !== 'unavailable'`. Pass `checkInLink(roomId, 'qr')` from `frontend/src/pages/RoomDisplayPage.tsx`. Extend `RoomDisplay.test.tsx` and `RoomDisplayPage.test.tsx` accordingly.
- [X] T030 [US1] Add an admin print section to `frontend/src/pages/RoomDetailPage.tsx`, shown only when `adminMode` is on: heading "Check-in-Codes", `<CheckInQrCode link={checkInLink(id,'qr')}>`, a "Drucken" button (`window.print()`), and print CSS in `RoomDetailPage.css` that hides everything except this section when printing (FR-014). Extend `RoomDetailPage.test.tsx` (hidden without admin mode, shown with it).

**Checkpoint**: US1 is fully functional and testable on its own (quickstart Scenarios 1–3 without the device expectations).

---

## Phase 4: User Story 2 – Room Is Prepared Automatically When a Booking Goes Into Use (Priority: P1)

**Goal**: Every `RESERVED → ACTIVE` transition switches lighting and ventilation on and unlocks the door through the stub gateway. The room display shows "Belegt" plus device icons. The door appears in the device controls.

**Independent Test**: Activate a booking (QR or manual). `GET /api/rooms/R/status` returns `OCCUPIED`, `lighting:true`, `ventilation:true` and `door:UNLOCKED`. The display shows the icons within 30 s, and `/rooms/R/control` lists "Tür".

### Tests for User Story 2 ⚠️ write first, watch them fail

- [X] T031 [P] [US2] Failing tests in `T/service/RoomDeviceServiceTest.java`:
  - New `apply(roomId, kind, state)` calls `gateway.setState` and saves the state.
  - A gateway exception throws `DeviceOperationException` and leaves the stored state unchanged.
  - When the stored state already equals the target, `apply` does not call the gateway (idempotent).
  - `getControls` lists `DOOR` for every room, and `setState(DOOR, true)` works for the booking user.
- [X] T032 [P] [US2] Failing tests in `T/service/RoomAutomationServiceTest.java` (mock `RoomDeviceService`):
  - `prepare(reservation)` applies LIGHTING=true, VENTILATION=true and DOOR=true, and never touches PROJECTOR.
  - If DOOR throws, LIGHTING and VENTILATION are still applied, the result has `failedDevices = [DOOR]`, and `room_automation_device_failed roomId=… reservationId=… kind=DOOR phase=prepare` is logged. Verify that the log line contains `roomId`, `reservationId` and `kind`, using the log-capturing approach already used in `T/AuthenticationLoggingTest.java`.
- [X] T033 [P] [US2] Failing tests in `T/service/ReservationServiceTest.java` and `T/service/CheckInServiceTest.java`:
  - Successful manual activation and check-in each call `RoomAutomationService.prepare` exactly once.
  - `alreadyActive` and rejected check-ins never call it.
  - A device failure does not change the resulting `ACTIVE` status.
  - `CheckInResultResponse.failedDevices` mirrors the automation result.
- [X] T034 [P] [US2] Failing tests in `T/service/RoomStatusServiceTest.java`, covering the rule table from data-model.md (`RoomStatus`):
  - ACTIVE covering now gives `OCCUPIED`.
  - Only RESERVED covering now gives `RESERVED`.
  - EXPIRED, CANCELLED, COMPLETED or nothing gives `AVAILABLE`.
  - Missing device rows give off/off/`LOCKED`, and the repository's `save` is **never** called.
  - `lastPresenceAt` is `null` for a non-admin `Actor`.
- [X] T035 [P] [US2] Failing MockMvc tests in `T/controller/RoomStatusControllerTest.java`:
  - `GET /api/rooms/{id}/status` returns `200` with the contract shape `RoomStatus`.
  - Anonymous requests return `401`, and an unknown room returns `404`.
  - The response JSON contains no `createdBy`, `reservedFor`, `note` or reservation id.
- [X] T036 [P] [US2] Failing frontend tests:
  - `frontend/src/API/roomStatus.test.ts`: GET path.
  - `frontend/src/components/RoomDeviceIcons/RoomDeviceIcons.test.tsx`: renders three labelled items, "Licht an/aus", "Lüftung an/aus" and "Tür entriegelt/verriegelt", with no buttons.
  - `frontend/src/pages/RoomDisplayPage.test.tsx`: status is fetched with the initial load and on each 30 s refresh (fake timers), and the icons update.
  - `frontend/src/components/RoomDeviceControls/RoomDeviceControls.test.tsx`: the DOOR row is labelled "Tür", with state text "Entriegelt"/"Verriegelt".
  - `frontend/src/utils/labels.test.ts` and `frontend/src/components/ReservationList/ReservationList.test.tsx`: an `ACTIVE` reservation shows the status badge "In Nutzung" (FR-007).
  - `frontend/src/pages/CheckInPage.test.tsx`: after confirming, device states are shown, and a non-empty `failedDevices` shows "Folgende Geräte konnten nicht geschaltet werden: Tür".

### Implementation for User Story 2

- [X] T037 [US2] Refactor `B/service/RoomDeviceService.java`:
  - Add a public `RoomDeviceState apply(UUID roomId, RoomDeviceKind kind, boolean state)` with no user authorization. It loads or creates the state row, returns early when it already equals `state`, otherwise calls `gateway.setState`, saves the state and returns it. On a gateway `RuntimeException` it throws `DeviceOperationException`.
  - Make `setState` authorize and then delegate to `apply`.
  - Add `DOOR` to the `getControls` device list (after VENTILATION).
- [X] T038 [US2] Create `B/service/RoomAutomationService.java` with `AutomationResult prepare(Reservation r)` and a record `AutomationResult(List<RoomDeviceKind> failedDevices)`. It applies LIGHTING=true, VENTILATION=true and DOOR=true via `RoomDeviceService.apply`, catching `DeviceOperationException` per device. Each failure is logged at WARN as `room_automation_device_failed roomId={} reservationId={} kind={} phase=prepare` and added to the result.
- [X] T039 [US2] Create `B/service/RoomStatusService.java` (read-only transaction) and DTOs `B/dto/DeviceStatesResponse.java` (`boolean lighting`, `boolean ventilation`, `String door` with `"LOCKED"|"UNLOCKED"`) and `B/dto/RoomStatusResponse.java` (`roomId`, `status`, `devices`, nullable `lastPresenceAt`).
  - Status follows the rule from data-model.md; device rows come from `RoomDeviceStateRepository.findByRoomIdAndKind`, with defaults when absent and **never** saved.
  - Add `DeviceStatesResponse devicesFor(roomId)`, which `CheckInService` reuses.
- [X] T040 [US2] Wire `prepare` into the activation paths:
  - In `B/service/CheckInService.java`, call `prepare` after a successful save. On `ALREADY_ACTIVE`, skip it and return the stored states.
  - Fill `devices` (read via `RoomStatusService`, T039) and `failedDevices` in `CheckInResultResponse`.
  - In `B/service/ReservationService.java#activateReservation`, call `prepare` after the save. The manual activation response stays unchanged.
- [X] T041 [US2] Create `B/controller/RoomStatusController.java` with `GET /api/rooms/{roomId}/status`, resolving `Actor` for the admin-only `lastPresenceAt`
- [X] T042 [P] [US2] Create `frontend/src/types/roomStatus.ts`, `frontend/src/API/roomStatus.ts` (`getRoomStatus(roomId)`) and `frontend/src/components/RoomDeviceIcons/RoomDeviceIcons.tsx` with `.css`. The component is read-only and uses lucide-react `Lightbulb`/`LightbulbOff`, `Fan` (with "aus" styling when off) and `LockOpen`/`Lock`. Each item has a visible text label for accessibility and e-ink friendliness (high contrast, no animation).
- [X] T043 [US2] Extend `frontend/src/pages/RoomDisplayPage.tsx` to fetch `getRoomStatus(roomId)` in the same `Promise.all` and 30 s refresh, and pass `devices` to `RoomDisplay`. Render `<RoomDeviceIcons>` in `frontend/src/components/RoomDisplay/RoomDisplay.tsx` below the status badge when `state !== 'unavailable'`. Keep the existing client-side badge derivation unchanged (research Decision 5).
- [X] T044 [US2] Add the `DOOR` label "Tür" in `frontend/src/components/RoomDeviceControls/RoomDeviceControls.tsx`, with state text "Entriegelt"/"Verriegelt" instead of "An"/"Aus" for DOOR only
- [X] T045 [P] [US2] Change the `ACTIVE` label in `frontend/src/utils/labels.ts` (`reservationStatusLabels`) from "Aktiv" to "In Nutzung", matching the original story wording (FR-007); update any existing tests that assert "Aktiv"
- [X] T046 [US2] Extend `frontend/src/pages/CheckInPage.tsx` to show the device states from `CheckInResult.devices` (reuse `RoomDeviceIcons`) and the failed-device notice

**Checkpoint**: US1 and US2 both work. Check-in visibly prepares the room (quickstart Scenarios 1, 6 and 7).

---

## Phase 5: User Story 3 – Room Is Released Automatically When the Booking Ends (Priority: P1)

**Goal**: Every `ACTIVE → COMPLETED|CANCELLED` transition (sweep, manual completion, cancellation) switches lighting and ventilation off, unless another reservation for the room is `ACTIVE`. The door stays as it is.

**Independent Test**: Let an `ACTIVE` booking pass its end time. Within 60 s the reservation is `COMPLETED`, `/status` shows `AVAILABLE` with lighting and ventilation off, and the door is unchanged (quickstart Scenario 4).

### Tests for User Story 3 ⚠️ write first, watch them fail

- [X] T047 [P] [US3] Failing tests in `T/service/RoomAutomationServiceTest.java`:
  - `release(reservation)` applies LIGHTING=false and VENTILATION=false and never touches DOOR or PROJECTOR.
  - When `existsByRoomIdAndStatusAndIdNot(roomId, ACTIVE, reservationId)` is true, `release` applies nothing.
  - A failing device is logged as `room_automation_device_failed roomId={} reservationId={} kind={} phase=release` and does not stop the other device.
- [X] T048 [P] [US3] Failing tests in `T/service/ReservationServiceTest.java`:
  - `completeOverdueActiveReservations`, `completeReservation` and `cancelReservation` (from `ACTIVE`) each call `release` once per transitioned reservation.
  - `cancelReservation` from `RESERVED` and `expireUnattendedReservations` never call it.
  - A `release` failure never prevents the status change or aborts the sweep loop.
- [X] T049 [P] [US3] Failing Testcontainers test `T/PresenceCheckInIntegrationTest.java` (fixed or mutable test `Clock` as in `ReservationExpirationIntegrationTest`), covering the full lifecycle:
  - Create, then check in via `POST /check-in`; status shows OCCUPIED with lighting, ventilation and door on/unlocked.
  - Advance past `endTime`, then call `sweepOverdueReservations()`; status shows AVAILABLE, lighting/ventilation off, door still UNLOCKED.
  - The back-to-back case keeps lighting on.

### Implementation for User Story 3

- [X] T050 [US3] Add `AutomationResult release(Reservation r)` to `B/service/RoomAutomationService.java`. It returns immediately when another reservation of the room is `ACTIVE`. Otherwise it applies LIGHTING=false and VENTILATION=false with the same per-device failure handling and logging (`room_automation_device_failed roomId={} reservationId={} kind={} phase=release`).
- [X] T051 [US3] Call `roomAutomationService.release(reservation)` in `B/service/ReservationService.java` after a successful save in:
  - `completeOverdueActiveReservations` (inside the loop, after `save`, wrapped so an exception is logged and the loop continues)
  - `completeReservation`
  - `cancelReservation`, only when the previous status was `ACTIVE`

**Checkpoint**: US1–US3 deliver the full acceptance criteria of the original story for QR check-in.

---

## Phase 6: User Story 4 – Confirm Presence by NFC Tap (Priority: P2)

**Goal**: The NFC link (`?method=nfc`) runs the same flow and records `NFC`. Admins can copy the NFC link to write onto a tag.

**Independent Test**: Open `/rooms/R/check-in?method=nfc` as the owner and confirm. The reservation has `checkInMethod = NFC`. Signed out, you return to the same URL, including the query, after login (quickstart Scenario 2).

### Tests for User Story 4 ⚠️ write first, watch them fail

- [X] T052 [P] [US4] Failing test in `T/service/CheckInServiceTest.java`: `checkIn(roomId, NFC, owner)` records `NFC`, and preview and outcome logic are identical to QR. Failing MockMvc case in `T/controller/CheckInControllerTest.java`: `{"method":"NFC"}` returns `200` with `checkInMethod: "NFC"`, and `{"method":"MANUAL"}` returns `400`.
- [X] T053 [P] [US4] Failing test in `frontend/src/pages/RoomDetailPage.test.tsx`: in admin mode, the "Check-in-Codes" section shows the NFC link text `…/rooms/{id}/check-in?method=nfc` and a "NFC-Link kopieren" button that calls `navigator.clipboard.writeText` with it

### Implementation for User Story 4

- [X] T054 [US4] Add the NFC link and the "NFC-Link kopieren" button to the admin "Check-in-Codes" section in `frontend/src/pages/RoomDetailPage.tsx`, using `checkInLink(id, 'nfc')`. Include a hint that the link can be written to any NFC tag as a URL record.

**Checkpoint**: Both on-site methods work. The method is recorded per reservation.

---

## Phase 7: User Story 5 – Motion Sensor Confirms Continued Presence (Priority: P3)

**Goal**: Administrators simulate motion events. During an `ACTIVE` booking these update `lastPresenceAt`; they never change a status or a device.

**Independent Test**: As an admin in administration mode, press "Bewegung simulieren" on the display during an `ACTIVE` booking, and "Zuletzt Bewegung erkannt: HH:MM:SS" appears. Do the same during a `RESERVED` booking: `recorded: false` and nothing changes. A non-admin gets `403` (quickstart Scenario 5).

### Tests for User Story 5 ⚠️ write first, watch them fail

- [X] T055 [P] [US5] Failing tests in `T/service/PresenceServiceTest.java`:
  - With an `ACTIVE` reservation covering now, `lastPresenceAt = now` is saved, the result is `recorded = true`, and `presence_event_recorded roomId={} reservationId={}` is logged.
  - With only `RESERVED`, or nothing, the result is `recorded = false` with no save, and status and devices are untouched (verify no `RoomDeviceService`/`RoomAutomationService` interaction).
  - An unknown room gives `NotFoundException`.
- [X] T056 [P] [US5] Failing MockMvc tests in `T/controller/PresenceEventControllerTest.java`:
  - Admin: `POST /api/admin/rooms/{id}/presence-events` returns `200 PresenceEventResult`.
  - A plain user returns `403` and anonymous returns `401`. An admin returns `200` regardless of administration mode; the server checks the role only, like all `/api/admin/**` routes.

  Extend `T/security/AuthorizationMatrixTest.java` if it lists admin routes.
- [X] T057 [P] [US5] Failing test in `T/service/RoomStatusServiceTest.java`: an admin `Actor` receives the `ACTIVE` reservation's `lastPresenceAt`, and a non-admin receives `null`
- [X] T058 [P] [US5] Failing frontend tests:
  - `frontend/src/components/PresenceSimulateButton/PresenceSimulateButton.test.tsx`: hidden when `adminMode` is false; when true, clicking POSTs, then shows "Zuletzt Bewegung erkannt: HH:MM:SS" or "Keine aktive Buchung – Bewegung nicht erfasst".
  - `frontend/src/pages/RoomDisplayPage.test.tsx`: the button renders only in admin mode.

### Implementation for User Story 5

- [X] T059 [US5] Create `B/service/PresenceService.java` (`PresenceEventResponse recordMotion(UUID roomId)`, using `findActiveCovering` and the injected `Clock`), `B/dto/PresenceEventResponse.java` (`boolean recorded`, nullable `Instant lastPresenceAt`) and `B/controller/PresenceEventController.java` (`POST /api/admin/rooms/{roomId}/presence-events`; protection comes from the existing admin-area handling in `RoleAccessFilter`)
- [X] T060 [US5] Fill `lastPresenceAt` in `B/service/RoomStatusService.java` from `findActiveCovering(roomId, now)` when `actor.admin()`, otherwise `null`
- [X] T061 [P] [US5] Create `frontend/src/API/presence.ts` (`simulatePresence(roomId)`) and `frontend/src/components/PresenceSimulateButton/PresenceSimulateButton.tsx`. It uses `useAdminMode()`, renders nothing when the mode is off, and shows the result text after a click. It also takes an optional initial `lastPresenceAt` from `/status`.
- [X] T062 [US5] Render `<PresenceSimulateButton roomId lastPresenceAt>` on `frontend/src/pages/RoomDisplayPage.tsx` (below the device icons) and in the admin section of `frontend/src/pages/RoomDetailPage.tsx`

**Checkpoint**: All five stories are independently functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T063 [P] Update `README.md`: add a short **On-site check-in** paragraph (QR/NFC links, automatic room preparation and release, simulated devices, the "Bewegung simulieren" admin action) and list the new endpoints in the endpoint block (`GET|POST /api/rooms/{roomId}/check-in`, `GET /api/rooms/{roomId}/status`, `POST /api/admin/rooms/{roomId}/presence-events`)
- [X] T064 [P] Update `docs/SYSTEMDOKUMENTATION.md`:
  - Feature table row 014.
  - §Gerätesteuerung: DOOR, `RoomAutomationService`, the status endpoint without lazy writes.
  - Mark finding F8 as partly addressed: the status endpoint no longer writes, while `getControls` still does.
- [X] T065 [P] Add a contract-conformance check in `T/controller/CheckInControllerTest.java` and `T/controller/RoomStatusControllerTest.java`: assert the JSON field sets exactly match `contracts/presence-checkin-api.yaml` (no extra personal fields)
- [X] T066 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`, and fix any failures
- [X] T067 Walk through `specs/014-presence-checkin/quickstart.md` Scenarios 1–8 against the running Compose stack, including a real phone scan of the QR code, and record deviations in the PR description

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: after Setup. **Blocks all stories.**
- **US1 (Phase 3)**: after Foundational. This is the MVP.
- **US2 (Phase 4)**: after Foundational. It touches `CheckInService` (T040), so do it after US1's T020. `ReservationService` and the display parts are independent.
- **US3 (Phase 5)**: after US2's T037/T038 (it needs `RoomDeviceService.apply` and `RoomAutomationService`).
- **US4 (Phase 6)**: after US1 (reuses the check-in endpoint, page and admin section).
- **US5 (Phase 7)**: after Foundational. T060 extends `RoomStatusService` from US2 (T039).
- **Polish (Phase 8)**: after all desired stories.

### Story completion order

```text
Setup → Foundational → US1 ─┬─> US2 ──> US3
                            └─> US4
              Foundational ─────> US5 (status part after US2/T039)
```

### Within each story

Tests (fail) → DTOs/types → services → controllers/filters → frontend API → components/pages.

## Parallel Examples

**Foundational**: T003, T004, T006, T007 and T008 in parallel; then T005 → T009 → T010.

**US1 tests**: T011–T018 are all [P] (separate files). Implementation: T019, T025 and T028 in parallel; then T020 → T021 → T022 → T023; T024 in parallel to those; frontend T026 → T027, then T029 and T030.

**US2**: tests T031–T036 in parallel. Then backend T037 → T038 → T039 → T040 → T041, alongside frontend T042 → T043 / T044 / T045 / T046.

**US3**: T047–T049 in parallel → T050 → T051.

**US5**: T055–T058 in parallel → T059 → T060, alongside T061 → T062.

## Implementation Strategy

### MVP first (US1 only)

1. Phases 1 and 2.
2. Phase 3 (US1): QR check-in with method recording, the QR code on the display and the admin printout.
3. **Stop and validate** with quickstart Scenarios 1–3 (ignore the device expectations).

### Incremental delivery

1. **+ US2**: room preparation, door, status endpoint, display icons. This is the visible "concept demo".
2. **+ US3**: release at the end. All acceptance criteria of the original story are now met for QR.
3. **+ US4**: the NFC variant.
4. **+ US5**: simulated motion sensor.
5. Polish, docs, quickstart walkthrough, PR (name the `qrcode` dependency in the description).

## Notes

- [P] means different files and no dependency on unfinished tasks.
- Each test task must be seen failing before its implementation task (constitution Principle I).
- Never log names or e-mail addresses; ids and reason codes only (Principle IV).
- Do not change the client-side display badge rule from feature 009; the backend status mirrors it (research Decision 5).

---

## Phase 9: Convergence

- [X] T068 Handle concurrent updates in `backend/src/main/java/at/mci/igp/raumlotse/service/CheckInService.java#checkIn`: on a version conflict (another check-in or the sweep's expiry won), re-read the room's candidates and reclassify, so a duplicate check-in answers `200` with `alreadyActive: true` and an expired booking answers `409 EXPIRED` instead of the generic "changed in the meantime" message. Add a failing test first (Testcontainers, two concurrent check-ins of the same booking) in `backend/src/test/java/at/mci/igp/raumlotse/PresenceCheckInIntegrationTest.java` per FR-011 / plan T020 (partial)
- [X] T069 Show the stored latest presence on the room page: load `getRoomStatus(room.id)` in `frontend/src/pages/RoomDetailPage.tsx` while administration mode is on and pass its `lastPresenceAt` to `<PresenceSimulateButton>` instead of `null`; extend `frontend/src/pages/RoomDetailPage.test.tsx` per FR-019 (partial)
- [X] T070 Record in `specs/014-presence-checkin/research.md` (Decision 10) that the room display treats a failed `/status` request as "no device icons" rather than "room unavailable", and why (device icons are optional; the existing display must keep working), matching `frontend/src/pages/RoomDisplayPage.tsx` per plan Decision 10 (unrequested)

---

## Phase 10: Early check-in and navigation (follow-up, 2026-10-10)

**Purpose**: Bug fix and change after hands-on testing: checking in hours early via "Einchecken" left the booking `ACTIVE` while display and device control still treated it as not started. Check-in becomes possible from 10 minutes before the start (if no other reservation of the room is ongoing) for every method, and a checked-in booking is in use from its check-in time. Plus navigation back from the display and device control, and device control as a popup.

- [X] T071 [P] Failing tests in `backend/src/test/java/at/mci/igp/raumlotse/service/CheckInServiceTest.java`: 10 minutes before start gives `READY`; 10 minutes + 1 ns before gives `TOO_EARLY` with `checkInOpensAt = startTime - 10 min`; another `RESERVED`/`ACTIVE` reservation covering now gives `TOO_EARLY` with `checkInOpensAt` = its end and a "Raum ist noch belegt" message; after the start time an ongoing check of other reservations is not needed per FR-004 / Clarification 2026-10-10
- [X] T072 [P] Failing tests in `backend/src/test/java/at/mci/igp/raumlotse/service/ReservationServiceTest.java`: manual `activateReservation` accepts 10 minutes early, rejects more than 10 minutes early and rejects early activation while the room is still occupied (`ConflictException` with explanation); give existing activation fixtures a `startTime` per FR-004
- [X] T073 [P] Failing tests in `backend/src/test/java/at/mci/igp/raumlotse/repository/ReservationRepositoryPresenceTest.java`: an `ACTIVE` reservation checked in 5 minutes before its start is returned by `findCovering`, `findActiveCovering` and `findEligibleDeviceReservations` from `checked_in_at` on; a `RESERVED` one only from its start per data-model "In use"
- [X] T074 Add `EARLY_CHECK_IN_PERIOD = Duration.ofMinutes(10)` to `backend/src/main/java/at/mci/igp/raumlotse/config/ReservationPolicyConstants.java` and a shared `backend/src/main/java/at/mci/igp/raumlotse/service/CheckInWindow.java`; use it in `CheckInService` and `ReservationService.activateReservation` per FR-004
- [X] T075 Change `findCovering`, `findActiveCovering` and `findEligibleDeviceReservations` in `backend/src/main/java/at/mci/igp/raumlotse/repository/ReservationRepository.java` so `ACTIVE` reservations are in use from `coalesce(checkedInAt, startTime)` per data-model "In use"
- [X] T076 Adjust tests that activated far-future bookings only to obtain an `ACTIVE` status (`backend/src/test/java/at/mci/igp/raumlotse/repository/ReservationOccupancyIntegrationTest.java`) to set the status directly per FR-004
- [X] T077 [P] Failing tests then fix in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts` (+ `.test.ts`): an `ACTIVE` reservation is current and makes the room `OCCUPIED` until its end, also before its start time per FR-004
- [X] T078 Failing tests then implementation: back link "Zurück zum Raum" on `frontend/src/pages/RoomDisplayPage.tsx` and `frontend/src/pages/RoomDeviceControlPage.tsx`; "Geräte steuern" on `frontend/src/pages/RoomDetailPage.tsx` opens device control as a closable popup (button, `role="dialog"`, closes with "Schließen" and Escape) per FR-021

---

## Phase 11: Convergence

- [X] T079 Update scenario 3 in `specs/014-presence-checkin/quickstart.md` to the revised window: a booking starting in 10 minutes can be checked in (room shows "Belegt" from then on), one starting in 15 minutes shows "Check-in ab HH:MM möglich", and an early check-in while another booking of the room is ongoing shows "Der Raum ist noch belegt …" per FR-004 (contradicts)

---

## Phase 12: Admin-editable check-in times (follow-up, 2026-10-10)

**Purpose**: FR-022 — the early check-in time and the grace period become admin settings instead of code constants.

- [X] T080 [P] Failing Testcontainers test `backend/src/test/java/at/mci/igp/raumlotse/CheckInSettingsMigrationIntegrationTest.java`: table `check_in_settings` exists with exactly one row (`id = 1`, `early_check_in_minutes = 10`, `grace_period_minutes = 5`), rejects a second row and values outside 0–60 / 1–30
- [X] T081 [P] Failing tests `backend/src/test/java/at/mci/igp/raumlotse/service/CheckInSettingsServiceTest.java`: `current()` returns the stored policy; `update` stores both values, `updated_at`, `updated_by_user_id` and logs `check_in_settings_changed earlyCheckInMinutes= gracePeriodMinutes= by=`
- [X] T082 [P] Failing tests `backend/src/test/java/at/mci/igp/raumlotse/controller/CheckInSettingsControllerTest.java`: `GET`/`PUT /api/admin/check-in-settings` return the contract shape; out-of-range or missing values give `400`; plain users `403`, anonymous `401`; add both routes as ADMIN in `AuthorizationMatrixTest`
- [X] T083 [P] Failing tests: `CheckInServiceTest` and `ReservationServiceTest` use a custom policy (e.g. 0 minutes early, 15 minutes grace) for check-in, manual activation and `expireUnattendedReservations`
- [X] T084 Migration `V15__check_in_settings.sql`, entity, repository, `CheckInPolicy` record, `CheckInSettingsService`, DTOs and `CheckInSettingsController`; `CheckInWindow`, `CheckInService` and `ReservationService` (check-in, manual activation, expiry sweep) take the current policy instead of the constants per FR-022
- [X] T085 [P] Failing frontend tests then implementation: `frontend/src/API/checkInSettings.ts`, `frontend/src/pages/AdminSettingsPage.tsx` (two number fields with ranges, zod validation, save, success and error messages), route `/admin/settings` and navigation entry "Einstellungen" in administration mode per FR-022
- [X] T086 Update `README.md` and `docs/SYSTEMDOKUMENTATION.md` (settings page, endpoint, 007's constant now an admin setting)

---

## Phase 13: Convergence

- [X] T087 Explain *why* check-in is not possible yet on the check-in page: add a nullable `detail` string to `CheckInPreviewResponse` (`backend/src/main/java/at/mci/igp/raumlotse/dto/CheckInPreviewResponse.java`, filled from the classification for `TOO_EARLY`/`EXPIRED`/`NO_MATCH`) and to `CheckInPreview` in `specs/014-presence-checkin/contracts/presence-checkin-api.yaml` and `frontend/src/types/checkIn.ts`; show it in `frontend/src/pages/CheckInPage.tsx` so an occupied room reads "Der Raum ist noch belegt. Der Check-in ist ab HH:MM Uhr möglich."; failing tests first in `CheckInServiceTest`, `CheckInControllerTest` (field set) and `CheckInPage.test.tsx` per FR-004 (partial)

---

## Phase 14: Follow-up after phone testing (2026-10-10)

- [X] T088 Regression test: the booking user can control the devices right after an early check-in, in `backend/src/test/java/at/mci/igp/raumlotse/PresenceCheckInIntegrationTest.java` (the reported failure came from a backend container built before Phase 10, not from the code) per FR-004
- [X] T089 Place "Zurück zum Raum" inside the page content, centred below it and drawn like the design's buttons (`.page-back` in `frontend/src/index.css`), on `RoomDisplayPage` (via the new `footer` slot of `RoomDisplay`) and `RoomDeviceControlPage` per FR-021 / design system 002
