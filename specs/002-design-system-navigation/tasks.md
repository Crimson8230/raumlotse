---

description: "Task list for feature implementation"
---

# Tasks: Design System & Navigation

**Input**: Design documents from `/specs/002-design-system-navigation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/design-tokens.md, contracts/navigation-component.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project: every task that introduces new logic (the Navigation component's active-route/link logic, the semantic status-class mapping) is preceded by a failing test task. **Scoping note (explicit project decision, not a silent gap)**: Principle I's "failing test before implementation" applies to logic-bearing changes — conditional rendering, state-to-class mapping, active-route detection. Pure token/CSS-only restyling with no conditional logic (T011, T012, T013, T015 — applying shared tokens to already-existing static markup) has no logic for a test to exercise; jsdom does not render CSS, so an RTL assertion there would be contrived. These tasks are instead verified via `npm run lint`, `npm run build`, and the manual `quickstart.md` walkthroughs — see Polish phase (T022, T024).

**Organization**: Tasks are grouped by user story (spec.md) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- File paths are relative to the repository root

## Path Conventions

Web app layout per plan.md: `frontend/src/...` (this feature touches only `frontend/`; `backend/` is unchanged).

---

## Phase 1: Setup

**Purpose**: Confirm a clean starting point before any design-system changes.

- [X] T001 In `frontend/`, run `npm run lint && npm run test && npm run build` to confirm the current baseline (post room-management merge) is clean before this feature's changes begin. Fix or report any pre-existing failures before proceeding — do not build the design system on top of a broken baseline.

**Checkpoint**: Baseline confirmed clean.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the shared Design Token contract (contracts/design-tokens.md, data-model.md "Design Token") that every user story below consumes. No page/component restyling or navigation work should start before this phase is done.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 In `frontend/src/index.css`, add a spacing scale (`--space-1` through `--space-5`) and a typography scale (`--font-size-heading`, `--font-size-body`, `--font-size-small`) to the existing `:root` block, alongside the current `--color-*` tokens. Values must be a small, fixed set (per contracts/design-tokens.md "typography/spacing" row) — do not introduce more than 5 spacing steps or 3 font sizes.
- [X] T003 In `frontend/src/index.css`, add four semantic color tokens to `:root`: `--color-success`, `--color-error`, `--color-warning`, `--color-info`. Each token maps to exactly one meaning (data-model.md "Design Token" validation rules: "no two semantic tokens may share a meaning, and no semantic token may be used for pure decoration"). Check each new token against `--color-background` and `--color-surface` for WCAG 2.1 AA contrast (4.5:1 normal text / 3:1 large text, per contracts/design-tokens.md rule 4) and adjust the hex values until they pass. (Depends on T002 — same file.)
- [X] T004 In `frontend/src/index.css`, add a one-line comment directly above `:root` listing the final accent-category tokens in use (currently just `--color-primary`) and confirm the count is ≤ 3 per contracts/design-tokens.md rule 2 ("Never introduce a new accent-category token without removing/reusing an existing one, so the total stays ≤ 3"). Do not add new accent tokens in this task — only document/verify the existing one, since Navigation's active-state styling (Phase 3) is expected to reuse `--color-primary` rather than add a second accent color. (Depends on T003 — same file.)

**Checkpoint**: Design Token contract is implemented in `frontend/src/index.css`. All user stories below may now proceed (in priority order, or in parallel if staffed).

---

## Phase 3: User Story 1 - Move between areas of the app without getting lost (Priority: P1) 🎯 MVP

**Goal**: A persistent primary navigation, present on every page, that lets users reach Home, Standorte, and Räume in one interaction and shows which one is active.

**Independent Test**: Load any of the four existing routes (`/`, `/locations`, `/rooms`, `/rooms/new`), confirm the navigation bar is visible with all three items, confirm clicking any item navigates there, and confirm the current area is visually marked active (quickstart.md section 2).

### Tests for User Story 1 ⚠️

> Write this test FIRST, and confirm it fails (Navigation component does not exist yet) before writing any implementation.

- [X] T005 [P] [US1] Create `frontend/src/components/Navigation/Navigation.test.tsx` per the test contract in contracts/navigation-component.md, using the existing RTL + router test pattern from `frontend/src/pages/RoomListPage.test.tsx`. Assert: (1) links to `/`, `/locations`, and `/rooms` are all rendered with their labels ("Home", "Standorte", "Räume"); (2) when rendered with the current route set to a nested path (e.g. `/rooms/some-id`), the "Räume" item is marked active (e.g. `aria-current="page"`) and no other item is; (3) clicking the "Standorte" link navigates to `/locations`. Run the test and confirm it fails because `frontend/src/components/Navigation/Navigation.tsx` does not yet exist.

### Implementation for User Story 1

- [X] T006 [US1] Create `frontend/src/components/Navigation/Navigation.tsx`: a fixed internal list of three Navigation Items (data-model.md "Navigation Item") — `{ label: 'Home', path: '/' }`, `{ label: 'Standorte', path: '/locations' }`, `{ label: 'Räume', path: '/rooms' }` — rendered with `react-router-dom`'s `NavLink` (research.md Decision 3) so active-state is derived from the current route rather than tracked in local state. No props (contracts/navigation-component.md — `NavigationProps` is empty). (Depends on T005 existing as a failing test.)
- [X] T007 [US1] Add `frontend/src/components/Navigation/Navigation.css`: lay out the three items as a persistent horizontal top bar (research.md Decision 2 — no hamburger/drawer, no hidden items at any width). Style the active `NavLink` using the accent token `var(--color-primary)`; style inactive items using neutral tokens only (`var(--color-text)` / `var(--color-muted)`) per contracts/design-tokens.md rule 3. Add a narrow-viewport rule (e.g. `@media (max-width: 480px)`) that compresses spacing/label size but keeps all three items visible and reachable — do not collapse them behind a toggle (FR-010). Import this stylesheet from `Navigation.tsx`. (Depends on T006.)
- [X] T008 [US1] In `frontend/src/App.tsx`, import and render `<Navigation />` once, above `<Routes>`, so it appears on every page (FR-001) without being duplicated per-route. (Depends on T006.)
- [X] T009 [US1] Run `npm run test -- Navigation` in `frontend/` and confirm `Navigation.test.tsx` (T005) now passes. Run `npm run lint` and confirm no new lint errors. (Depends on T006, T007, T008.)

**Checkpoint**: User Story 1 is fully functional and independently testable — navigation works on every page with correct active-state, satisfying SC-001 and SC-006.

---

## Phase 4: User Story 2 - A calm, consistent visual experience (Priority: P2)

**Goal**: Every existing page and component draws its colors, type, spacing, and control styling from the shared Design Token contract instead of one-off/default styling, with no more than the one established accent color and no purely decorative elements.

**Independent Test**: Compare all four pages side by side; confirm shared styling rules, confirm no page introduces a color outside the token set, and confirm every non-default visual element (border, spacing, badge) ties to a specific function (quickstart.md section 3).

### Tests for User Story 2 ⚠️

- [X] T010 [P] [US2] In `frontend/src/pages/RoomListPage.test.tsx`, add an assertion that a room's status is rendered with a semantic, state-dependent class (e.g. an element with `className` containing `status-active` for `room.status === 'ACTIVE'` and `status-deactivated` otherwise), mapping to the neutral/semantic tokens from contracts/design-tokens.md. Run the test and confirm it fails against the current `RoomListPage.tsx` (which renders the raw status string with no such class).

### Implementation for User Story 2

- [X] T011 [P] [US2] Restyle `frontend/src/pages/HomePage.tsx` and any of its inline structure so headings, body text, and layout spacing use only the tokens from `frontend/src/index.css` (already largely true via global tag selectors — verify no literal colors/spacing are introduced) (FR-003, FR-008).
- [X] T025 [US2] In `frontend/src/pages/HomePage.tsx`, give the pending health-check text ("checking...") a semantic "informational/muted" class distinct from the resolved "unreachable" case, which should use the semantic error/warning class, so the loading state required by FR-009 ("form validation errors, empty states, **loading states**, and the concurrent room-update conflict warning" MUST be restyled using semantic colors) is covered. (Depends on T002–T004; feeds into T016's audit.)
- [X] T012 [US2] Restyle `frontend/src/pages/LocationCatalogPage.tsx` and `frontend/src/components/BuildingCatalog/BuildingCatalog.tsx`: apply `className`s that reference the design tokens for structure (grouping buildings/floors, form inputs, action buttons); remove reliance on unstyled default browser rendering for anything the design system defines (FR-008).
- [X] T013 [US2] Restyle `frontend/src/components/EquipmentCatalog/EquipmentCatalog.tsx` the same way as T012 (FR-008).
- [X] T014 [US2] Restyle `frontend/src/pages/RoomListPage.tsx`: apply shared tokens to the list/filter/actions layout, and add the semantic status class (`status-active` / `status-deactivated`) required by T010's test, styled in a small colocated `frontend/src/pages/RoomListPage.css` using `var(--color-success)`/`var(--color-muted)` (a deactivated room is a neutral/muted state, not an error — do not use `--color-error` here). (Depends on T010, T002–T004.)
- [X] T015 [US2] Restyle `frontend/src/pages/RoomFormPage.tsx` and `frontend/src/components/RoomForm/RoomForm.tsx`: apply shared tokens to form layout, labels, inputs, and buttons; keep the existing "new building"/"new floor" inline-form toggles but style them with tokens instead of default rendering (FR-008).
- [X] T016 [US2] Audit `frontend/src/components/Navigation/Navigation.tsx`/`.css` (Phase 3) and all files touched in T011–T015 and T025: grep for literal hex/`rgb(`/`hsl(` values in `frontend/src` component and page files and confirm none remain outside `frontend/src/index.css`; confirm the accent-token count is still ≤ 3 per contracts/design-tokens.md rules 1–2. Additionally grep the same files for `box-shadow`, `gradient`, `animation`, and `transition`; for each match, confirm it is justified by a stated function (grouping, interactivity, state) per FR-006/FR-007, or remove it. (Depends on T011–T015, T025.)
- [X] T017 [US2] Run `npm run lint && npm run test` in `frontend/` and confirm all existing tests (including `BuildingCatalog.test.tsx`, `EquipmentCatalog.test.tsx`, `RoomForm.test.tsx`, `RoomListPage.test.tsx`) and the new assertion from T010 pass after restyling. (Depends on T010–T016.)

**Checkpoint**: User Stories 1 AND 2 both work independently — the app has one consistent visual system, satisfying SC-002 and SC-003.

---

## Phase 5: User Story 3 - Understand system feedback at a glance (Priority: P3)

**Goal**: Existing validation-error and concurrent-update-conflict feedback are shown with distinct, consistent semantic styling, so they read as different states even within the restrained palette.

**Independent Test**: Submit an incomplete room form and separately trigger a concurrent-update conflict; confirm each renders with a distinct semantic style (error vs. conflict vs. success) (quickstart.md section 4).

### Tests for User Story 3 ⚠️

- [X] T018 [P] [US3] In `frontend/src/components/RoomForm/RoomForm.test.tsx`, add an assertion that the error message rendered on a failed submit (`role="alert"`) carries the semantic error class (e.g. `className` containing `feedback-error`), distinct from any success/confirmation styling. Run the test and confirm it fails against current `RoomForm.tsx` (which renders `<p role="alert">{error}</p>` with no semantic class).

### Implementation for User Story 3

- [X] T019 [US3] In `frontend/src/components/RoomForm/RoomForm.tsx`, apply a `feedback-error` class (styled via `var(--color-error)` per contracts/design-tokens.md) to the existing validation-error `<p role="alert">` element, satisfying T018. Do not change the underlying validation logic — only its presentation. (Depends on T018.)
- [X] T020 [US3] Confirm how the existing concurrent-update conflict (see `backend/src/test/java/at/mci/igp/raumlotse/RoomConcurrentUpdateIntegrationTest.java` for the scenario, and `frontend/src/API/rooms.ts`/`frontend/src/API/client.ts` for how a 409/conflict response currently surfaces to `RoomForm.tsx`) is presented to the user. If it currently reuses the same generic error path as validation errors, give it its own semantic class distinct from `feedback-error` (e.g. `feedback-conflict`, styled via `var(--color-warning)` — a conflicting-but-recoverable state, distinct from a hard validation `--color-error`) so it reads as a different state per FR-009 and User Story 3's acceptance scenario 2. (Depends on T019.)
- [X] T021 [US3] Run `npm run test -- RoomForm` in `frontend/` and confirm T018's assertion passes and no existing `RoomForm.test.tsx` assertions regressed. (Depends on T019, T020.)

**Checkpoint**: All three user stories are independently functional — navigation, consistent visual system, and distinguishable semantic feedback.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Validate the feature end-to-end against the spec's success criteria, beyond what individual story checkpoints already covered.

- [X] T022 Walk through `specs/002-design-system-navigation/quickstart.md` section 5 (responsiveness/contrast): using browser DevTools, check the app at ~375px and ~1280px widths for horizontal scrolling or hidden controls (FR-010, SC-005), and check every distinct text/background token pairing actually used across the four pages and Navigation (not a sample) against WCAG 2.1 AA using DevTools' contrast checker (FR-011, SC-004).
- [X] T023 [P] Update `frontend/src/pages/RoomListPage.test.tsx`'s or add a lightweight empty-state check: confirm the room list's empty state (no rooms for the selected status filter) renders using the design system rather than an unstyled/blank list, per spec.md Edge Cases ("What happens when a page or list has no content yet?").
- [X] T024 Run `npm run lint && npm run test && npm run build` in `frontend/` one final time to confirm the whole feature (Phases 2–5) is lint-clean, test-green, and builds successfully.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup (T001) completion — BLOCKS all user stories (T002 → T003 → T004, all in `frontend/src/index.css`, done sequentially).
- **User Stories (Phase 3–5)**: All depend on Foundational (Phase 2) completion.
  - US1 (Phase 3) has no dependency on US2 or US3.
  - US2 (Phase 4) restyles the pages Navigation (US1) also touches indirectly (T016 audits Navigation's file), so US2 is easiest to do after US1, but does not require US1's tests to pass — only the Foundational tokens.
  - US3 (Phase 5) depends only on Foundational, not on US1/US2, but naturally follows US2 since it restyles the same `RoomForm.tsx` file touched in T015.
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### Within Each User Story

- Test task(s) written and failing before the implementation task(s) that make them pass (Constitution Principle I).
- Within Phase 2, T002 → T003 → T004 are sequential (same file, `frontend/src/index.css`).
- Within Phase 4, T011/T012/T013 are parallel-safe (different files); T025 depends on T011 (same file, `HomePage.tsx`, sequential); T014 depends on T010 (its test) and the Foundational tokens; T016/T017 depend on all prior US2 tasks including T025.
- Within Phase 5, T018 → T019 → T020 → T021 are sequential (same file, `RoomForm.tsx`, and each depends on the prior step's output).

### Parallel Opportunities

- T002 exists alone at the start of Phase 2 (no other file-independent Foundational tasks to parallelize).
- T005 (Navigation test) can be written in parallel with nothing else in Phase 3 (it's the only task before T006 depends on it).
- Phase 4: T011, T012, T013 (different files: `HomePage.tsx`, `LocationCatalogPage.tsx`+`BuildingCatalog.tsx`, `EquipmentCatalog.tsx`) can run in parallel once Foundational (Phase 2) is done.
- T010 (Phase 4 test) and T018 (Phase 5 test) touch different files (`RoomListPage.test.tsx` vs. `RoomForm.test.tsx`) and can be written in parallel.
- Different user stories (Phase 3 vs. 4 vs. 5) can be worked on by different people in parallel once Phase 2 is done, keeping in mind T016 (Phase 4) reads files touched in Phase 3.

---

## Parallel Example: Phase 4 (User Story 2)

```bash
# Once Phase 2 (Foundational) is complete, launch independent restyling tasks together:
Task: "Restyle frontend/src/pages/HomePage.tsx to use only design tokens"
Task: "Restyle frontend/src/pages/LocationCatalogPage.tsx and frontend/src/components/BuildingCatalog/BuildingCatalog.tsx"
Task: "Restyle frontend/src/components/EquipmentCatalog/EquipmentCatalog.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002–T004) — CRITICAL, blocks all stories.
3. Complete Phase 3: User Story 1 (T005–T009).
4. **STOP and VALIDATE**: Run quickstart.md section 2 manually; confirm Navigation works on every existing route.
5. Demo if ready — the app now has working navigation even before the visual restyle (Phase 4) lands.

### Incremental Delivery

1. Setup + Foundational → tokens ready.
2. Add User Story 1 → validate independently → demo (MVP: navigation works).
3. Add User Story 2 → validate independently → demo (consistent visual system).
4. Add User Story 3 → validate independently → demo (clear semantic feedback).
5. Polish (Phase 6) → final cross-cutting validation against all success criteria.

### Parallel Team Strategy

1. One person completes Setup + Foundational (Phases 1–2) first — this blocks everyone else.
2. Once Foundational is done:
   - Developer A: User Story 1 (Navigation).
   - Developer B: User Story 2 (page/component restyling) — coordinate with A on `Navigation.css` token usage (T016 reads Navigation's files).
   - Developer C: User Story 3 (semantic feedback in `RoomForm`).
3. Stories complete and integrate independently; Polish (Phase 6) runs last.

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks.
- [Story] label maps each task to its user story for traceability.
- This feature makes no backend/API changes — all tasks are under `frontend/`.
- Verify each test task's test fails before writing its corresponding implementation.
- Avoid: literal colors outside `frontend/src/index.css`, a fourth accent color, decorative elements with no stated function, and collapsing the navigation behind a hidden toggle.

---

## Phase 7: Convergence

- [X] T026 Add a `.status-empty` message in `frontend/src/components/BuildingCatalog/BuildingCatalog.tsx` for the zero-buildings case and for a building whose `floors` list is empty, matching the pattern already used in `frontend/src/pages/RoomListPage.tsx` per FR-009 (partial)
- [X] T027 Add a `.status-empty` message in `frontend/src/components/EquipmentCatalog/EquipmentCatalog.tsx` for the zero-equipment-types case, matching the pattern already used in `frontend/src/pages/RoomListPage.tsx` per FR-009 (partial)
