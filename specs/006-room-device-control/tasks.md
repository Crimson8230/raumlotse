# Tasks: Room Device Control

**Input**: Design documents from `/specs/006-room-device-control/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [API contract](./contracts/room-device-control-api.yaml), [quickstart.md](./quickstart.md)

**Tests**: Required by the project constitution. Every test task must be written first, observed failing, and then implemented through the Red-Green-Refactor cycle.

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 [P] Verify the existing backend test profile, authenticated-user fixtures, and PostgreSQL/Flyway test setup in `backend/src/test/` and document fixture extensions in `specs/006-room-device-control/quickstart.md`
- [ ] T002 [P] Verify the existing frontend API-client, auth-provider, and route-test helpers in `frontend/src/API/client.ts`, `frontend/src/auth/`, and `frontend/src/test/` for authenticated device-control tests
- [ ] T003 [P] Validate the OpenAPI contract structure and status-code examples in `specs/006-room-device-control/contracts/room-device-control-api.yaml` before implementing REST endpoints

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish stable ownership, capability identity, persistence, and server-side authorization required by every user story.

**⚠️ CRITICAL**: No user story implementation can begin until this phase is complete.

- [ ] T004 [P] Add failing repository/service tests for owner matching, `ACTIVE` status, room association, and the exact predicate `startTime <= now < endTime` in `backend/src/test/java/at/mci/igp/raumlotse/service/RoomDeviceAuthorizationTest.java`
- [ ] T005 [P] Add failing migration/entity mapping tests for reservation owner IDs, canonical equipment code `PROJECTOR`, and one unique device state per room/kind in `backend/src/test/java/at/mci/igp/raumlotse/repository/RoomDeviceStateRepositoryTest.java`
- [ ] T006 [P] Add failing frontend type/API contract tests for device capability and command shapes in `frontend/src/API/roomDevices.test.ts` and `frontend/src/types/roomDevice.test.ts`
- [ ] T007 Add Flyway migration `backend/src/main/resources/db/migration/V9__create_room_device_control.sql` with nullable historical `reservation.created_by_user_id`, unique `equipment_type.code`, and `room_device_state` keyed uniquely by `(room_id, kind)`; preserve existing reservation status constraints
- [ ] T008 Add `RoomDeviceKind` and `RoomDeviceState` JPA types in `backend/src/main/java/at/mci/igp/raumlotse/domain/RoomDeviceKind.java` and `backend/src/main/java/at/mci/igp/raumlotse/domain/RoomDeviceState.java`, including UUID room relation, `enabled`, boolean `state`, and `updatedAt`
- [ ] T009 Extend `backend/src/main/java/at/mci/igp/raumlotse/domain/Reservation.java` with immutable `createdByUserId` ownership and extend `backend/src/main/java/at/mci/igp/raumlotse/domain/EquipmentType.java` with unique canonical `code`; keep `createdBy` as the display/audit snapshot
- [ ] T010 Extend reservation creation in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` to derive `createdByUserId` and `createdBy` from the authenticated `AuthenticatedUser`, reject missing ownership for new reservations, and never accept a client-supplied owner identity
- [ ] T011 Add `RoomDeviceStateRepository` in `backend/src/main/java/at/mci/igp/raumlotse/repository/RoomDeviceStateRepository.java` with room/kind lookup and unique-state query; add an eligible-reservation query to `backend/src/main/java/at/mci/igp/raumlotse/repository/ReservationRepository.java` filtering by owner, room, `ACTIVE`, and `startTime <= now < endTime`
- [ ] T012 Add typed device DTOs `RoomDeviceResponse` and `RoomDeviceCommandRequest` in `backend/src/main/java/at/mci/igp/raumlotse/dto/`, enforcing a required boolean command state and enum values `LIGHTING`, `VENTILATION`, and `PROJECTOR`
- [ ] T013 Implement shared authorization and capability resolution in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomDeviceService.java`: authenticate the current account, require matching owner and room, require `ACTIVE`, enforce `[startTime, endTime)`, expose default lighting/ventilation, and derive projector presence from active equipment code `PROJECTOR`
- [ ] T045 [P] Add failing gateway tests for acknowledged commands, simulated device failure, and unchanged persisted state after failure in `backend/src/test/java/at/mci/igp/raumlotse/service/PersistedRoomDeviceGatewayTest.java`
- [ ] T046 [P] Add failing reservation-owner integration tests proving the authenticated account supplies immutable `createdByUserId`/`createdBy` and client input cannot select another owner in `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationOwnershipTest.java`
- [ ] T047 Extend `backend/src/main/java/at/mci/igp/raumlotse/controller/ReservationController.java`, `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java`, and `backend/src/main/java/at/mci/igp/raumlotse/dto/ReservationCreateRequest.java` so the authenticated `AuthenticatedUser` supplies immutable `createdByUserId`/`createdBy` and client input cannot select another owner
- [ ] T048 Update `frontend/src/components/ReservationForm/ReservationForm.tsx`, `frontend/src/components/ReservationForm/ReservationForm.test.tsx`, and `frontend/src/auth/useAuth.ts` so reservation creation no longer submits arbitrary owner identity and relies on the authenticated account context
- [ ] T049 Add the `RoomDeviceGateway` interface and deterministic persisted-state implementation in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomDeviceGateway.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/PersistedRoomDeviceGateway.java`; define acknowledgement and failure behavior without a vendor dependency

