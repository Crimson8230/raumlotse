# Tasks: User Reservation Integration

**Branch**: `009-user-reservations` | **Date**: 2026-09-28 | **Spec**: [spec.md](file:///home/simon/Progg/MCI/sem_5/raumlotse/specs/009-user-reservations/spec.md) | **Plan**: [plan.md](file:///home/simon/Progg/MCI/sem_5/raumlotse/specs/009-user-reservations/plan.md)

---

## Phase 1: Setup (Database Schema & Persistence)

**Purpose**: Database schema migration and foundational JPA entity mapping.

- [ ] T001 Create Flyway migration `backend/src/main/resources/db/migration/V10__add_reserved_for_and_user_reservation_index.sql` adding column `reserved_for VARCHAR(255)`, backfilling with `created_by`, setting `NOT NULL`, adding constraint `ck_reservation_reserved_for_nonempty CHECK (length(btrim(reserved_for)) BETWEEN 1 AND 255)`, and creating index `ix_reservation_user_upcoming ON reservation (created_by, status, end_time, start_time)`
- [ ] T002 Update `Reservation` JPA entity in `backend/src/main/java/at/mci/igp/raumlotse/domain/Reservation.java` with `@Column(name = "reserved_for", nullable = false) private String reservedFor;` along with getters and setters

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Data transfer objects, shared TypeScript types, and duration formatting utilities needed across user stories.

**⚠️ CRITICAL**: Must complete before user story implementation begins.

- [ ] T003 [P] Update backend DTOs: add `@NotBlank @Size(max = 255) String reservedFor` to `backend/src/main/java/at/mci/igp/raumlotse/dto/ReservationCreateRequest.java`, add `@Size(min = 1, max = 255) String reservedFor` to `backend/src/main/java/at/mci/igp/raumlotse/dto/ReservationUpdateRequest.java`, and add `String reservedFor` to `backend/src/main/java/at/mci/igp/raumlotse/dto/ReservationResponse.java`
- [ ] T004 [P] Update frontend reservation interfaces in `frontend/src/types/reservation.ts` adding `reservedFor: string` to `Reservation`, `reservedFor: string` to `ReservationCreatePayload`, and optional `reservedFor?: string` to `ReservationUpdatePayload`
- [ ] T005 [P] Implement duration formatting helper in `frontend/src/utils/date.ts` with unit tests in `frontend/src/utils/date.test.ts` converting time difference to localized strings (e.g. `"45 Min."` for < 60 min and `"1 Std. 30 Min."` for >= 60 min)

**Checkpoint**: Core models, DTOs, and shared utilities ready; story implementations can proceed.

---

## Phase 3: User Story 1 - Create Reservation Linked to Authenticated User and Designated Person (Priority: P1) 🎯 MVP

**Goal**: Authenticated user creating a reservation has their account ID automatically attributed as `createdBy`, while the mandatory `reservedFor` field is pre-filled with their display name and remains editable. Unauthenticated users are prevented from creating reservations.

**Independent Test**: As an authenticated user, submit a reservation accepting the default `reservedFor` display name and verify `createdBy` stores the user's ID and `reservedFor` stores the display name. Submit a second reservation overwriting `reservedFor` with custom text (e.g., "Web Project Group") and verify custom text is persisted. Verify submissions with empty `reservedFor` return validation error (400), and unauthenticated requests return 401 `AUTH_REQUIRED`.

### Tests for User Story 1 (Test-First) ⚠️

- [ ] T006 [P] [US1] Write failing controller tests in `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationControllerTest.java` verifying `POST /api/rooms/{roomId}/reservations` assigns `createdBy` from authenticated session principal (`AuthenticatedUser`), stores `reservedFor`, returns 400 Bad Request on blank or missing `reservedFor`, and returns 401 Unauthorized (`AUTH_REQUIRED`) when unauthenticated
- [ ] T007 [P] [US1] Write failing integration test in `backend/src/test/java/at/mci/igp/raumlotse/ReservationCreationIntegrationTest.java` testing end-to-end reservation creation asserting `createdBy` matches user account ID and `reservedFor` persists non-blank text
- [ ] T008 [P] [US1] Write failing component tests in `frontend/src/components/ReservationForm/ReservationForm.test.tsx` verifying `reservedFor` input pre-fills with `auth.user.displayName`, validates non-blank input, submits without manual `createdBy` input, and displays authentication prompt when unauthenticated

### Implementation for User Story 1

- [ ] T009 [US1] Update `createReservation` in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` to set `reservation.setCreatedBy(userId)` and `reservation.setReservedFor(request.reservedFor().trim())`, validating `reservedFor` length between 1 and 255
- [ ] T010 [US1] Update `POST /api/rooms/{roomId}/reservations` in `backend/src/main/java/at/mci/igp/raumlotse/controller/ReservationController.java` to extract `AuthenticatedUser` principal from Spring Security `Authentication`, rejecting unauthenticated requests with 401 `AUTH_REQUIRED`, and passing authenticated user ID to the service
- [ ] T011 [US1] Update `createReservation` in `frontend/src/API/reservations.ts` to include `reservedFor` in payload
- [ ] T012 [US1] Update `ReservationForm` in `frontend/src/components/ReservationForm/ReservationForm.tsx` to read `user` from `useAuth()`, pre-fill `reservedFor` with `user.displayName`, validate non-blank (max 255), remove legacy `createdBy` input, and render a sign-in redirect prompt when `user` is not authenticated

**Checkpoint**: User Story 1 (MVP) is fully functional and testable independently.

---

## Phase 4: User Story 2 - View "My Upcoming Reservations" on the Home Page (Priority: P1)

**Goal**: Authenticated user sees a dedicated overview table titled "My Upcoming Reservations" / "Meine nächsten Reservierungen" on the entry/home page displaying their upcoming bookings (Reservation Time, Room link to `/rooms/:id`, Duration), ordered chronologically ascending, displaying up to 10 entries.

**Independent Test**: Log in as User A and navigate to the home page. Verify table displays only User A's upcoming bookings (`createdBy == User A ID` and `status IN ('RESERVED', 'ACTIVE')` and `endTime > now`), sorted by `startTime` ascending, capped at 10 entries. Clicking room link navigates to room details. Verify User B bookings, past bookings, and cancelled bookings are omitted. Verify empty state message appears when user has no bookings.

### Tests for User Story 2 (Test-First) ⚠️

- [ ] T013 [P] [US2] Write failing repository test in `backend/src/test/java/at/mci/igp/raumlotse/repository/ReservationOccupancyIntegrationTest.java` verifying `findTop10ByCreatedByAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc` filters strictly by creator ID, future end time, active/reserved statuses, ascending order, and limit 10
- [ ] T014 [P] [US2] Write failing controller tests in `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationControllerTest.java` for `GET /api/reservations/my-upcoming` verifying HTTP 200 OK with list of reservations for authenticated user, 0% cross-user exposure, and HTTP 401 when unauthenticated
- [ ] T015 [P] [US2] Write failing component test in `frontend/src/components/MyUpcomingReservations/MyUpcomingReservations.test.tsx` verifying table rendering (Reservation Time, Room link, Duration), empty state message ("Keine anstehenden Reservierungen vorhanden."), error feedback, and loading state

### Implementation for User Story 2

- [ ] T016 [US2] Add query method `findTop10ByCreatedByAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc(String createdBy, Collection<ReservationStatus> statuses, Instant now)` to `backend/src/main/java/at/mci/igp/raumlotse/repository/ReservationRepository.java`
- [ ] T017 [US2] Implement `getMyUpcomingReservations(String createdBy)` in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` querying repository with `clock.instant()` and statuses `[RESERVED, ACTIVE]`
- [ ] T018 [US2] Implement endpoint `GET /api/reservations/my-upcoming` in `backend/src/main/java/at/mci/igp/raumlotse/controller/ReservationController.java` requiring authenticated user and returning `List<ReservationResponse>`
- [ ] T019 [US2] Add API function `getMyUpcomingReservations(): Promise<Reservation[]>` in `frontend/src/API/reservations.ts`
- [ ] T020 [US2] Create component `MyUpcomingReservations` in `frontend/src/components/MyUpcomingReservations/MyUpcomingReservations.tsx` and styling in `frontend/src/components/MyUpcomingReservations/MyUpcomingReservations.css` rendering the table with columns Zeitpunkt, Raum (link to `/rooms/:id`), and Dauer (using `formatDuration`), plus empty state
- [ ] T021 [US2] Integrate `MyUpcomingReservations` component into `frontend/src/pages/HomePage.tsx`

**Checkpoint**: User Stories 1 and 2 work independently; authenticated users can book rooms and view their personal upcoming reservations.

---

## Phase 5: User Story 3 - Unauthenticated Visitor Experience on Home Page (Priority: P2)

**Goal**: Unauthenticated visitor viewing the home page sees public content while the "My Upcoming Reservations" section is completely hidden from the DOM, with no 401 errors or personalized data queries triggered.

**Independent Test**: Visit the home page without an active session. Verify that the "My Upcoming Reservations" section is completely absent from the rendered DOM. Log in and verify that the section becomes visible.

### Tests for User Story 3 (Test-First) ⚠️

- [ ] T022 [P] [US3] Write failing page tests in `frontend/src/pages/HomePage.test.tsx` verifying "My Upcoming Reservations" is not rendered in the DOM when `useAuth().state !== 'authenticated'`, and renders when authenticated

### Implementation for User Story 3

- [ ] T023 [US3] Update `frontend/src/pages/HomePage.tsx` to conditionally mount `MyUpcomingReservations` only when `auth.state === 'authenticated'`, completely omitting the section and preventing network requests when unauthenticated

**Checkpoint**: Home page cleanly distinguishes authenticated and unauthenticated visitors.

---

## Phase 6: User Story 4 - Display Designated Person (`reservedFor`) in Room and Reservation Details (Priority: P3)

**Goal**: Room detail view and reservation schedule display `reservedFor` for each booking, and allow updating `reservedFor` alongside attendees and notes on existing `RESERVED` reservations.

**Independent Test**: View a room's reservation list on `/rooms/:id` and confirm that each reservation displays `reservedFor`. Edit a `RESERVED` booking's `reservedFor` to a new non-blank value and save; verify the updated name persists and displays without changing room, layout, or time window.

### Tests for User Story 4 (Test-First) ⚠️

- [ ] T024 [P] [US4] Write failing controller tests in `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationControllerTest.java` for `PATCH /api/reservations/{reservationId}` verifying `reservedFor` can be updated on `RESERVED` bookings, rejects blank strings (400), and rejects updates when status is not `RESERVED` (409)
- [ ] T025 [P] [US4] Write failing component tests in `frontend/src/components/ReservationList/ReservationList.test.tsx` verifying `reservedFor` is rendered on reservation cards, included in the inline edit form, and saved via `updateReservationMetadata`

### Implementation for User Story 4

- [ ] T026 [US4] Update `updateReservationMetadata` in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` to update `reservation.setReservedFor(request.reservedFor().trim())` when non-null and validate non-blank (max 255)
- [ ] T027 [US4] Update `ReservationList` in `frontend/src/components/ReservationList/ReservationList.tsx` to display `reservedFor` ("Reserviert für"), add `reservedFor` input to inline edit form, and pass it in payload to `updateReservationMetadata`
- [ ] T028 [US4] Update `RoomDisplay` in `frontend/src/components/RoomDisplay/RoomDisplay.tsx` and tests in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx` to render `reservedFor` for the active/upcoming reservation

**Checkpoint**: All 4 user stories are fully implemented and independently verifiable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Backwards compatibility of existing tests, regression checks, and end-to-end quickstart validation.

- [ ] T029 [P] Update existing backend tests in `backend/src/test/java/at/mci/igp/raumlotse/service/ReservationServiceTest.java`, `backend/src/test/java/at/mci/igp/raumlotse/ReservationExpirationIntegrationTest.java`, and `backend/src/test/java/at/mci/igp/raumlotse/RoomSearchIntegrationTest.java` to supply valid `reservedFor` in test requests
- [ ] T030 [P] Update existing frontend tests in `frontend/src/pages/RoomDetailPage.test.tsx` and `frontend/src/pages/RoomDisplayPage.test.tsx` to mock `reservedFor` in reservation test fixtures
- [ ] T031 Execute and document all manual validation scenarios from `specs/009-user-reservations/quickstart.md` (unauthenticated visitor check, creation with custom `reservedFor`, home page table links, and inline editing)

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Phase 1: Setup (Migration & Entity)
   └── Phase 2: Foundational (DTOs, Types, Utils)
          ├── Phase 3: User Story 1 (P1 - Create Reservation with createdBy & reservedFor) [MVP]
          │      └── Phase 4: User Story 2 (P1 - My Upcoming Reservations Dashboard)
          │             └── Phase 5: User Story 3 (P2 - Unauthenticated Visitor Isolation)
          └── Phase 6: User Story 4 (P3 - Display & Edit reservedFor in Room Details)
                 └── Phase 7: Polish & Cross-Cutting Concerns
```

- **Phase 1 (Setup)**: Can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1; blocks all user stories.
- **Phase 3 (User Story 1 - MVP)**: Can start immediately once Phase 2 is complete.
- **Phase 4 (User Story 2)**: Depends on Phase 2; integrates with data created in Phase 3.
- **Phase 5 (User Story 3)**: Depends on Phase 4 component integration.
- **Phase 6 (User Story 4)**: Depends on Phase 2 DTOs; can run in parallel with Phase 4/5.
- **Phase 7 (Polish)**: Runs after all user stories complete.

### Parallel Opportunities per Phase

- **Foundational**: T003, T004, T005 can all execute in parallel.
- **User Story 1 Tests**: T006 (controller test), T007 (integration test), and T008 (frontend test) can execute in parallel.
- **User Story 2 Tests**: T013 (repo test), T014 (controller test), and T015 (frontend test) can execute in parallel.
- **User Story 4 Tests**: T024 (backend test) and T025 (frontend test) can execute in parallel.
- **Polish**: T029 and T030 can execute in parallel.

---

## Parallel Example: User Story 1

```bash
# Launch test-first tests for User Story 1 in parallel:
Task: "T006 [P] [US1] Write failing controller tests in backend/.../ReservationControllerTest.java"
Task: "T007 [P] [US1] Write failing integration test in backend/.../ReservationCreationIntegrationTest.java"
Task: "T008 [P] [US1] Write failing component tests in frontend/.../ReservationForm.test.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Migration & Entity: T001, T002)
2. Complete Phase 2 (Foundational: T003, T004, T005)
3. Complete Phase 3 (User Story 1: T006–T012)
4. **VALIDATE MVP**: Verify authenticated users can book rooms with valid `createdBy` and `reservedFor`.

### Incremental Delivery

1. Foundation ready (Phases 1 & 2)
2. User Story 1 (MVP) delivered → room bookings linked to user accounts.
3. User Story 2 delivered → home page displays user's upcoming reservations table.
4. User Story 3 delivered → unauthenticated visitors cleanly isolated.
5. User Story 4 delivered → room schedules display and edit `reservedFor`.
6. Polish (Phase 7) → clean test suite and quickstart validation.
