---

description: "Actionable task list for the Admin-Statistikbereich feature"
---

# Tasks: Admin-Statistikbereich

**Input**: Design documents from `/specs/011-admin-statistics/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/admin-statistics.yaml](./contracts/admin-statistics.yaml), [quickstart.md](./quickstart.md)

**Tests**: Required by the project constitution. Every test task must be written and observed failing before the corresponding implementation task.

**Organization**: Tasks are grouped by user story. Shared query and authorization infrastructure is completed before story-specific work.

## Phase 1: Setup

**Purpose**: Establish the test and source-file touchpoints without changing existing behavior.

- [X] T001 [P] Create the backend service-test scaffold for invalid periods, empty datasets, status filtering, and metric formulas in `backend/src/test/java/at/mci/igp/raumlotse/service/AdminStatisticsServiceTest.java`.
- [X] T002 [P] Create the backend controller-test scaffold for authentication, admin authorization, valid date queries, and structured validation errors in `backend/src/test/java/at/mci/igp/raumlotse/controller/AdminStatisticsControllerTest.java`.
- [X] T003 [P] Create the repository integration-test scaffold with rooms, equipment, and reservations covering boundary-overlap and zero-use rows in `backend/src/test/java/at/mci/igp/raumlotse/repository/AdminStatisticsRepositoryIntegrationTest.java`.
- [X] T004 [P] Create the frontend page-test scaffold for admin rendering, shared date filters, loading/error/empty states, and metric cards in `frontend/src/pages/AdminStatisticsPage.test.tsx`.

---

## Phase 2: Foundational

**Purpose**: Implement shared contract, period, aggregation, and authorization primitives required by every user story.

**Checkpoint**: The backend can produce a typed, admin-protected snapshot for a valid period; no user-story UI is required yet.

- [X] T005 [P] Define the Java response records for `period`, `summary`, `rooms`, and `features`, including nullable cancellation rate and attendee average, in `backend/src/main/java/at/mci/igp/raumlotse/dto/AdminStatisticsResponse.java`.
- [X] T006 [P] Define the TypeScript response types and date-query helpers matching `contracts/admin-statistics.yaml` in `frontend/src/types/adminStatistics.ts`.
- [X] T007 Implement required `from`/`to` validation with `from < to`, local-date conversion in `Europe/Berlin`, exclusive `to`, and a bounded supported period in `backend/src/main/java/at/mci/igp/raumlotse/service/AdminStatisticsPeriod.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/AdminStatisticsService.java`.
- [X] T008 Implement read-only aggregation projections for clipped overlap duration `max(0, min(end,to) - max(start,from))`, room zero-use rows, feature joins, and status filters in `backend/src/main/java/at/mci/igp/raumlotse/repository/StatisticsRepository.java`.
- [X] T009 Implement the shared statistics service that combines repository results into one snapshot, uses `RESERVED`, `ACTIVE`, and `COMPLETED` for usage/attendee metrics, excludes `CANCELLED` and `EXPIRED` from actual usage, and returns `null` for undefined averages/quotas in `backend/src/main/java/at/mci/igp/raumlotse/service/AdminStatisticsService.java`.
- [X] T010 Extend the existing admin path matching so `/api/admin/statistics` requires `Role.ADMIN` and returns the established structured `401`/`403` responses in `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java`.
- [X] T011 Add the read-only `GET /api/admin/statistics?from=YYYY-MM-DD&to=YYYY-MM-DD` controller with documented `200`, `400`, `401`, `403`, and failure handling in `backend/src/main/java/at/mci/igp/raumlotse/controller/AdminStatisticsController.java`.
- [X] T012 Add the typed frontend request function using the existing same-origin API client and query parameters in `frontend/src/API/adminStatistics.ts`.

---

## Phase 3: User Story 1 - Statistikübersicht als Administrator öffnen (Priority: P1) 🎯 MVP

**Goal**: An authenticated administrator can open one protected statistics page, choose a shared period, and see a coherent snapshot; non-admins cannot access it.

**Independent Test**: Log in as an admin, open `/admin/statistics`, load a valid period, and verify the period plus all four metric groups render. Repeat as a non-admin and verify neither navigation nor API data is available.

### Tests for User Story 1

- [X] T013 [P] [US1] Complete controller contract tests for `200`, `400`, `401`, and `403` behavior against `/api/admin/statistics` in `backend/src/test/java/at/mci/igp/raumlotse/controller/AdminStatisticsControllerTest.java`.
- [X] T014 [P] [US1] Complete frontend route and navigation tests proving only admins see the statistics link and non-admins receive the existing denied state in `frontend/src/App.protected-routes.test.tsx` and `frontend/src/components/Navigation/Navigation.test.tsx`.
- [X] T015 [P] [US1] Complete page tests for default last-twelve-full-month period, explicit period display, one shared reload, loading state, API error state, and empty-period state in `frontend/src/pages/AdminStatisticsPage.test.tsx`.

### Implementation for User Story 1

- [X] T016 [US1] Add the `/admin/statistics` route under `RequireAuth` and `RequireAdmin`, and add the admin navigation link in `frontend/src/App.tsx` and `frontend/src/components/Navigation/Navigation.tsx`.
- [X] T017 [US1] Implement the date-period form, one-snapshot loader, accessible loading/error/empty states, and overview layout in `frontend/src/pages/AdminStatisticsPage.tsx`.
- [X] T018 [US1] Add the statistics page layout, period controls, status messaging, and responsive admin-page styling in `frontend/src/pages/AdminStatisticsPage.css`.

**Checkpoint**: US1 is independently usable as a protected overview even if the story-specific tables initially contain only their shared response data.

---

## Phase 4: User Story 2 - Raumauslastung vergleichen (Priority: P1)

**Goal**: Administrators can compare every room by valid booking count, clipped booked time, and calendar-time utilization.

**Independent Test**: Seed two rooms with different valid booking durations, one cancelled booking, and one unused room; load a period and verify the room rows, zero row, exclusion, formula, and sorting.

### Tests for User Story 2

- [X] T019 [P] [US2] Implement repository integration fixtures and assertions for valid statuses, cancelled/expired exclusion, half-open boundary clipping, deactivated historical rooms, and unused rooms in `backend/src/test/java/at/mci/igp/raumlotse/repository/AdminStatisticsRepositoryIntegrationTest.java`.
- [X] T020 [P] [US2] Add service assertions for `bookingCount`, `bookedSeconds`, and `utilizationPercent = bookedSeconds / periodSeconds * 100` in `backend/src/test/java/at/mci/igp/raumlotse/service/AdminStatisticsServiceTest.java`.
- [X] T021 [P] [US2] Add frontend tests for room rows, units, calendar-time utilization explanation, zero values, and deterministic utilization sorting in `frontend/src/pages/AdminStatisticsPage.test.tsx`.

### Implementation for User Story 2

- [X] T022 [US2] Implement room aggregation and response mapping for `roomId`, `roomName`, `roomStatus`, `bookingCount`, `bookedSeconds`, and `utilizationPercent` in `backend/src/main/java/at/mci/igp/raumlotse/repository/StatisticsRepository.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/AdminStatisticsService.java`.
- [X] T023 [US2] Render the room utilization table with booking count, formatted hours, percentage, status, zero-use state, and stable sortable columns in `frontend/src/pages/AdminStatisticsPage.tsx`.
- [X] T024 [US2] Add accessible table, numeric alignment, sorting controls, and utilization-help text styles in `frontend/src/pages/AdminStatisticsPage.css`.

**Checkpoint**: US1 and US2 work together, and room utilization can be validated independently from feature, attendee, and cancellation details.

---

## Phase 5: User Story 3 - Nutzung von Raumfeatures auswerten (Priority: P2)

**Goal**: Administrators can identify the most-used catalog equipment attached to rooms without inflating system-wide booking totals.

**Independent Test**: Seed one room with two features and another with one feature, create valid and cancelled reservations, and verify each valid reservation contributes once per assigned feature while the room/summary totals remain deduplicated.

### Tests for User Story 3

- [X] T025 [P] [US3] Add repository integration assertions for `room_equipment` joins, one booking counted once per assigned feature, unused catalog features, and cancelled/expired exclusion in `backend/src/test/java/at/mci/igp/raumlotse/repository/AdminStatisticsRepositoryIntegrationTest.java`.
- [X] T026 [P] [US3] Add service assertions for feature `bookingCount`, clipped `bookedSeconds`, active/deactivated feature labels, and unchanged room totals in `backend/src/test/java/at/mci/igp/raumlotse/service/AdminStatisticsServiceTest.java`.
- [X] T027 [P] [US3] Add frontend tests for feature rows, feature sorting, unused features, and the distinction between feature counts and total counts in `frontend/src/pages/AdminStatisticsPage.test.tsx`.

### Implementation for User Story 3

- [X] T028 [US3] Implement feature aggregation from catalog `EquipmentType` and `room_equipment` assignments, preserving historical names/statuses in `backend/src/main/java/at/mci/igp/raumlotse/repository/StatisticsRepository.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/AdminStatisticsService.java`.
- [X] T029 [US3] Render the room-feature usage table with booking count, formatted booked time, status, zero-use state, and stable sorting in `frontend/src/pages/AdminStatisticsPage.tsx`.
- [X] T030 [US3] Add feature-table responsive and explanatory styles in `frontend/src/pages/AdminStatisticsPage.css`.

**Checkpoint**: US3 can be accepted by comparing feature rows against seeded room assignments without changing any persistence data.

---

## Phase 6: User Story 4 - Personenanzahl pro Buchung beurteilen (Priority: P2)

**Goal**: Administrators see the average planned participants per valid reservation, with the included booking count and missing-data count.

**Independent Test**: Seed valid reservations with known `expectedAttendees`, one invalid/missing source value if possible, and cancelled reservations; verify the average excludes cancelled/expired rows and does not treat missing data as zero.

### Tests for User Story 4

- [X] T031 [P] [US4] Add service tests for `attendeeSum`, `attendeeBookingCount`, `averageExpectedAttendees`, `missingAttendeeCount`, null average, and cancelled/expired exclusion in `backend/src/test/java/at/mci/igp/raumlotse/service/AdminStatisticsServiceTest.java`.
- [X] T032 [P] [US4] Add controller serialization tests proving nullable `averageExpectedAttendees` is represented as `null` rather than a fabricated zero in `backend/src/test/java/at/mci/igp/raumlotse/controller/AdminStatisticsControllerTest.java`.
- [X] T033 [P] [US4] Add frontend tests for average attendees, included booking count, missing-data explanation, and no-data placeholder in `frontend/src/pages/AdminStatisticsPage.test.tsx`.

### Implementation for User Story 4

- [X] T034 [US4] Implement attendee aggregation from the existing `Reservation.expectedAttendees` field and map valid/missing counts in `backend/src/main/java/at/mci/igp/raumlotse/repository/StatisticsRepository.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/AdminStatisticsService.java`.
- [X] T035 [US4] Render average attendees, included reservation count, missing attendee count, and non-applicable state in the overview metric cards in `frontend/src/pages/AdminStatisticsPage.tsx`.
- [X] T036 [US4] Add accessible metric-card labels and formatting for averages and missing-data states in `frontend/src/pages/AdminStatisticsPage.css`.

**Checkpoint**: US4 is independently verifiable from the summary response and does not require actual attendance tracking.

---

## Phase 7: User Story 5 - Stornierungen nachvollziehen (Priority: P2)

**Goal**: Administrators see cancelled booking count and cancellation rate with an explicit denominator for the selected period.

**Independent Test**: Seed cancelled and non-cancelled reservations overlapping the period, plus an empty period; verify count, rate, zero-cancellation state, and non-applicable empty denominator.

### Tests for User Story 5

- [X] T037 [P] [US5] Add service tests for cancelled count, total-reservation denominator, 0% with reservations but no cancellations, and null rate with no reservations in `backend/src/test/java/at/mci/igp/raumlotse/service/AdminStatisticsServiceTest.java`.
- [X] T038 [P] [US5] Add repository/controller coverage for time-overlapping cancelled reservations and structured invalid-period errors in `backend/src/test/java/at/mci/igp/raumlotse/controller/AdminStatisticsControllerTest.java` and `backend/src/test/java/at/mci/igp/raumlotse/repository/AdminStatisticsRepositoryIntegrationTest.java`.
- [X] T039 [P] [US5] Add frontend tests for cancellation count, rate denominator explanation, 0%, and non-applicable empty-period state in `frontend/src/pages/AdminStatisticsPage.test.tsx`.

### Implementation for User Story 5

- [X] T040 [US5] Implement total, cancelled, and rate aggregation using all overlapping reservations as denominator and `CANCELLED` as numerator in `backend/src/main/java/at/mci/igp/raumlotse/repository/StatisticsRepository.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/AdminStatisticsService.java`.
- [X] T041 [US5] Render cancellation count, percentage, denominator explanation, and empty-denominator state in `frontend/src/pages/AdminStatisticsPage.tsx`.
- [X] T042 [US5] Add cancellation-card styles, percentage formatting, and accessible explanatory text in `frontend/src/pages/AdminStatisticsPage.css`.

**Checkpoint**: All five user stories are available in the same consistent snapshot and can be accepted against the contract and seeded data.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Complete quality gates, performance validation, accessibility, and documentation.

- [X] T043 [P] Add API response-contract assertions against `specs/011-admin-statistics/contracts/admin-statistics.yaml` in `backend/src/test/java/at/mci/igp/raumlotse/controller/AdminStatisticsControllerTest.java`.
- [X] T044 [P] Add large-dataset performance coverage for 100 rooms and 10,000 reservations and verify the 5-second first-result goal in `backend/src/test/java/at/mci/igp/raumlotse/repository/AdminStatisticsRepositoryIntegrationTest.java`.
- [X] T045 [P] Review keyboard navigation, labels, live regions, table headers, sorting announcements, responsive layout, and `—`/`0`/`null` distinctions in `frontend/src/pages/AdminStatisticsPage.tsx` and `frontend/src/pages/AdminStatisticsPage.css`.
- [X] T046 Run the feature validation scenarios from `specs/011-admin-statistics/quickstart.md` and record any deviations in `specs/011-admin-statistics/quickstart.md`.
- [X] T047 Run `./mvnw test` from `backend/`, `npm test`, `npm run lint`, and `npm run build` from `frontend/`; resolve any regressions in the files touched by this feature.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: No dependencies; creates failing test entry points.
- **Phase 2 Foundational**: Depends on Phase 1; blocks all user stories because every story uses the shared snapshot and admin-protected endpoint.
- **Phase 3 US1**: Depends on Phase 2; MVP and access baseline.
- **Phase 4 US2**: Depends on Phase 2 and can proceed independently of US3–US5; it uses the shared page from US1 for final integration.
- **Phase 5 US3**: Depends on Phase 2 and can proceed independently of US2, US4, and US5.
- **Phase 6 US4**: Depends on Phase 2 and can proceed independently of US2, US3, and US5.
- **Phase 7 US5**: Depends on Phase 2 and can proceed independently of US2, US3, and US4.
- **Phase 8 Polish**: Depends on all desired user stories being complete.

### User Story Dependencies

- **US1 (P1)**: No story dependency after Phase 2; establishes the protected page and common period interaction.
- **US2 (P1)**: No story dependency after Phase 2; final UI integration uses the US1 page shell.
- **US3 (P2)**: No story dependency after Phase 2; final UI integration uses the US1 page shell.
- **US4 (P2)**: No story dependency after Phase 2; final UI integration uses the US1 page shell.
- **US5 (P2)**: No story dependency after Phase 2; final UI integration uses the US1 page shell.

### Within Each User Story

- Write or complete tests first and observe failure before implementation.
- Complete repository/service behavior before page rendering that depends on it.
- Keep each story's response fields and UI independently testable.
- Run the story checkpoint before starting the next priority when working sequentially.

### Parallel Opportunities

- T001–T004 can run in parallel because they create separate test files.
- T005, T006, and T008 can run in parallel after their test scaffolds exist; T007 and T009 depend on the period and projection decisions.
- After Phase 2, US2, US3, US4, and US5 backend tests and aggregation work can run in parallel in separate test sections, while frontend edits to the shared page should be coordinated.
- Within each story, the backend test and frontend test tasks marked `[P]` can run in parallel before implementation.
- T043–T045 can run in parallel after all story implementation is complete.

## Implementation Strategy

### MVP First

1. Complete Phase 1 and Phase 2.
2. Complete US1, including admin authorization, shared period selection, and a coherent snapshot page.
3. Add US2 room utilization as the first decision-useful metric.
4. Stop and validate the protected overview plus room utilization independently.

### Incremental Delivery

1. Deliver US1 as the protected statistics-page baseline.
2. Deliver US2 for room utilization comparison.
3. Deliver US3 for equipment usage.
4. Deliver US4 for expected attendee averages.
5. Deliver US5 for cancellation metrics.
6. Run Phase 8 quality and performance validation.

## Notes

- `[P]` means the task can run in parallel because it touches a separate file or independent test section and has no dependency on incomplete work.
- Every task includes an exact repository path.
- No database migration is planned; if implementation discovers that existing data cannot support a required metric, stop and update the design artifacts before adding schema.
