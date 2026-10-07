# Tasks: Room Status Visualization

**Input**: Design documents from `/specs/009-room-status-visualization/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Required by the project constitution. Write each story's tests first, verify failure, then implement the smallest change that makes them pass.

**Organization**: Tasks are grouped by user story and ordered by dependency and priority.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the existing display boundary and establish the focused validation commands.

- [X] T001 Verify the existing room-display route, reservation API calls, 30-second refresh behavior, and frontend test scripts in `frontend/src/pages/RoomDisplayPage.tsx`, `frontend/src/API/rooms.ts`, `frontend/src/API/reservations.ts`, and `frontend/package.json`.
- [X] T002 [P] Prepare reusable room and reservation fixtures covering `RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, and `CANCELLED` records in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`, `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`, and `frontend/src/pages/RoomDisplayPage.test.tsx`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Lock down the existing contract and time semantics before changing display behavior.

**Checkpoint**: No backend or database work is required; user-story implementation can begin after the focused fixtures and baseline are confirmed.

- [X] T003 [P] Document the unchanged room/reservation input contract and half-open interval `[startTime, endTime)` in `specs/009-room-status-visualization/contracts/room-status-display.md` and verify the existing TypeScript types in `frontend/src/types/reservation.ts`.
- [X] T004 Run the current focused room-display tests from `specs/009-room-status-visualization/quickstart.md` and record the baseline result before modifying production files.

---

## Phase 3: User Story 1 - Current Room State at a Glance (Priority: P1) 🎯 MVP

**Goal**: Show `Verfügbar` in green for a free room, `Reserviert` in yellow only during an unclaimed reservation period, and `Belegt` in red for a checked-in room.

**Independent Test**: Supply no reservation, a future `RESERVED` reservation, a current `RESERVED` reservation, and a current `ACTIVE` reservation; verify the derived status label and visual class for each case.

### Tests for User Story 1

> **Write first and verify these tests fail before implementation.**

- [X] T005 [P] [US1] Add failing unit tests for `AVAILABLE` with no reservation and with a future `RESERVED` reservation, `RESERVED` for a current unclaimed interval, `OCCUPIED` for a current `ACTIVE` interval, `ACTIVE` precedence, terminal-state exclusion, wrong-room exclusion, and `[startTime, endTime)` boundaries in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`.
- [X] T006 [P] [US1] Add failing component tests requiring visible `Verfügbar`, `Reserviert`, and `Belegt` labels, accessible status names, and status placement above the current date/time in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`.

### Implementation for User Story 1

- [X] T007 [US1] Update `deriveRoomDisplayStatus` in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts` to inspect only current records in `[startTime, endTime)`, return `OCCUPIED` for current `ACTIVE`, return `RESERVED` for current `RESERVED`, and return `AVAILABLE` for future-only reservations or no eligible records.
- [X] T008 [US1] Keep `selectCurrentReservation` in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts` aligned with the same current-interval, room-id, terminal-state, and deterministic tie-break rules used by status derivation.
- [X] T009 [US1] Change the available status label from `Frei` to `Verfügbar` and preserve semantic status attributes in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`; retain `Reserviert`, `Belegt`, and `Nicht verfügbar` labels.
- [X] T010 [US1] Ensure green, yellow, and red status treatments remain distinct and the status block stays above the clock in `frontend/src/components/RoomDisplay/RoomDisplay.css`, without making color the only status cue.

**Checkpoint**: User Story 1 is independently demonstrable and its focused logic/component tests pass.

---

## Phase 4: User Story 3 - Reflect State Changes Reliably (Priority: P1)

**Goal**: Reflect time and check-in transitions on the existing refresh cadence and never show misleading green or stale reservation details after a data failure.

**Independent Test**: Start with a future reservation, advance through its start time, change it to `ACTIVE`, refresh at 30 seconds, and simulate room/reservation load failure; verify each visible state.

### Tests for User Story 3

> **Write first and verify these tests fail before implementation.**

- [X] T011 [P] [US3] Add failing page tests for future `RESERVED` → current unclaimed `Reserviert`, current `RESERVED` → `ACTIVE` `Belegt`, end-boundary fallback, and 30-second room/reservation refresh in `frontend/src/pages/RoomDisplayPage.test.tsx`.
- [X] T012 [P] [US3] Add failing component/page tests proving unavailable room or reservation data renders an alert, does not render `Verfügbar`, and does not expose stale current-reservation details in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx` and `frontend/src/pages/RoomDisplayPage.test.tsx`.

### Implementation for User Story 3

- [X] T013 [US3] Update `frontend/src/pages/RoomDisplayPage.tsx` so clock and room/reservation refreshes continue every 30 seconds and recompute current status from the new time and reservation data.
- [X] T014 [US3] Preserve safe request cleanup and map missing room data or failed room/reservation requests to `UNAVAILABLE` in `frontend/src/pages/RoomDisplayPage.tsx` and `frontend/src/components/RoomDisplay/roomDisplayTypes.ts`.
- [X] T015 [US3] Ensure `frontend/src/components/RoomDisplay/RoomDisplay.tsx` suppresses successful status and reservation content in the unavailable state while preserving the room identity and clear fallback message.

**Checkpoint**: User Story 3 is independently demonstrable with time transitions, lifecycle changes, refresh, and failure scenarios.

---

## Phase 5: User Story 2 - See the Next Reservation (Priority: P2)

**Goal**: Keep the earliest future `RESERVED` booking visible as next-reservation information without changing a currently free room to `Reserviert`.

**Independent Test**: Supply zero, one, and multiple future reservations, including `ACTIVE` and terminal records, and verify the earliest eligible future booking and explicit empty state.

### Tests for User Story 2

> **Write first and verify these tests fail before implementation.**

- [X] T016 [P] [US2] Add or update unit tests for displayed-room filtering, future-only `RESERVED` selection, earliest start time, deterministic id tie-break, and exclusion of `ACTIVE`, `COMPLETED`, `EXPIRED`, and `CANCELLED` records in `frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts`.
- [X] T017 [P] [US2] Add or update component tests for the existing next-reservation line, `Reserviert für` fallback, explicit no-next-reservation state, and the requirement that a future booking coexists with visible `Verfügbar` in `frontend/src/components/RoomDisplay/RoomDisplay.test.tsx`.

### Implementation for User Story 2

- [X] T018 [US2] Preserve or correct `selectNextReservation` in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts` so it selects only `status == RESERVED` records with `startTime > now`, ordered by start time then id, independently of current status derivation.
- [X] T019 [US2] Verify `frontend/src/pages/RoomDisplayPage.tsx` passes the independently derived next reservation while using the current reservation only for current-state content.
- [X] T020 [US2] Retain the next-reservation labels, blank-`reservedFor` fallback, and explicit no-next-reservation message in `frontend/src/components/RoomDisplay/RoomDisplay.tsx`; ensure the existing one-line presentation remains intact in `frontend/src/components/RoomDisplay/RoomDisplay.css`.

