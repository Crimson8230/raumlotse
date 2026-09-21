# Tasks: Display Room Information

**Input**: Design documents from `specs/005-display-room-information/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/room-display-view.md](./contracts/room-display-view.md), [quickstart.md](./quickstart.md)

**Tests**: Required by the project constitution's test-first development principle. Each story's tests must be written and observed failing before its implementation tasks begin.

**Organization**: Tasks are grouped by user story to enable independent implementation and validation.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the display feature's frontend file structure and test-first entry point.

- [X] T001 Create the RoomDisplay component and page directories at `frontend/src/components/RoomDisplay/` and `frontend/src/pages/` for the feature's implementation and tests
- [X] T002 [P] Write a failing RoomDisplay smoke test that establishes the initial test-first entry point in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Define shared typed presentation logic and test fixtures required by all user stories.

**⚠️ CRITICAL**: No user story implementation may begin until this phase is complete.

- [X] T003 [P] Define the `RoomDisplayViewModel` state types in `frontend/src/components/RoomDisplay/roomDisplayTypes.ts`
- [X] T004 [P] Add deterministic reservation-selection test fixtures covering `startTime <= currentTime < endTime`, matching `roomId`, eligible statuses `RESERVED`/`ACTIVE`, and terminal statuses `COMPLETED`/`EXPIRED`/`CANCELLED` in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`

**Checkpoint**: Shared display types and test fixtures are ready; user stories can proceed in priority order or in parallel where noted.

---

## Phase 3: User Story 1 - View Current Room Reservation on E-Ink Screen (Priority: P1) 🎯 MVP

**Goal**: Show one room's name, current date/time, current reservation note, current reservation creator as `Booked by: <createdBy>`, separately labeled start/end times, and the next eligible same-day reservation using existing room and reservation data, without separate title or lecturer fields.

**Independent Test**: Render a room with one eligible `RESERVED` or `ACTIVE` reservation whose required `createdBy` is `Albert Einstein` and a later eligible reservation on the same local calendar day; verify `Booked by: Albert Einstein`, current details, and one-line `Upcoming reservation` details appear while reservation-title and lecturer-name fields do not.

### Tests for User Story 1

> Write these tests first and verify they fail before implementing the display behavior.

- [X] T005 [P] [US1] Write failing current-reservation selection tests for matching room, half-open interval boundaries, and `RESERVED`/`ACTIVE` eligibility in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`
- [X] T006 [P] [US1] Write failing component tests for room name, current date/time, reservation note, separately labeled start/end times, and absence of title/lecturer fields in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`
- [X] T007 [P] [US1] Write failing page tests for loading room data through `frontend/src/API/rooms.ts` and reservation data through `frontend/src/API/reservations.ts` in `frontend/src/pages/RoomDisplayPage.test.tsx`

### Implementation for User Story 1

- [X] T008 [US1] Implement typed current-reservation selection and deterministic tie-breaking by earliest start time then smallest reservation id in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts`
- [X] T009 [US1] Implement the read-only display markup and data labels in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`, rendering the room name, current time, reservation note, start time, and end time only
- [X] T010 [US1] Add the initial e-ink-inspired layout and readable field styling in `frontend/src/components/RoomDisplay/RoomDisplay.css`
- [X] T011 [US1] Implement the `RoomDisplayPage` shell and room/reservation loading in `frontend/src/pages/RoomDisplayPage.tsx`, then register `/rooms/:roomId/display` in `frontend/src/App.tsx` only after the page shell exists
- [X] T026 [P] [US1] Write failing tests for selecting the first eligible later reservation on the same local calendar day, excluding next-day reservations, and applying earliest-start/id tie-breaking in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`
- [X] T027 [P] [US1] Write failing component tests for the one-line `Upcoming reservation` label and start/end times when a same-day upcoming reservation is available in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`
- [X] T028 [US1] Implement `selectUpcomingReservation` with room matching, `RESERVED`/`ACTIVE` filtering, `startTime > currentTime`, same-local-date filtering, and deterministic ordering in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts`
- [X] T029 [US1] Extend the typed display props/view model and page derivation to pass the selected upcoming reservation through `frontend/src/components/RoomDisplay/roomDisplayTypes.ts` and `frontend/src/pages/RoomDisplayPage.tsx`
- [X] T030 [US1] Render the `Upcoming reservation` section with one-line start/end information in `frontend/src/components/RoomDisplay/RoomDisplay.tsx` and its base layout in `frontend/src/components/RoomDisplay/RoomDisplay.css`
- [X] T041 [P] [US1] Write failing component and page tests proving the current reservation renders `Booked by: <createdBy>` and the upcoming one-line section does not add creator data in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx` and `frontend/src/pages/RoomDisplayPage.test.tsx`
- [X] T042 [US1] Render the current reservation's required `createdBy` value with the exact `Booked by:` label in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`, using the existing typed reservation response without a backend change