**Checkpoint**: Database model, authenticated ownership, capability identity, and reusable authorization predicate are available.

---

## Phase 3: User Story 1 - Control Default Room Devices (Priority: P1) 🎯 MVP

**Goal**: Let the booking user control lighting and ventilation during an in-window `ACTIVE` reservation.

**Independent Test**: As the reservation owner, open a room with an `ACTIVE` reservation whose current time is within `[startTime, endTime)`, verify lighting and ventilation are listed, toggle each successfully, reload, and verify confirmed states. A different authenticated user must receive `403` and no state change.

### Tests for User Story 1

- [ ] T014 [P] [US1] Add failing backend service tests for default lighting/ventilation availability, successful state persistence, owner-only access, and no mutation after rejected commands in `backend/src/test/java/at/mci/igp/raumlotse/service/RoomDeviceServiceTest.java`
- [ ] T015 [P] [US1] Add failing backend controller contract tests for `GET /api/rooms/{roomId}/device-controls` and `POST /api/rooms/{roomId}/device-controls/{kind}` in `backend/src/test/java/at/mci/igp/raumlotse/controller/RoomDeviceControllerTest.java`
- [ ] T016 [P] [US1] Add failing frontend API tests for loading capabilities and posting boolean device state commands in `frontend/src/API/roomDevices.test.ts`
- [ ] T017 [P] [US1] Add failing component tests for default device rendering, current state display, successful toggle feedback, forbidden state, and command failure in `frontend/src/components/RoomDeviceControls/RoomDeviceControls.test.tsx`

### Implementation for User Story 1

