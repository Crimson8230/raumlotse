# Tasks: Expire Unclaimed Room Reservations

**Branch**: `007-expire-unclaimed-reservations` | **Date**: 2026-09-23
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contract**: [expiration-api.yaml](contracts/expiration-api.yaml)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Clock abstraction, time injection, and policy constants required across all expiration workflows.

- [X] T001 Register `java.time.Clock` singleton bean (`Clock.systemUTC()`) in `backend/src/main/java/at/mci/igp/raumlotse/config/ClockConfig.java`
- [X] T002 Inject `Clock` dependency into `ReservationService` via constructor injection in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java`
- [X] T003 [P] Define domain policy constants (`CHECK_IN_GRACE_PERIOD = Duration.ofMinutes(5)`, `EXPIRATION_POLL_INTERVAL_MS = 30000L`) in `backend/src/main/java/at/mci/igp/raumlotse/config/ReservationPolicyConstants.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core repository queries and response models required by user stories.

**⚠️ CRITICAL**: Must complete before user story execution begins.

- [X] T004 [P] Add repository query `findUnattendedReservationsForExpiration` selecting `status = 'RESERVED'` and `(startTime < :graceCutoff OR endTime <= :now)` in `backend/src/main/java/at/mci/igp/raumlotse/repository/ReservationRepository.java`
- [X] T005 [P] Add repository query `findByStatusAndEndTimeLessThanEqual` selecting `status = 'ACTIVE'` and `endTime <= :now` in `backend/src/main/java/at/mci/igp/raumlotse/repository/ReservationRepository.java`
- [X] T006 [P] Create DTO record `ReservationSweepResponse(int expiredCount, int completedCount)` in `backend/src/main/java/at/mci/igp/raumlotse/dto/ReservationSweepResponse.java` matching `contracts/expiration-api.yaml`

**Checkpoint**: Core queries and models ready — user story implementation can proceed.

---

## Phase 3: User Story 1 - Automatic Expiration of Unattended Reservations (Priority: P1) 🎯 MVP

**Goal**: Automatically transition unattended reservations in `RESERVED` status to `EXPIRED` once 5 minutes strictly elapse or scheduled end time is reached, releasing the room immediately and rejecting subsequent check-in attempts.

**Independent Test**: Create a reservation in `RESERVED` status. Advance clock past 5 minutes (or scheduled end time). Execute sweep. Verify status transitions to `EXPIRED`, room is immediately bookable for the remaining duration, and check-in returns `409 Conflict`.

### Tests for User Story 1 (TDD - Mandatory Test-First) ⚠️

- [X] T007 [P] [US1] Unit test unattended reservation expiration logic, timestamp updating, and zero-buffer room availability in `backend/src/test/java/at/mci/igp/raumlotse/service/ReservationServiceTest.java`
- [X] T008 [P] [US1] Unit test activation rejection on expired reservations and concluded bookings (`now >= endTime`) in `backend/src/test/java/at/mci/igp/raumlotse/service/ReservationServiceTest.java`
- [X] T009 [P] [US1] Integration test full auto-expiration sweep lifecycle and conflict resolution with database in `backend/src/test/java/at/mci/igp/raumlotse/ReservationExpirationIntegrationTest.java`

### Implementation for User Story 1

