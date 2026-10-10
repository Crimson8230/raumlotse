# Implementation Plan: On-Site Presence Check-In

**Branch**: `014-presence-checkin` | **Date**: 2026-10-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-presence-checkin/spec.md`

## Summary

Users confirm their presence on site by scanning a room's QR code or opening its NFC link. A new room-scoped check-in endpoint selects the booking whose check-in window is open, verifies the user is its owner or an administrator, and sets it from `RESERVED` to `ACTIVE`.

Every `RESERVED → ACTIVE` transition (QR, NFC or the existing manual activation) triggers a new `RoomAutomationService`. It switches lighting and ventilation on and unlocks the door. Every `ACTIVE → COMPLETED|CANCELLED` transition switches lighting and ventilation off. Devices remain simulated through the existing feature 006 stub (`RoomDeviceGateway` → `PersistedRoomDeviceGateway`). The door is added as a fourth device kind.

A read-only `GET /api/rooms/{roomId}/status` feeds the room display's new device icons, and later any e-ink or LED controller. Administrators can simulate motion-sensor events, which only record the latest presence time of an `ACTIVE` booking. QR codes are generated in the browser.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript strict / React 19 (frontend)

**Primary Dependencies**: Spring Boot (Web MVC, Data JPA, Security, Validation), Flyway; React Router, lucide-react (icons). **New**: npm `qrcode` and dev dependency `@types/qrcode` (research Decision 8)

**Storage**: PostgreSQL 17. Migration `V14__presence_checkin.sql` adds four nullable `reservation` columns and extends the `room_device_state.kind` check with `DOOR`

**Testing**: JUnit 5 + MockMvc + Testcontainers (backend); Vitest + Testing Library (frontend)

**Target Platform**: Docker Compose (Linux) backend; modern browsers, including phone browsers for check-in

**Project Type**: Web application (Spring Boot backend + React SPA)

**Performance Goals**: Check-in round-trip under 1 s server side (SC-001: under 15 s end to end); display reflects changes within one 30 s refresh (SC-002); release within 60 s of end time through the existing sweep (SC-003)

**Constraints**: No physical device protocol (FR-018); the status endpoint never writes and carries no personal data (FR-020); device failures never roll back a status change (FR-010); all routes require sign-in

**Scale/Scope**: One room per request; four device kinds; one check-in and one presence column set per reservation. Hardware integration, device sign-in, automatic release on absence and a check-in attempt history are out of scope

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan complies |
|---|---|---|
| I. Test-First (non-negotiable) | ✅ | Tasks will start with failing tests: check-in window and ownership rules, automation on every transition, idempotency, device failure, status rule table, presence endpoint guard, migration, and frontend check-in, display icons and login return |
| II. Modern, typed, consistent | ✅ | Constructor injection, Bean Validation on `CheckInRequest`, controller → service → repository layering; strict TS, functional components, no `any` |
| III. Contract-first API | ✅ | [contracts/presence-checkin-api.yaml](./contracts/presence-checkin-api.yaml) is defined before implementation. Changes to existing contracts are additive (`DOOR` kind, side effects of activate), so no version bump is needed |
| IV. Secure by default | ✅ | All routes are authenticated. The presence simulation lives in `/api/admin/**` (ADMIN role; administration mode only controls visibility in the frontend). The check-in enforces owner-or-admin like `ReservationAccessPolicy`. The status endpoint has no personal data and shows `lastPresenceAt` to admins only. Login return is limited to same-app paths (no open redirect). JPA parameter binding only; logs carry ids and reasons, no names |
| V. Simplicity & observability | ✅ | The direct service call is preferred over events (Decision 3). No attempts table (Decision 6). Structured logs: `check_in_accepted`, `check_in_rejected reason=…`, `room_automation_device_failed`, `presence_event_recorded`. The new dependency `qrcode` is justified in research Decision 8 and must be named in the PR |

**Post-design re-check (after Phase 1)**: still passes. The only stack addition is `qrcode` (plus its type definitions `@types/qrcode`), a compatible frontend-layer addition that needs a PR note and no amendment. Complexity Tracking stays empty.

## Project Structure

### Documentation (this feature)

```text
specs/014-presence-checkin/
├── plan.md              # This file
├── research.md          # Phase 0: decisions 1–10
├── data-model.md        # Phase 1: reservation columns, DOOR, automation table, RoomStatus
├── quickstart.md        # Phase 1: validation scenarios
├── contracts/
│   └── presence-checkin-api.yaml
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/at/mci/igp/raumlotse/
├── domain/
│   ├── CheckInMethod.java                 # NEW enum QR, NFC, MANUAL
│   ├── RoomDeviceKind.java                # + DOOR
│   └── Reservation.java                   # + checkInMethod, checkedInAt, checkedInByUserId, lastPresenceAt
├── dto/
│   ├── CheckInRequest.java                # NEW
│   ├── CheckInPreviewResponse.java        # NEW
│   ├── CheckInResultResponse.java         # NEW
│   ├── DeviceStatesResponse.java          # NEW
│   ├── RoomStatusResponse.java            # NEW
│   └── PresenceEventResponse.java         # NEW
├── controller/
│   ├── CheckInController.java             # NEW  GET/POST /api/rooms/{id}/check-in
│   ├── RoomStatusController.java          # NEW  GET /api/rooms/{id}/status
│   └── PresenceEventController.java       # NEW  POST /api/admin/rooms/{id}/presence-events
├── service/
│   ├── CheckInService.java                # NEW  window, owner-or-admin selection, method recording
│   ├── RoomAutomationService.java         # NEW  prepare/release, AutomationResult
│   ├── RoomStatusService.java             # NEW  read-only status, defaults without writes
│   ├── PresenceService.java               # NEW  lastPresenceAt for ACTIVE booking
│   ├── RoomDeviceService.java             # + apply(roomId, kind, state) used by automation; setState delegates
│   ├── ReservationService.java            # activate/complete/cancel/sweep call automation; activate records MANUAL
│   └── RoleAccessFilter.java              # + USER_ACTION POST /api/rooms/[^/]+/check-in
└── repository/ReservationRepository.java  # + queries: check-in candidate, active-in-room-now

backend/src/main/resources/db/migration/V14__presence_checkin.sql   # NEW

backend/src/test/java/at/mci/igp/raumlotse/
├── service/   CheckInServiceTest, RoomAutomationServiceTest, RoomStatusServiceTest, PresenceServiceTest,
│              ReservationServiceAutomationTest
├── controller/ CheckInControllerTest, RoomStatusControllerTest, PresenceEventControllerTest (MockMvc)
├── PresenceCheckInIntegrationTest.java   # Testcontainers: migration + end-to-end lifecycle (root package, like the others)
└── MigrationVersionTest.java             # updated for V14

frontend/src/
├── API/checkIn.ts, API/roomStatus.ts, API/presence.ts        # NEW
├── types/checkIn.ts, types/roomStatus.ts                     # NEW; types/roomDevice.ts + 'DOOR'
├── utils/checkInLink.ts                                      # NEW  builds …/rooms/:id/check-in?method=qr|nfc
├── components/
│   ├── CheckInQrCode/CheckInQrCode.tsx                       # NEW  SVG via qrcode
│   ├── RoomDeviceIcons/RoomDeviceIcons.tsx                   # NEW  read-only light/vent/door icons
│   ├── PresenceSimulateButton/PresenceSimulateButton.tsx     # NEW  admin-mode only
│   ├── RoomDisplay/RoomDisplay.tsx                           # + icons, QR, simulate button slots
│   └── RoomDeviceControls/RoomDeviceControls.tsx             # + DOOR label (Verriegelt/Entriegelt)
├── pages/
│   ├── CheckInPage.tsx                                       # NEW  /rooms/:roomId/check-in
│   ├── RoomDisplayPage.tsx                                   # + fetch /status in the 30 s refresh
│   ├── RoomDetailPage.tsx                                    # + admin "Check-in-Codes" print section
│   └── LoginPage.tsx                                         # navigate to safe state.from
├── auth/RequireAuth.tsx                                      # from = pathname + search
└── App.tsx                                                   # + route /rooms/:roomId/check-in
```

**Structure Decision**: The existing web application layout (`backend/` Spring Boot, `frontend/` React SPA) is extended in place, following feature 006's controller → service → gateway seam. Tests sit next to the existing ones (backend `src/test/java/...`, frontend colocated `*.test.tsx`).

## Complexity Tracking

No constitution violations; nothing to justify.