- [ ] T018 [US1] Implement `getControls` and `setState` in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomDeviceService.java`, initializing missing lighting/ventilation state to off and persisting only acknowledged state changes
- [ ] T019 [US1] Implement `RoomDeviceController` in `backend/src/main/java/at/mci/igp/raumlotse/controller/RoomDeviceController.java` with the two paths from `specs/006-room-device-control/contracts/room-device-control-api.yaml`
- [ ] T020 [US1] Add structured authorization, unavailable-device, and operation-failure mappings in `backend/src/main/java/at/mci/igp/raumlotse/exception/GlobalExceptionHandler.java` without logging credentials, owner IDs, or command payloads
- [ ] T021 [US1] Implement typed client functions and response types in `frontend/src/API/roomDevices.ts` and `frontend/src/types/roomDevice.ts`, preserving shared `apiRequest` error behavior
- [ ] T022 [US1] Implement `RoomDeviceControls` in `frontend/src/components/RoomDeviceControls/RoomDeviceControls.tsx` and `frontend/src/components/RoomDeviceControls/RoomDeviceControls.css` with loading, unavailable, forbidden, command-failure, and current-state views
- [ ] T023 [US1] Add `RoomDeviceControlPage` in `frontend/src/pages/RoomDeviceControlPage.tsx`, register `/rooms/:roomId/control` in `frontend/src/App.tsx`, and add an entry link from `frontend/src/pages/RoomDetailPage.tsx`
- [ ] T024 [US1] Run US1 backend tests, frontend tests, TypeScript build, and ESLint; refactor only after new tests pass in `backend/` and `frontend/`

**Checkpoint**: User Story 1 is independently usable as the MVP for lighting and ventilation.

---

## Phase 4: User Story 3 - Enforce Reservation and Time Boundaries (Priority: P1)

**Goal**: Guarantee that all reads and commands fail before start, at/after end, for non-`ACTIVE` reservations, or for users other than the booking user.

**Independent Test**: Execute identical commands as the owner before, at, and after the reservation interval, with `RESERVED`/terminal statuses, and as another authenticated user. Only the owner command within `[startTime, endTime)` succeeds.

### Tests for User Story 3

- [ ] T025 [P] [US3] Add failing boundary tests for exact start success, exact end rejection, before-start rejection, midnight-spanning reservations, and injected `Instant` in `backend/src/test/java/at/mci/igp/raumlotse/service/RoomDeviceAuthorizationTest.java`
- [ ] T026 [P] [US3] Add failing lifecycle tests proving `RESERVED`, `COMPLETED`, `EXPIRED`, and `CANCELLED` reservations cannot read or mutate device state in `backend/src/test/java/at/mci/igp/raumlotse/service/RoomDeviceAuthorizationTest.java`
- [ ] T027 [P] [US3] Add failing security tests proving a different authenticated user cannot use room, reservation, or device identifiers to control the room in `backend/src/test/java/at/mci/igp/raumlotse/controller/RoomDeviceSecurityTest.java`
- [ ] T028 [P] [US3] Add failing page tests for time-expired/forbidden responses and disabling/reloading controls after a reservation becomes invalid in `frontend/src/pages/RoomDeviceControlPage.test.tsx`

### Implementation for User Story 3

- [ ] T029 [US3] Inject a testable clock into `backend/src/main/java/at/mci/igp/raumlotse/service/RoomDeviceService.java` and ensure every GET/POST authorization check uses one captured `Instant now` with `startTime <= now < endTime`
- [ ] T030 [US3] Ensure reservation cancellation, completion, expiry, and owner changes cannot leave an authorization cache in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomDeviceService.java`; re-query eligibility on every command
- [ ] T031 [US3] Map `401`/`403`/`409` responses to explicit safe UI states in `frontend/src/components/RoomDeviceControls/RoomDeviceControls.tsx` and prevent retries from mutating state after authorization failure
- [ ] T032 [US3] Run the complete US3 boundary/security suite and verify no rejected request changes `room_device_state` in `backend/src/test/java/at/mci/igp/raumlotse/`

**Checkpoint**: All authorization and temporal restrictions are enforced server-side and independently verified.

---

## Phase 5: User Story 2 - Control an Available Projector (Priority: P2)

**Goal**: Add projector control only when Room Management assigns an active equipment type with canonical code `PROJECTOR`.

**Independent Test**: Use one room with active `PROJECTOR` equipment and one without; an eligible booking user sees and controls projector only in the first room, while direct projector commands for the second room are rejected.

### Tests for User Story 2

- [ ] T033 [P] [US2] Add failing backend tests for active projector discovery, absent/deactivated projector omission, manually submitted projector-kind rejection, and configuration removal in `backend/src/test/java/at/mci/igp/raumlotse/service/RoomDeviceProjectorTest.java`
- [ ] T034 [P] [US2] Add failing frontend tests for conditional projector rendering and the unavailable-projector explanation in `frontend/src/components/RoomDeviceControls/RoomDeviceControls.test.tsx`

### Implementation for User Story 2