**Checkpoint**: User Story 2 is independently demonstrable with future, absent, occupied, and terminal reservation scenarios.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validate the complete feature and preserve existing application behavior.

- [X] T021 [P] Run the focused room-display logic/component/page tests from `specs/009-room-status-visualization/quickstart.md` and resolve feature-related failures in `frontend/src/components/RoomDisplay/` and `frontend/src/pages/RoomDisplayPage.tsx`.
- [X] T022 [P] Run `npm run lint` and `npm run build` from `frontend/`; fix only feature-related TypeScript, accessibility, or styling issues in the scoped frontend files.
- [ ] T023 Run the full frontend test suite and the existing backend regression suite, confirming no change to the room/reservation contract documented in `specs/009-room-status-visualization/contracts/room-status-display.md`.
- [ ] T024 Run the manual validation scenarios in `specs/009-room-status-visualization/quickstart.md`, including future-only availability, current unclaimed reservation, checked-in occupancy, terminal records, refresh transitions, and failed data loading.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No implementation dependency; establishes fixtures and baseline commands.
- **Foundational (Phase 2)**: Depends on Setup and blocks user-story work.
- **User Story 1 (Phase 3)**: Depends on Phase 2 and is the MVP foundation for status derivation and labels.
- **User Story 3 (Phase 4)**: Depends on US1's status behavior; uses the existing page refresh boundary.
- **User Story 2 (Phase 5)**: Depends on the shared display behavior from US1 and can be developed in parallel with US3 when separate contributors avoid the same files.
- **Polish (Phase 6)**: Depends on all selected stories.

### User Story Dependencies

- **US1 (P1)**: No story dependency after Phase 2; MVP.
- **US3 (P1)**: Depends on US1's status labels and derivation; page refresh already exists and must be verified/retained.
- **US2 (P2)**: Selection logic is independently testable after Phase 2, but final UI validation shares the RoomDisplay component with US1/US3.

### Parallel Opportunities

- T002 and T003 can run in parallel after T001.
- T005 and T006 can run in parallel after Phase 2.
- T011 and T012 can run in parallel after US1.
- T016 and T017 can run in parallel after Phase 2; T018 follows their failing-test state.
- T021 and T022 can run in parallel after all desired stories; T023 and T024 follow the focused checks.

## Parallel Example: User Story 1

```text
Task T005: Add failing status-derivation tests in frontend/src/components/RoomDisplay/roomDisplayLogic.test.ts
Task T006: Add failing status-label/layout tests in frontend/src/components/RoomDisplay/RoomDisplay.test.tsx
```

## Parallel Example: User Story 3

```text
Task T011: Add failing lifecycle/refresh tests in frontend/src/pages/RoomDisplayPage.test.tsx
Task T012: Add failing unavailable-state tests in frontend/src/components/RoomDisplay/RoomDisplay.test.tsx
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete T001-T004.
2. Complete T005-T010 test-first.
3. Stop at the US1 checkpoint and validate all four status inputs: no reservation, future reservation, current unclaimed reservation, and current checked-in reservation.

### Incremental Delivery

1. Deliver US1 for correct immediate availability recognition.
2. Deliver US3 for reliable clock/data transitions and unavailable handling.
3. Deliver US2 for independent next-reservation information.
4. Run the Polish phase and full regression suite.

### Backend Scope

No backend production task is planned. Add backend work only if a regression test proves an existing response no longer supplies `roomId`, `startTime`, `endTime`, `status`, or `reservedFor`.

## Notes

- Every implementation task uses `- [ ] T###` with an explicit path.
- `[P]` marks tasks that can run in parallel without touching incomplete dependent work.
- `[US1]`, `[US2]`, and `[US3]` map directly to the specification's user stories.
- Existing half-open time semantics remain `startTime <= now < endTime`.
- Regression note: the full frontend suite reported 308 passing tests plus one pre-existing unhandled `EquipmentCatalog` mock error in `App.user-roles.test.tsx`; the backend suite could not initialize Mockito's Byte Buddy agent under the available JDK 25 runtime.
