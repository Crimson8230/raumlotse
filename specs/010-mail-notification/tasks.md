---

description: "Dependency-ordered implementation tasks for optional booking confirmation email"
---

# Tasks: Optional Booking Confirmation Email

**Input**: Design documents from `/specs/010-mail-notification/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/reservation-confirmation.yaml`, `quickstart.md`

**Tests**: Tests are mandatory under Constitution Principle I. Every test task below must be completed and observed failing before its corresponding production-code task begins.

**Organization**: Tasks are grouped by user story so the explicit opt-in MVP and failure-isolation increment can be implemented and validated separately.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes different files and does not depend on an unfinished task.
- **[Story]**: Maps the task to User Story 1 (`US1`) or User Story 2 (`US2`).
- Every task includes the exact repository-relative file path or paths it changes.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish an observable red test before introducing the approved mail dependency and local SMTP capture.

- [X] T001 Extend the migration sequence test to require `V12__create_booking_confirmation.sql` after V11 in `backend/src/test/java/at/mci/igp/raumlotse/MigrationVersionTest.java`; run it and record the expected failure before changing dependencies, configuration, migrations, or production code
- [X] T002 [P] Add `spring-boot-starter-mail` under the existing Spring Boot dependency management and verify dependency resolution in `backend/pom.xml`
- [X] T003 [P] Add a reviewed, pinned Mailpit service on SMTP `1025` and UI `8025`, wire non-secret local backend mail environment variables, and add corresponding placeholders in `docker-compose.yml` and `.env.example`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the tested persistence model, queue repository, and typed environment-backed configuration required by both user stories.

**⚠️ CRITICAL**: Complete this phase before user-story implementation. T001 must already be demonstrably red; execute T004–T006 and confirm they fail before T007–T010.

### Failing Tests

- [X] T004 [P] Write failing domain tests for `PENDING → PROCESSING → SENT|FAILED`, immutable terminal states, timestamp consistency, and the `attemptCount` constraint `0..1` in `backend/src/test/java/at/mci/igp/raumlotse/domain/BookingConfirmationTest.java`
- [X] T005 [P] Write failing PostgreSQL repository tests proving `reservationId` is required and unique, `status` is one of `PENDING|PROCESSING|SENT|FAILED`, `attemptCount` is `0..1`, state timestamps/failure code obey the data-model combinations, opt-in/email/body columns do not exist, and concurrent `FOR UPDATE SKIP LOCKED` claims select a row once in `backend/src/test/java/at/mci/igp/raumlotse/repository/BookingConfirmationRepositoryTest.java`
- [X] T006 [P] Write failing configuration tests proving sender/zone/polling values are typed, fixed-delay polling is at most one second, the stale threshold exceeds all finite SMTP connection/read/write timeouts, credentials are environment-backed, and tracked configuration contains no secret values in `backend/src/test/java/at/mci/igp/raumlotse/config/MailConfigurationPrivacyTest.java`

### Implementation

- [X] T007 Implement `BookingConfirmationStatus` and `BookingConfirmation` with generated non-null UUID `id`, required unique reservation relationship, required status, `attemptCount` constrained to `0..1`, required `createdAt`, status-dependent nullable timestamps, allowlisted `failureCode` of at most 64 characters, optimistic-lock `version`, and guarded terminal transitions in `backend/src/main/java/at/mci/igp/raumlotse/domain/BookingConfirmationStatus.java` and `backend/src/main/java/at/mci/igp/raumlotse/domain/BookingConfirmation.java`
- [X] T008 Create `booking_confirmation` with the exact state consistency checks, unique `reservation_id` foreign key, `attempt_count BETWEEN 0 AND 1`, partial pending/processing indexes, and no opt-in boolean, recipient email, or rendered message columns in `backend/src/main/resources/db/migration/V12__create_booking_confirmation.sql`
- [X] T009 Implement persistence, oldest-pending `FOR UPDATE SKIP LOCKED` claim selection, stale-processing selection, and status lookup methods in `backend/src/main/java/at/mci/igp/raumlotse/repository/BookingConfirmationRepository.java`
- [X] T010 Implement validated sender, fixed `Europe/Berlin` zone, fixed-delay polling of at most one second, bounded batch size, a stale threshold greater than all SMTP timeouts, and environment-backed finite SMTP settings in `backend/src/main/java/at/mci/igp/raumlotse/config/BookingConfirmationProperties.java` and `backend/src/main/resources/application.yaml`