**Checkpoint**: US1 is independently usable as the MVP display for a room with a current reservation.

---

## Phase 4: User Story 2 - Recognize an Unoccupied Room (Priority: P2)

**Goal**: Keep the room identity and clock visible while clearly communicating that no eligible reservation is currently active, without showing stale, ended, future, or terminal reservations.

**Independent Test**: Render a known room with no eligible current reservation and verify the explicit no-current-reservation state; render failed data loading and verify the unavailable state contains no stale reservation details.

### Tests for User Story 2

> Write these tests first and verify they fail before implementing the empty/error behavior.

- [X] T012 [P] [US2] Write failing selection tests for ended, future, cancelled, completed, expired, wrong-room, and multiple-eligible-reservation cases in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`
- [X] T013 [P] [US2] Write failing component tests for `no-reservation` and `unavailable` states, including room name/current time retention and absence of stale reservation content, in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`
- [X] T014 [P] [US2] Write failing page tests for room-load failure and reservation-load failure in `frontend/src/pages/RoomDisplayPage.test.tsx`

### Implementation for User Story 2

- [X] T015 [US2] Extend `frontend/src/components/RoomDisplay/roomDisplayLogic.ts` to exclude terminal/future/ended/wrong-room reservations and apply the documented deterministic tie-breaker
- [X] T016 [US2] Implement explicit `no-reservation` and `unavailable` rendering with no stale reservation data in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`
- [X] T017 [US2] Complete error handling and stale-state clearing for room/reservation requests in `frontend/src/pages/RoomDisplayPage.tsx`
- [X] T018 [US2] Add empty and unavailable visual states to `frontend/src/components/RoomDisplay/RoomDisplay.css`
- [X] T031 [P] [US2] Write failing page and component tests proving a same-day upcoming reservation is shown even when no current reservation exists, while a next-day reservation is omitted in `frontend/src/pages/RoomDisplayPage.test.tsx` and `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`
- [X] T032 [US2] Integrate the upcoming selection into the no-current-reservation path without changing the explicit no-current-reservation status in `frontend/src/pages/RoomDisplayPage.tsx` and `frontend/src/components/RoomDisplay/RoomDisplay.tsx`

**Checkpoint**: US1 and US2 both work independently; viewers can trust the display when a room is unoccupied or data is unavailable.

---

## Phase 5: User Story 3 - Read the Screen at a Glance (Priority: P3)

**Goal**: Preserve readability at the intended mockup size, including long notes, while keeping the clock fresh and required information available without scrolling or navigation.

**Independent Test**: Render long and special-character notes with fake timers and verify readable bounded preview behavior, explicit truncation, semantic labels, and clock freshness.

### Tests for User Story 3

> Write these tests first and verify they fail before implementing readability and refresh behavior.

- [X] T019 [P] [US3] Write failing note-preview tests for null/empty/whitespace fallback `No note provided`, preserved visible text order, and explicit truncation for oversized notes in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`
- [X] T020 [P] [US3] Write failing component tests for the 800 × 480 CSS-pixel layout, minimum 14 px body text, long room/note layout, visible truncation indicator, no overlapping/scroll-required content, semantic labels, and non-color-only status communication in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`
- [X] T021 [P] [US3] Write failing fake-timer tests proving the displayed clock refreshes at least every 30 seconds and remains within the 60-second freshness requirement in `frontend/src/pages/RoomDisplayPage.test.tsx`

### Implementation for User Story 3

- [X] T022 [US3] Implement note fallback and bounded preview helpers with an explicit truncation marker in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts`
- [X] T023 [US3] Implement the mounted clock refresh with cleanup and existing local date/time formatting in `frontend/src/pages/RoomDisplayPage.tsx`
- [X] T024 [US3] Refine `frontend/src/components/RoomDisplay/RoomDisplay.css` for the 800 × 480 CSS-pixel mockup, minimum 14 px body text, readable typography, bounded note layout, wrapping, visible labels, and no required-field overlap
- [X] T025 [US3] Add semantic headings, labels, and text-based state indicators in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`
- [X] T033 [P] [US3] Write failing component tests proving the upcoming line remains visible while a long note is shortened with an explicit `...` indicator in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`
- [X] T034 [US3] Implement dynamic note-area reduction and explicit `...` truncation before omitting the one-line upcoming section in `frontend/src/components/RoomDisplay/RoomDisplay.css` and `frontend/src/components/RoomDisplay/RoomDisplay.tsx`
- [X] T043 [P] [US3] Add readability assertions for the `Booked by` label/value at the 800 × 480 CSS-pixel target without overlap or body text below 14 px in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`

**Checkpoint**: All three user stories are independently validated and the display meets the readability and freshness requirements.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validate the complete feature against the approved design artifacts and project quality gates.

- [X] T035 [P] Add route-level integration coverage for `/rooms/:roomId/display` and existing-route regression coverage in `frontend/src/App.test.tsx`
- [X] T036 [P] Run the complete frontend test suite and fix feature-related failures in `frontend/`
- [X] T037 [P] Run the TypeScript production build and resolve type errors in `frontend/`
- [X] T038 [P] Run ESLint and resolve feature-related violations in `frontend/`
- [ ] T039 Execute every manual scenario from `specs/005-display-room-information/quickstart.md`, including same-day upcoming, next-day exclusion, note-priority behavior, and at least 10 five-second usability trials with a 90% success threshold, and record any deviations in the feature handoff

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; creates feature directories and establishes the first failing smoke test. Route registration happens in T011 after the page shell exists.
- **Foundational (Phase 2)**: Depends on Setup; blocks all user-story work.
- **User Story 1 (Phase 3)**: Depends on Phase 2; delivers the MVP.
- **User Story 2 (Phase 4)**: Depends on Phase 2 and can reuse US1 display components; its selection/error tests may begin after Phase 2, but implementation integrates with US1.
- **User Story 3 (Phase 5)**: Depends on Phase 2 and the US1 display shell; its styling/clock work integrates with US1/US2 states.
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2; no dependency on another story. It establishes current and same-day upcoming reservation selection and rendering.
- **US2 (P2)**: Selection tests are independently startable after Phase 2; no-current-reservation upcoming rendering integrates with the US1 display shell.
- **US3 (P3)**: Tests are independently startable after Phase 2; note-priority layout implementation depends on the US1 upcoming markup and US2 states.

### Parallel Execution Examples

```text
After Phase 2:
- Developer A: T005, T006, T007 (US1 tests)
- Developer B: T012, T013, T014 (US2 tests)
- Developer C: T019, T020, T021 (US3 tests)