- [X] T010 [US1] Implement `expireUnattendedReservations()` in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` transitioning candidates to `EXPIRED`, setting `updatedAt`, and emitting SLF4J audit logs without PII
- [X] T011 [US1] Update `activateReservation(UUID reservationId)` in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` to strictly reject activation if `now >= reservation.getEndTime()`, throwing `ConflictException`
- [X] T012 [US1] Implement `@Scheduled(initialDelay = 30000, fixedDelay = 30000)` background runner in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationExpirationScheduler.java` delegating to `ReservationService`
- [X] T013 [US1] Expose operational endpoint `POST /api/reservations/expire-unattended` returning `ReservationSweepResponse` in `backend/src/main/java/at/mci/igp/raumlotse/controller/ReservationController.java` per `contracts/expiration-api.yaml`

**Checkpoint**: User Story 1 fully functional and testable independently (MVP Complete).

---

## Phase 4: User Story 2 - Successful Check-In Prevents Expiration & Concludes on End Time (Priority: P1)

**Goal**: Attended reservations activated within the grace period transition to `ACTIVE`, exempting them from `EXPIRED`, and automatically transition to `COMPLETED` once scheduled `endTime` arrives.

**Independent Test**: Check in at minute 2 after start time. Advance clock past minute 5; verify status remains `ACTIVE`. Advance clock past scheduled `endTime`; run sweep; verify status transitions to `COMPLETED`.

### Tests for User Story 2 (TDD - Mandatory Test-First) ⚠️

- [X] T014 [P] [US2] Unit test verifying `ACTIVE` reservations are exempted from expiration and auto-transition to `COMPLETED` upon `now >= endTime` in `backend/src/test/java/at/mci/igp/raumlotse/service/ReservationServiceTest.java`
- [X] T015 [P] [US2] Integration test verifying check-in exemption and automatic completion past `endTime` in `backend/src/test/java/at/mci/igp/raumlotse/ReservationExpirationIntegrationTest.java`

### Implementation for User Story 2

- [X] T016 [US2] Implement `completeOverdueActiveReservations()` in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` transitioning overdue active bookings to `COMPLETED` and logging transitions
- [X] T017 [US2] Compose unified `sweepOverdueReservations()` in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` executing unattended expiration and active completion in sequence, returning `ReservationSweepResponse`

**Checkpoint**: User Stories 1 and 2 work independently and harmoniously.

---

## Phase 5: User Story 3 - Standardized 5-Minute Grace Period Boundary Enforcement (Priority: P2)

**Goal**: The 5-minute check-in grace period is enforced with exact mathematical precision: at `startTime + 5m` check-in remains valid; at strictly `> startTime + 5m` the reservation is eligible for expiration.

**Independent Test**: Create a reservation starting at 10:00. Verify check-in is accepted at 10:05:00.000. In a separate test at 10:05:00.001, verify the reservation is expired by the sweep.

### Tests for User Story 3 (TDD - Mandatory Test-First) ⚠️

- [X] T018 [P] [US3] Unit test boundary precision at exact 5-minute mark (`now == startTime + 5m` vs. `now > startTime + 5m`) in `backend/src/test/java/at/mci/igp/raumlotse/service/ReservationServiceTest.java`
- [X] T019 [P] [US3] Concurrency unit test simulating simultaneous activation and sweep under optimistic locking (`@Version`) in `backend/src/test/java/at/mci/igp/raumlotse/service/ReservationServiceTest.java`

### Implementation for User Story 3

- [X] T020 [US3] Enforce strict temporal comparison logic using `clock.instant()` against `startTime.plus(CHECK_IN_GRACE_PERIOD)` and catch `OptimisticLockingFailureException` cleanly per-reservation during sweep in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java`

**Checkpoint**: Exact temporal boundary and optimistic concurrency resilience verified.

---

## Phase 6: User Story 4 - Visible Status and Action Feedback in Room Schedule (Priority: P3)

**Goal**: Expired and completed bookings display read-only badges in room schedule views, with activation and metadata modification strictly disallowed on terminal states.

**Independent Test**: Query room reservations endpoint for expired and completed bookings; attempt metadata updates and activations on terminal bookings; verify `409 Conflict` is returned and operational controls are disabled.

### Tests for User Story 4 (TDD - Mandatory Test-First) ⚠️

- [X] T021 [P] [US4] Controller tests verifying `POST /api/reservations/expire-unattended` and terminal state mutation rejection (`409 Conflict`) in `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationControllerTest.java`
- [X] T022 [P] [US4] Integration test verifying GET room reservation schedules contain `EXPIRED` status badges and exclude expired slots from conflicts in `backend/src/test/java/at/mci/igp/raumlotse/ReservationExpirationIntegrationTest.java`

