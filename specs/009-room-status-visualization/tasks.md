# Tasks: Room Status Visualization

**Input**: Design documents from `/specs/009-room-status-visualization/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Tests are required by the project constitution. Each story's tests must be written and observed failing before its implementation tasks are completed.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the existing display boundary and test commands before feature work.

- [X] T001 Confirm the existing room-display route, API client calls, and test commands against `frontend/src/pages/RoomDisplayPage.tsx`, `frontend/src/API/rooms.ts`, `frontend/src/API/reservations.ts`, and `frontend/package.json`
- [X] T002 [P] Add complete reusable room/reservation fixtures, including `reservedFor` and each lifecycle status, in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts` and `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Confirm that no additional shared infrastructure is required before the story-specific TDD work.

**⚠️ CRITICAL**: Complete this phase before user-story implementation.

**Checkpoint**: Existing project structure and fixtures are ready; the first production-code change is gated by a failing user-story test.

---

## Phase 3: User Story 1 - Recognize Room Availability at a Glance (Priority: P1) 🎯 MVP

**Goal**: Show green/available, yellow/reserved, or red/reserved-and-occupied status with text that remains understandable without color.

**Independent Test**: Supply no future booking, a future `RESERVED` booking, and a current `ACTIVE` booking and verify the display's derived state, visible text label, and status treatment for each case.

### Tests for User Story 1

> **Write first and verify these tests fail before implementation.**

- [X] T003 [P] [US1] Add failing unit tests for current-reservation filtering, `ACTIVE` precedence, future `RESERVED` detection, terminal-state exclusion, wrong-room exclusion, half-open `[startTime, endTime)` boundaries, and deterministic id tie-breaking in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`
- [X] T004 [P] [US1] Add failing component tests for visible `Available`, `Reserved`, and `Reserved and Occupied` labels plus non-color status semantics in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`

### Implementation for User Story 1

- [X] T005 [US1] Define the typed room-display status/view-model values and props needed for `AVAILABLE`, `RESERVED`, `OCCUPIED`, and `UNAVAILABLE` states in `frontend/src/components/RoomDisplay/roomDisplayTypes.ts`
- [X] T006 [US1] Implement typed status derivation and current-reservation selection in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts`, using only displayed-room records and the existing lifecycle statuses
- [X] T007 [US1] Update `frontend/src/pages/RoomDisplayPage.tsx` to pass the derived status and current reservation into the display while preserving the existing unavailable and loading behavior
- [X] T008 [US1] Render the status label and semantic status attributes in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`, keeping the room name and current date/time visible in every successful state
- [X] T009 [US1] Add green/yellow/red status treatments, visible text cues, and accessible styling hooks in `frontend/src/components/RoomDisplay/RoomDisplay.css` without relying on color alone
- [X] T025 [US1] Add a failing component/layout assertion that the rendered room-status element precedes the current date/time element in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`
- [X] T026 [US1] Reorder the successful room-display markup so the colored/text status appears above the current date and time in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`, and update `frontend/src/components/RoomDisplay/RoomDisplay.css` to preserve the intended hierarchy

**Checkpoint**: User Story 1 is independently demonstrable with all three status cases and its tests pass.

---

## Phase 4: User Story 2 - See the Next Reservation (Priority: P1)

**Goal**: Show the earliest future `RESERVED` booking under `Next Reservation` with `Reserved for:`, `Start Time`, and `End Time`.

**Independent Test**: Supply zero, one, and multiple future reservations, including an `ACTIVE` and terminal record, and verify that only the earliest future `RESERVED` record is shown.

### Tests for User Story 2

> **Write first and verify these tests fail before implementation.**

- [X] T010 [P] [US2] Add failing unit tests for `Next Reservation` selection by room, status `RESERVED`, `startTime > now`, earliest start time, deterministic id tie-break, and exclusion of `ACTIVE`, `COMPLETED`, `EXPIRED`, and `CANCELLED` records in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`
- [X] T011 [P] [US2] Add failing component tests for the `Next Reservation` heading, `Reserved for:`, `Start Time`, `End Time`, explicit no-next-reservation state, and the visible fallback when `reservedFor` is blank in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`

### Implementation for User Story 2

- [X] T012 [US2] Implement `selectNextReservation` and the associated derived view-model data in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts`, selecting only future `RESERVED` records and returning `null` when none qualify
- [X] T013 [US2] Connect next-reservation derivation from `frontend/src/pages/RoomDisplayPage.tsx` to `frontend/src/components/RoomDisplay/RoomDisplay.tsx`, including the current-room id and current time inputs
- [X] T014 [US2] Render the `Next Reservation` section and its exact labels/fallback in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`, using `reservedFor` and the existing date/time formatting utilities
- [X] T015 [US2] Add readable layout rules for the next-reservation section and long `reservedFor` values in `frontend/src/components/RoomDisplay/RoomDisplay.css`
- [X] T027 [US2] Add failing component assertions that `Next Reservation`, `Reserved for:`, `Start Time`, and `End Time` are rendered together as one readable line in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`
- [X] T028 [US2] Replace the multi-block next-reservation markup with a compact single-line presentation in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`, retaining the blank-`reservedFor` fallback and all required values
- [X] T029 [US2] Update `frontend/src/components/RoomDisplay/RoomDisplay.css` so the complete next-reservation line remains readable without wrapping, overlap, or hidden labels/values at the intended display size

**Checkpoint**: User Story 2 is independently demonstrable with future, absent, occupied, and terminal reservation scenarios.

---

## Phase 5: User Story 3 - Understand Status Changes Reliably (Priority: P2)

**Goal**: Keep status and next reservation current as time and reservation state change, and avoid misleading green/stale output on data failures.

**Independent Test**: Advance the display clock across reservation boundaries, update mocked reservation data, and simulate room/reservation loading failure; verify refreshed state and unavailable handling.

### Tests for User Story 3

> **Write first and verify these tests fail before implementation.**

- [X] T016 [P] [US3] Add failing page tests for yellow-to-red, red-to-yellow/green, cancelled/expired next-booking replacement, and reservation refresh on the 30-second cadence in `frontend/src/pages/RoomDisplayPage.test.tsx`
- [X] T017 [P] [US3] Add failing component tests ensuring unavailable data shows an alert, never renders green/available, and does not show stale reservation details in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`