After US1 test tasks:
- Developer A: T008 (selection logic)
- Developer B: T009 + T010 (display markup and base styling)
- Developer C: T011 (page data loading)

After the display shell exists:
- Developer A: T015 + T016 (US2 selection and states)
- Developer B: T017 (page error/stale-state handling)
- Developer C: T018 (empty/unavailable styling)

After the current-reservation shell exists:
- Developer A: T026 + T028 (upcoming selection tests and logic)
- Developer B: T027 + T030 (upcoming component tests and markup)
- Developer C: T029 (typed page/view-model integration)

After US1 upcoming support exists:
- Developer A: T031 + T032 (no-current-reservation upcoming behavior)
- Developer B: T033 + T034 (note-priority layout and truncation)

After the current reservation display is available:
- Developer A: T041 (Booked-by tests)
- Developer B: T042 + T043 (Booked-by rendering and readability assertions)
```

### Within Each User Story

Tests MUST be written and observed failing before implementation. Shared logic precedes the components that consume it; page integration follows the component contract; styling and polish follow behavior that is already covered by tests.

## Implementation Strategy

1. **MVP first**: Complete Phase 1, Phase 2, and US1 to render one current reservation with note, `Booked by`, times, plus a same-day upcoming line when available.
2. **Trustworthy empty state**: Add US2 so ended, future, terminal, and unavailable data cannot appear as current.
3. **Physical-display quality**: Add US3 for bounded notes, semantic readability, and a fresh clock.
4. **Quality gate**: Complete Phase 6 and validate the quickstart scenarios before handing off to implementation review.

## Phase 7: Convergence

- [ ] T040 Execute and document the remaining manual quickstart validation at 800 × 480 CSS pixels, including same-day/upcoming and next-day exclusion, note-priority truncation with `...`, non-overlap/readability checks, and at least 10 five-second viewer trials with a minimum 90% success rate per SC-002, SC-005, SC-008, and T039 (partial)
- [X] T044 [P] Re-run the frontend tests, TypeScript build, and ESLint after the `Booked by` implementation and resolve any regressions in `frontend/`

## Phase 8: Convergence

- [ ] T045 Execute and document the complete manual quickstart validation at 800 × 480 CSS pixels, including `Booked by: <createdBy>`, same-day/upcoming and next-day exclusion, note-priority truncation with `...`, non-overlap/readability checks, and at least 10 five-second viewer trials with a minimum 90% success rate per SC-002, SC-005, SC-008, and T039/T040 (partial)

## Phase 9: Convergence

- [ ] T046 Perform the outstanding manual acceptance validation for the room display at 800 × 480 CSS pixels and attach evidence for `Booked by`, same-day Upcoming Reservation behavior, note truncation priority, non-overlap/readability, and at least 10 viewer trials achieving 90% or more success per SC-002, SC-005, and SC-008 (partial)