### Implementation for User Story 4

- [X] T023 [US4] Enforce terminal state immutability in `updateReservationMetadata` in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java`, strictly rejecting updates if status is in `EXPIRED`, `COMPLETED`, or `CANCELLED`
- [X] T024 [US4] Validate frontend badge rendering and action button suppression for terminal states against `frontend/src/components/ReservationList/ReservationList.tsx` and run frontend test suite via `npm test` in `frontend/`

**Checkpoint**: Visual feedback, schedule queries, and terminal immutability verified across frontend and backend.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Operational observability, test coverage validation, and regression prevention.

- [X] T025 [P] Unit test scheduler delegation and exception resilience in `backend/src/test/java/at/mci/igp/raumlotse/service/ReservationExpirationSchedulerTest.java`
- [X] T026 [P] Verify structured logging adheres to Constitution Principle IV (no credentials or personal data) in `backend/src/test/java/at/mci/igp/raumlotse/config/ConfigurationPrivacyTest.java`
- [X] T027 Execute end-to-end quickstart validation per `specs/007-expire-unclaimed-reservations/quickstart.md` using `./dev.sh` and Maven verification

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Phase 2 completion — MVP delivery increment.
- **User Story 2 (Phase 4)**: Depends on Phase 2 completion and Phase 3 service methods.
- **User Story 3 (Phase 5)**: Depends on Phase 3 and Phase 4 service methods.
- **User Story 4 (Phase 6)**: Depends on Phase 3, Phase 4, and Phase 5 completion.
- **Polish (Phase 7)**: Depends on all user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Foundational queries ready. No dependencies on other stories.
- **User Story 2 (P1)**: Extends sweep service method from US1 to include active auto-completion.
- **User Story 3 (P2)**: Refines temporal boundary precision and concurrency handling built in US1 and US2.
- **User Story 4 (P3)**: Validates read-only presentation and terminal immutability across US1/US2 states.

### Within Each User Story

1. Unit and integration tests (TDD) written FIRST and verified failing.
2. Models / Queries implemented.
3. Service logic implemented.
4. Controllers / Schedulers connected.
5. Tests verified passing (Green).

### Parallel Opportunities

- **Phase 1**: T003 can run in parallel with T001/T002.
- **Phase 2**: T004, T005, and T006 can run concurrently.
- **User Story 1 Tests**: T007, T008, and T009 can be authored concurrently.
- **User Story 2 Tests**: T014 and T015 can be authored concurrently.
- **User Story 3 Tests**: T018 and T019 can be authored concurrently.
- **User Story 4 Tests**: T021 and T022 can be authored concurrently.
- **Polish Phase**: T025 and T026 can run concurrently.

---

## Parallel Example: User Story 1

```bash
# Launch test authorship concurrently:
Task: "Unit test unattended reservation expiration logic in ReservationServiceTest.java" (T007)
Task: "Unit test activation rejection on expired reservations in ReservationServiceTest.java" (T008)
Task: "Integration test full auto-expiration sweep lifecycle in ReservationExpirationIntegrationTest.java" (T009)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (`ClockConfig`, injection, constants)
2. Complete Phase 2: Foundational (queries and `ReservationSweepResponse`)
3. Complete Phase 3: User Story 1 (tests, expiration sweep, scheduler, controller endpoint)
4. **STOP and VALIDATE**: Verify User Story 1 passes all tests independently
5. Deliver/demonstrate MVP release for unclaimed room expiration

### Incremental Delivery

1. Setup + Foundational → Core time & query infrastructure ready
2. Add User Story 1 → Unattended reservations expire automatically (MVP)
3. Add User Story 2 → Overdue active meetings complete automatically
4. Add User Story 3 → Exact boundary precision and optimistic locking resilience verified
5. Add User Story 4 → Schedule badge display, terminal state immutability, and API contracts verified
6. Polish → Full test suite, privacy verification, and quickstart validation