**Checkpoint**: Domain, migration, repository, and configuration tests pass; the foundation contains no opt-in or mail-delivery behavior yet.

---

## Phase 3: User Story 1 — Explicitly Request an Email Confirmation (Priority: P1) 🎯 MVP

**Goal**: Show a default-off `Email Notification` checkbox before `Confirm Reservation`; successful opt-in creates and submits exactly one correct confirmation, while false/omitted opt-out, rejected bookings, and non-create activity produce none.

**Independent Test**: Complete one valid booking with the checkbox selected and one with it unselected. Verify both reservations succeed, only the selected booking creates one confirmation to the account email with matching room/start/end/zone, a newly mounted form is unchecked, and reloads or rejected attempts create no additional confirmation.

### Tests for User Story 1

> **Write and observe T011–T015 failing before implementing T016–T023.**

- [X] T011 [P] [US1] Write failing Zod schema and React tests proving the complete booking payload is runtime-validated, `seatingArrangementId` and every `additionalEquipmentTypeIds` item accept only UUIDs using contract-valid UUID fixtures, `emailNotification` accepts only boolean `true|false`, invalid or manipulated values prevent the API call through the existing error path, and `Email Notification` is accessible before `Confirm Reservation`, starts unchecked on every new mount, can be selected/deselected, and submits its final visible value in `frontend/src/components/ReservationForm/reservationFormSchema.test.ts` and `frontend/src/components/ReservationForm/ReservationForm.test.tsx`
- [X] T012 [P] [US1] Extend failing create-reservation contract tests to prove omitted and explicit `emailNotification:false` values bind as opt-out, `emailNotification:true` reaches the service, the existing authenticated `201` `ReservationResponse` has no mail-status field, and `400|401|404|409` behavior remains unchanged in `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationControllerTest.java`
- [X] T013 [P] [US1] Write failing PostgreSQL integration tests proving an authenticated successful create with `emailNotification:true` atomically commits one confirmation, false or omitted commits the reservation with zero confirmations, rejected/rolled-back creates commit none regardless of the flag, mixed distinct bookings create rows only for opted-in reservations, concurrent claims submit once, and reads/reloads/lifecycle actions enqueue nothing in `backend/src/test/java/at/mci/igp/raumlotse/BookingConfirmationIntegrationTest.java`
- [X] T014 [P] [US1] Write failing SMTP adapter tests for the exact account recipient, subject `Raumlotse: Buchung bestätigt – {roomName}`, UTF-8 German plain-text body, `dd.MM.yyyy HH:mm Europe/Berlin` start/end formatting, cross-day and daylight-saving boundaries, umlauts, and exclusion of notes/attendees/`reservedFor` in `backend/src/test/java/at/mci/igp/raumlotse/service/SmtpBookingConfirmationMailGatewayTest.java`
- [X] T015 [P] [US1] Write failing queue-service tests for one `PENDING` enqueue per opted-in reservation, oldest-row atomic claim to `PROCESSING` with one claimed attempt, successful finalization to immutable `SENT`, and no duplicate enqueue in `backend/src/test/java/at/mci/igp/raumlotse/service/BookingConfirmationQueueServiceTest.java`

### Implementation for User Story 1