### Implementation for User Story 3

- [X] T018 [US3] Update the mounted refresh effect in `frontend/src/pages/RoomDisplayPage.tsx` to refresh reservation/room data on the existing 30-second cadence while retaining safe cleanup for unmounted or changed room ids
- [X] T019 [US3] Ensure failed refreshes and unresolved room data produce the typed `UNAVAILABLE` display state in `frontend/src/pages/RoomDisplayPage.tsx` and `frontend/src/components/RoomDisplay/roomDisplayTypes.ts`
- [X] T020 [US3] Finalize transition-safe rendering and accessible fallback messaging in `frontend/src/components/RoomDisplay/RoomDisplay.tsx` and `frontend/src/components/RoomDisplay/RoomDisplay.css`

**Checkpoint**: User Story 3 is independently demonstrable with time transitions, lifecycle changes, and unavailable data.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validate the complete feature and preserve existing behavior.

- [X] T021 [P] Run the focused room-display unit/component/page suite from `specs/009-room-status-visualization/quickstart.md` and resolve failures in the referenced frontend files
- [X] T022 [P] Run `npm run lint` and `npm run build` from `frontend/`, fixing only feature-related TypeScript, accessibility, or styling issues in `frontend/src/components/RoomDisplay/` and `frontend/src/pages/RoomDisplayPage.tsx`
- [ ] T023 Run the full frontend test suite and the existing backend regression suite, confirming that the unchanged room/reservation API contracts remain compatible with `specs/009-room-status-visualization/contracts/room-status-display.md`
- [ ] T024 Run the manual scenarios in `specs/009-room-status-visualization/quickstart.md` against a room with no booking, a future `RESERVED` booking, a current `ACTIVE` booking, terminal bookings, and a simulated data failure; additionally validate with at least 10 first-time viewers that status is identified within 5 seconds
- [ ] T030 Run the updated layout scenarios in `specs/009-room-status-visualization/quickstart.md`, confirming status-before-clock order and one-line next-reservation readability at the intended display size

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No implementation dependency; establishes fixtures and baseline commands.
- **Foundational (Phase 2)**: Depends on Setup and blocks all user-story implementation.
- **User Story 1 (Phase 3)**: Depends on Phase 2 and is the MVP foundation for the display status model.
- **User Story 2 (Phase 4)**: Depends on Phase 2; it reuses the view-model and display boundary from US1, so in the single-developer flow it follows US1.
- **User Story 3 (Phase 5)**: Depends on the status and next-reservation behavior from US1/US2 plus Phase 2.
- **Polish (Phase 6)**: Depends on all desired user stories.

### User Story Dependencies

- **US1 (P1)**: No story dependency after Phase 2; MVP.
- **US2 (P1)**: Logically independent selection tests can start after Phase 2, but UI integration depends on the shared display model from US1.
- **US3 (P2)**: Depends on the completed US1/US2 display model and refresh integration.

### Within Each User Story

- Write the listed tests first and confirm they fail.
- Implement logic before page wiring, then component rendering and CSS.
- Run the story's checkpoint tests before starting the next story.

### Parallel Opportunities

- T002 and T003 can run in parallel after T001.
- T004 and T005 can run in parallel; T006 follows their failing-test state.
- T010 and T011 can run in parallel after Phase 2; T012 follows their failing-test state.
- T016 and T017 can run in parallel after US1/US2 behavior is available.
- T021, T022, and T030 can run in parallel after the clarified UI changes; T023 and T024 follow the focused checks.

## Parallel Example: User Story 1

```text
Task T004: Add failing selection/status unit tests in frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts
Task T005: Add failing status-label component tests in frontend/src/components/RoomDisplay/RoomDisplay.test.tsx
```

## Parallel Example: User Story 2

```text
Task T010: Add failing next-reservation unit tests in frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts
Task T011: Add failing next-reservation component tests in frontend/src/components/RoomDisplay/RoomDisplay.test.tsx
Task T027: Add failing one-line layout assertions in frontend/src/components/RoomDisplay/RoomDisplay.test.tsx
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete T001-T002.
2. Complete T003-T009 using test-first execution.
3. Stop at the US1 checkpoint and validate all three color/status cases.
4. Demo the display before adding next-reservation details.

### Incremental Delivery

1. Add US1 for immediate availability recognition.
2. Add US2 for the earliest future reservation details.
3. Add US3 for reliable refresh and failure behavior.
4. Apply the clarified status ordering and one-line reservation layout.
5. Run the Polish phase and the full regression suite.

### Backend Scope

No backend production task is required: the existing room and reservation endpoints already
provide the fields in the contract. Add backend work only if a contract regression test proves
that an existing response no longer supplies `roomId`, `startTime`, `endTime`, `status`, or
`reservedFor`; in that case, update the smallest existing DTO/controller path and its tests.

## Notes

- Every task uses the required `- [ ] T###` checklist format.
- `[P]` marks tasks that touch different files and have no dependency on incomplete work.
- `[US1]`, `[US2]`, and `[US3]` map directly to the prioritized stories in `spec.md`.
- Existing half-open time semantics remain `startTime <= now < endTime`.