- [ ] T035 [US2] Extend `RoomDeviceService` and `RoomDeviceStateRepository` in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomDeviceService.java` and `backend/src/main/java/at/mci/igp/raumlotse/repository/RoomDeviceStateRepository.java` to expose projector only for active room equipment code `PROJECTOR` and reject other projector commands with the documented problem response
- [ ] T036 [US2] Update Room Management equipment DTOs, validation, and catalog handling in `backend/src/main/java/at/mci/igp/raumlotse/dto/EquipmentTypeRequest.java`, `backend/src/main/java/at/mci/igp/raumlotse/dto/EquipmentTypeResponse.java`, `backend/src/main/java/at/mci/igp/raumlotse/service/EquipmentTypeService.java`, and `backend/src/main/java/at/mci/igp/raumlotse/controller/EquipmentTypeController.java` so `code` is persisted and displayed safely
- [ ] T037 [US2] Update frontend room/equipment types and forms in `frontend/src/types/room.ts`, `frontend/src/components/EquipmentCatalog/EquipmentCatalog.tsx`, and `frontend/src/components/EquipmentCatalog/EquipmentCatalog.test.tsx` to preserve and submit the canonical equipment code
- [ ] T038 [US2] Update `RoomDeviceControls` tests and UI copy in `frontend/src/components/RoomDeviceControls/RoomDeviceControls.tsx` and `frontend/src/components/RoomDeviceControls/RoomDeviceControls.test.tsx` so projector controls appear only in the API response and absent projector state is clearly explained
- [ ] T039 [US2] Run US2 backend and frontend tests plus the manual projector add/remove scenario in `specs/006-room-device-control/quickstart.md`

**Checkpoint**: User Stories 1 and 2 work independently; projector presence is controlled solely by Room Management.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T040 [P] Add API error/status examples and ownership migration notes to `specs/006-room-device-control/contracts/room-device-control-api.yaml` and `specs/006-room-device-control/data-model.md`
- [ ] T041 [P] Add accessibility labels, keyboard operation, and non-color-only state indicators in `frontend/src/components/RoomDeviceControls/RoomDeviceControls.tsx` and `frontend/src/components/RoomDeviceControls/RoomDeviceControls.css`
- [ ] T042 [P] Add structured device-command failure logging and verify logs contain no credentials, user identifiers, or command payloads in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomDeviceService.java`
- [ ] T043 Run the complete validation sequence from `specs/006-room-device-control/quickstart.md` and record environment-specific prerequisites
- [ ] T044 [P] Add explicit response-time assertions for SC-005/SC-006 and a 30-second usability checklist for SC-003 in `frontend/src/pages/RoomDeviceControlPage.test.tsx` and `specs/006-room-device-control/quickstart.md`
- [ ] T050 [P] Add backend response-time assertions for SC-005/SC-006 in `backend/src/test/java/at/mci/igp/raumlotse/controller/RoomDevicePerformanceTest.java`, retaining the 30-second usability checklist in `frontend/src/pages/RoomDeviceControlPage.test.tsx` and `specs/006-room-device-control/quickstart.md`
- [ ] T051 Review changed files for Constitution compliance and update `specs/006-room-device-control/plan.md` only if implementation decisions materially diverge from the design

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: No dependencies; T001-T003 can run in parallel.
- **Phase 2 Foundational**: Depends on setup; tests T004-T006, T045, and T046 must be written before implementation T007-T013, T047-T049. These foundational tasks are executed before Phase 3 even though their IDs are reserved after the original generated story range. This phase blocks all user stories.
- **Phase 3 US1**: Depends on Phase 2 and delivers the MVP.
- **Phase 4 US3**: Depends on Phase 2 and shares the US1 service/controller; execute after US1 implementation or coordinate file ownership.
- **Phase 5 US2**: Depends on Phase 2 and the shared device API from US1 (T018-T023).
- **Phase 6 Polish**: Depends on all selected user stories.

### User Story Dependencies

- **US1 (P1)**: Phase 2 → US1; independently delivers lighting and ventilation.
- **US3 (P1)**: Phase 2 → US1 authorization/service → US3; boundary tests harden the same server-side predicate before projector work.
- **US2 (P2)**: Phase 2 → US1 device API/service → US2; projector behavior is independently testable once the shared contract exists.

### Parallel Opportunities

- T001-T003 can run in parallel.
- T004-T006 can run in parallel before foundational implementation.
- T045-T046 can run in parallel and must complete before T047-T049.
- Within US1, T014-T017 can run in parallel; after tests are written, frontend work T021-T023 can proceed in parallel with backend work T018-T020.
- Within US3, T025-T028 can run in parallel before T029-T031.
- Within US2, T033-T034 can run in parallel; backend catalog work T036 and frontend catalog work T037 can proceed in parallel after the migration contract is agreed.
- T040-T042 and T050 can run in parallel during polish.

## Parallel Example: User Story 1

```text
Developer A: T014, T015 — backend service and controller tests
Developer B: T016, T017 — frontend API and component tests
After the tests fail:
Developer A: T018, T019, T020 — backend implementation and error mapping
Developer B: T021, T022, T023 — frontend API, controls, page, and route
Together: T024 — complete MVP validation
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Setup and Foundational phases.
2. Complete US1 for lighting and ventilation.
3. Stop and validate owner, `ACTIVE`, time-window, state-persistence, and failure scenarios.
4. Demo the MVP before adding projector-specific work.

### Incremental Delivery

1. Add US1 and validate independently.
2. Add US2 and validate projector discovery against Room Management.
3. Add US3 boundary/security hardening and validate all negative paths.
4. Run the full quickstart and polish checks.

## Notes

- Every task uses the required checklist format: `- [ ] [TaskID] [P?] [Story?] description with file path`.
- `[P]` appears only where tasks touch different files and have no unfinished dependency on one another.
- User story labels map to the three stories in `spec.md`; setup/foundational/polish tasks intentionally have no story label.