- [X] T016 [P] [US1] Add required internal payload property `emailNotification: boolean` to `ReservationCreatePayload` while leaving reservation responses unchanged in `frontend/src/types/reservation.ts`
- [X] T017 [US1] Implement the Zod schema for parseable required start/end values with end strictly after start, UUID `seatingArrangementId`, positive integer attendees, `reservedFor` length `1..255`, optional note length at most `2000`, an optional array of UUID `additionalEquipmentTypeIds`, and boolean `emailNotification`; call `safeParse` before `createReservation`, map failures to the existing user-facing error path, and add controlled default-false checkbox state, exact `Email Notification` label before submit, final-value submission, and checkbox-specific layout in `frontend/src/components/ReservationForm/reservationFormSchema.ts`, `frontend/src/components/ReservationForm/ReservationForm.tsx`, and `frontend/src/components/ReservationForm/ReservationForm.css`
- [X] T018 [P] [US1] Add optional API property `emailNotification` to `ReservationCreateRequest` so `true` requests one confirmation and false or omission defaults to opt-out; keep existing constructor/test helpers compatible by defaulting them to false in `backend/src/main/java/at/mci/igp/raumlotse/dto/ReservationCreateRequest.java`
- [X] T019 [P] [US1] Define the testable mail boundary and immutable delivery command containing recipient, room name, start, and end—but no note, attendee count, credentials, or `reservedFor`—in `backend/src/main/java/at/mci/igp/raumlotse/service/BookingConfirmationMailGateway.java`
- [X] T020 [US1] Implement `JavaMailSender` submission, exact German subject/body contract, UTF-8 plain text, and explicit `Europe/Berlin` formatting in `backend/src/main/java/at/mci/igp/raumlotse/service/SmtpBookingConfirmationMailGateway.java`
- [X] T021 [P] [US1] Implement transactional enqueue, skip-locked claim, and `SENT` finalization operations with one logical row and one claimed attempt per opted-in reservation in `backend/src/main/java/at/mci/igp/raumlotse/service/BookingConfirmationQueueService.java`
- [X] T022 [US1] Implement the bounded fixed-delay worker happy path that claims in a short transaction, resolves `Reservation.createdByUserId → UserAccount.email`, performs SMTP outside database transactions, finalizes `SENT`, and logs only fixed event names plus confirmation/reservation UUIDs in `backend/src/main/java/at/mci/igp/raumlotse/service/BookingConfirmationWorker.java`
- [X] T023 [US1] Refactor authenticated reservation creation to enqueue after saving in the same transaction only when `request.emailNotification()` is true, create no row for false/omitted values, keep the API response unchanged, default the legacy direct-service overload to opt-out, and avoid enqueue calls from reads/updates/cancel/activate/complete/expire paths in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java`

**Checkpoint**: User Story 1 is independently functional: the checkbox resets to off, both opt-in and opt-out bookings succeed, and only opt-in produces one correct confirmation.

---

## Phase 4: User Story 2 — Preserve Booking During Delivery Failure (Priority: P2)

**Goal**: Convert missing-recipient, formatting, SMTP, and interrupted-worker outcomes for requested confirmations into observable terminal failures without changing or deleting the successful reservation and without automatic retries.

**Independent Test**: Select `Email Notification`, make the gateway fail after the valid reservation commits, and verify the reservation remains `RESERVED`, the confirmation becomes terminal `FAILED` with an allowlisted code, logs contain no personal/message/exception data, and later worker sweeps do not resubmit it.

### Tests for User Story 2

> **Write and observe T024–T026 failing before implementing T027–T028.**

- [X] T024 [P] [US2] Extend worker tests for missing account/email, message-format failure, SMTP rejection, no automatic retry, and logs excluding email, room name, times, body, credentials, and raw exception text in `backend/src/test/java/at/mci/igp/raumlotse/service/BookingConfirmationWorkerTest.java`
- [X] T025 [P] [US2] Extend queue-service tests for terminal `FAILED`, persisted allowlisted codes `RECIPIENT_UNAVAILABLE|MESSAGE_FORMAT_FAILED|SMTP_REJECTED|WORKER_INTERRUPTED`, correct `failedAt`, immutable reservation association, and stale `PROCESSING` recovery without a second claim in `backend/src/test/java/at/mci/igp/raumlotse/service/BookingConfirmationQueueServiceTest.java`
- [X] T026 [P] [US2] Extend PostgreSQL integration coverage so an opted-in gateway outage leaves the reservation unchanged in `RESERVED`, records one terminal failure, exposes no opt-in/email/body data, and remains unsent after a later healthy sweep in `backend/src/test/java/at/mci/igp/raumlotse/BookingConfirmationIntegrationTest.java`

### Implementation for User Story 2

- [X] T027 [US2] Implement `FAILED` finalization and stale `PROCESSING → FAILED(WORKER_INTERRUPTED)` recovery as short transactions, reject all terminal-state mutations, and persist only fixed codes/timestamps without exception details in `backend/src/main/java/at/mci/igp/raumlotse/service/BookingConfirmationQueueService.java`
- [X] T028 [US2] Add missing-recipient, format, SMTP, stale-work, and finalization-failure handling that never mutates reservations, never retries terminal rows, persists only the four allowlisted failure codes, and emits `FINALIZATION_FAILED` exclusively as a fixed privacy-safe log event without raw exception messages in `backend/src/main/java/at/mci/igp/raumlotse/service/BookingConfirmationWorker.java`

**Checkpoint**: User Stories 1 and 2 pass independently; opt-in success, opt-out silence, and isolated terminal failure are demonstrable without changing the reservation response.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Complete documentation, privacy checks, regression gates, and end-to-end acceptance evidence.

- [X] T029 [P] Document default-off per-booking checkbox behavior, backward-compatible omitted-request behavior, Mailpit URL, local SMTP behavior, production `MAIL_*` variables, finite timeout expectations, and rationale for the mail dependency/gateway boundary in `README.md`
- [X] T030 Run the focused frontend and backend commands from `specs/010-mail-notification/quickstart.md`; record commands, test counts, and results in `specs/010-mail-notification/quickstart.md`
- [X] T031 Run the complete backend test suite with PostgreSQL Testcontainers and record failures/skips or the clean result in `specs/010-mail-notification/quickstart.md`
- [X] T032 Run frontend regression gates `npm test`, `npm run lint`, and `npm run build`, then record outcomes in `specs/010-mail-notification/quickstart.md`
- [ ] T033 Execute Quickstart scenarios A–F against the browser UI and local Mailpit, including default opt-out, explicit opt-in, final checkbox state, rejected booking, SMTP outage/no retry, and mixed distinct bookings; record evidence in `specs/010-mail-notification/quickstart.md`
- [ ] T034 Execute Quickstart scenario G with 100 successful opted-in bookings and no more than 20 concurrent requests; verify at least 95 confirmations are accepted within 120 seconds of their booking completion and record timestamps, counts, elapsed times, and failures in `specs/010-mail-notification/quickstart.md`
- [ ] T035 Execute Quickstart scenario H with at least 10 representative users; verify at least 9 explain the opt-in effect within 15 seconds and at least 9 identify room/start/end from opted-in mail within 30 seconds, then record anonymized results in `specs/010-mail-notification/quickstart.md`
- [X] T036 Validate `specs/010-mail-notification/contracts/reservation-confirmation.yaml`, scan tracked configuration and log-test output for credentials/personal data, verify `/api/health`, and document final Constitution I–V compliance in `specs/010-mail-notification/quickstart.md`
- [ ] T037 Copy the architectural decisions and rationale for the optional false-default request field, non-persisted choice, `spring-boot-starter-mail`, Mailpit, SMTP gateway abstraction, and durable database queue from `specs/010-mail-notification/research.md` into the introducing pull request description as required by Constitution Principle V

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: No dependencies. T001 must be written and observed failing first; T002 and T003 may then run in parallel.
- **Phase 2 — Foundational**: Depends on Phase 1. T004–T006 are parallel failing-test tasks; T007–T010 implement the tested foundation and block both stories.
- **Phase 3 — User Story 1**: Depends on Phase 2. This MVP establishes the UI choice, backward-compatible request contract, conditional enqueue, and successful delivery pipeline.
- **Phase 4 — User Story 2**: Depends on the requested-confirmation pipeline from User Story 1, then independently adds and validates failure isolation.
- **Phase 5 — Polish**: Depends on the desired story phases; full acceptance requires both stories.

### User Story Dependency Graph

```text
Setup
  └── Foundation
        └── US1: Explicit opt-in and successful confirmation (MVP)
              └── US2: Delivery failure isolation
                    └── Polish and full validation
```

### Within Each User Story

- Write all listed tests first and capture a failing result.
- Add typed request shapes before form/service integration.
- Implement domain/gateway boundaries before worker orchestration.
- Keep transaction-only queue operations separate from SMTP network I/O.
- Complete focused tests before advancing to the next story.
- Do not introduce a stored notification preference or public notification-status endpoint.

### Parallel Opportunities

- After the required red T001 result, T002 and T003 can run concurrently.
- T004, T005, and T006 can run concurrently before foundational implementation.
- T011–T015 can run concurrently as the failing US1 test set because they target separate frontend/backend test concerns.
- After those tests are red, T016, T018, T019, and T021 can proceed in parallel; T017 follows T016, T020 follows T019, and T022/T023 integrate the completed pieces.
- T024, T025, and T026 can run concurrently as the failing US2 test set.
- T029 can proceed independently after behavior stabilizes while T030–T036 record validation sequentially in the shared quickstart file.

---

## Parallel Example: User Story 1

```text
Task T011: ReservationForm schema, checkbox, and payload tests
Task T012: Reservation-create request contract tests
Task T013: Conditional transactional integration tests
Task T014: SMTP content/format adapter tests
Task T015: Queue happy-path state tests
```

After the test set is demonstrably red:

```text
Task T016: TypeScript request payload property
Task T018: Java request DTO property and false default
Task T019: Mail gateway contract
Task T021: Transactional queue service happy path
```

T017 implements the schema and form after T016; T020 implements the SMTP adapter after T019; T022 and T023 then integrate worker and conditional reservation creation.

## Parallel Example: User Story 2

```text
Task T024: Worker failure/privacy tests
Task T025: Queue failure/stale-state tests
Task T026: Database-level reservation-survival and no-retry tests
```

T027 and T028 then implement the tested queue and worker behavior sequentially because they update the same production files exercised in US1.

---

## Implementation Strategy

### MVP First — User Story 1

1. Write and run T001 first, preserve the red result, then complete Setup and Foundation.
2. Write and run T011–T015 to establish the US1 red test set.
3. Complete T016–T023 with Red-Green-Refactor.
4. Stop and validate the independent test: opted-in and opted-out bookings both succeed, only opt-in sends one correct email, and each new form starts unchecked.
5. Demo scenarios A–D and F against Mailpit if only the successful-flow MVP is required.

### Incremental Delivery

1. **Foundation**: Durable constrained queue and typed configuration.
2. **US1 MVP**: Default-off checkbox, compatible request flag, conditional enqueue, and successful asynchronous SMTP delivery.
3. **US2**: Reservation-safe terminal failures and privacy-safe observability for requested confirmations.
4. **Polish**: Full backend/frontend gates and manual UI/Mailpit acceptance.

### Parallel Team Strategy

1. After T001 establishes the required red test, one developer handles mail dependency/local infrastructure while another writes foundational tests.
2. After Foundation, frontend checkbox tests, API contract tests, database integration tests, SMTP tests, and queue tests can be authored in parallel.
3. Once the US1 red set exists, frontend payload/form work can proceed in parallel with backend DTO, gateway, and queue work before worker/service integration.
4. After US1, split US2 failure tests across worker, queue, and integration files, then converge production changes in dependency order.

---

## Notes

- `[P]` means different files and no incomplete dependency, not merely tasks that look similar.
- `[US1]` and `[US2]` provide direct traceability to `spec.md`.
- Missing or false `emailNotification` means opt-out; only true may create a confirmation row.
- The unique queue row represents one requested logical confirmation; the design deliberately performs no automatic retry after an ambiguous SMTP outcome.
- The queue stores no opt-in boolean, duplicated recipient address, or rendered mail body.
- Commit after each task or coherent Red-Green-Refactor group if the optional git hook is later invoked.
