---

description: "Task list for Shared UI Building Blocks"
---

# Tasks: Shared UI Building Blocks

**Input**: Design documents from `/specs/018-shared-ui-building-blocks/`

**Prerequisites**: plan.md, spec.md, research.md (R1–R13), data-model.md, contracts/ui-blocks.md

**Tests**: REQUIRED (Constitution I, FR-016). Every test task MUST be run and seen failing before its implementation task starts.

**Organization**: Grouped by user story. All paths are under `frontend/src/components/ui/` unless stated. Run tests with `cd frontend && npm test -- src/components/ui`.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [X] T001 Create folder `frontend/src/components/ui/` with empty `index.ts` barrel and `ui.css` (shared `ui-` prefixed rules only; do NOT edit `frontend/src/index.css`, pages or `App.tsx`)
- [X] T002 [P] Write failing cross-cutting rules test `frontend/src/components/ui/ui.rules.test.ts`: (a) every `*.css` in `components/ui/` contains no `#hex`, `rgb(`, `rgba(`, `hsl(` literals (FR-011); (b) token pairs used by blocks (text/background, text/surface, background/text for inverted, primary/background, error/background, muted/surface) parsed from `frontend/src/index.css` have WCAG contrast ≥ 7:1 (FR-012, research R10); (c) `.ui-target` in `ui.css` sets `min-height: 44px` and `min-width: 44px`; (d) block CSS for room names (`.ui-strip__name`, `.ui-wayfinding__target`) uses `var(--font-serif)` and CSS for times/numbers (`.ui-strip__time`, `.ui-table__cell--mono`, `.ui-wayfinding__distance`) uses `var(--font-mono)` (FR-013)

---

## Phase 2: Foundational (blocks all stories)

- [X] T003 Write failing test `vocabulary.test.ts`: five states `frei, belegt, meine, jetzt, gesperrt`, each with non-empty `word`, `pattern` ∈ `empty|hatch|solid|line|dotted`, all patterns distinct; `windowPriority` order gesperrt > belegt > meine > frei (data-model.md)
- [X] T004 Implement `vocabulary.ts` exporting `OccupancyState`, `StateDefinition`, `stateVocabulary`, `windowPriority` per data-model.md
- [X] T005 Add shared rules to `ui.css`: `.ui-target` (44×44 min), `.ui-focus` focus ring (`outline: 2px solid var(--color-primary)`), pattern classes `.ui-pattern-empty|hatch|solid|line|dotted` using `var(--hatch)` and tokens only; make T002 pass
- [X] T006 [P] Add `.ui-sr-only` visually-hidden rule to `ui.css` (do not rely on global `.sr-only`)

**Checkpoint**: T002–T005 green (T002 passes only once the shared CSS from T005 exists).

---

## Phase 3: User Story 1 – Strip with "Jetzt" marker (P1) 🎯 MVP

**Goal**: One strip per day with patterns, words and accessible name.

**Independent Test**: Day with two bookings and now inside one → pattern, marker position, accessible name lists all windows and current state.

- [X] T007 [P] [US1] Write failing `Strip/Strip.test.tsx` covering spec scenarios: (1) free empty/booked hatched with state word in `aria-label`; (2) "Jetzt" marker at matching `left` % and in name; (3) no bookings → name contains "ganztägig frei"; (4) now outside range → no marker element and no "Jetzt" in name; (5) own = solid, blocked = dotted, with words "meine"/"gesperrt"; edge cases: adjacent equal-state windows merged, window ending at range end, zero-length window ignored, overlap resolved by priority, >12 windows → "und N weitere Zeitfenster", windows clamped to range; role is `img`
- [X] T008 [P] [US1] Write failing `Strip/StatusDot.test.tsx`: renders dot with pattern class for state and `.ui-sr-only` word; no color-only meaning
- [X] T009 [US1] Implement pure helper `Strip/stripModel.ts` (normalize, clamp, merge, priority, percent positions, spoken name builder, times as `HH:MM`) per research R1/R2
- [X] T010 [US1] Implement `Strip/Strip.tsx` and `Strip/Strip.css` (segments aria-hidden, "Jetzt" marker 2 px `var(--color-primary)` with visible label, room `name` as visible heading in `var(--font-serif)` (class `ui-strip__name`), times in `var(--font-mono)` (class `ui-strip__time`), `forced-color-adjust: none`); make T007 pass
- [X] T011 [P] [US1] Implement `Strip/StatusDot.tsx`; make T008 pass
- [X] T012 [US1] Export `Strip`, `StatusDot` from `index.ts`

**Checkpoint**: US1 independently demonstrable.

---

## Phase 4: User Story 2 – Fields and errors (P1)

**Goal**: Labeled fields, required wording, hints, German errors with field label.

**Independent Test**: Field with label, hint, required, error → bound, announced with input, "Fehler:" shown.

- [X] T013 [P] [US2] Write failing `ErrorMessage/ErrorMessage.test.tsx`: `role="alert"`, text "Fehler:" visible, symbol `aria-hidden`, optional `field` renders label via `fieldLabel`; announced when mounted after initial render (form-wide error, scenario 5)
- [X] T014 [P] [US2] Write failing `fieldLabels.test.ts`: `fieldLabel('startTime')` → "Beginn", `fieldLabel('endTime')` → "Ende", override wins, unknown `some_field` → humanized fallback (never the raw technical string unchanged for camelCase/snake_case) (scenario 6, FR-006)
- [X] T015 [P] [US2] Write failing `Field/Field.test.tsx`: label visible and click focuses input; required shows word "Pflichtfeld" + `aria-required` (not asterisk only); hint in `aria-describedby`; error sets `aria-invalid` and is described; with hint AND error both referenced, error id first; no hint/error → no `aria-describedby`/dangling ids; works with `input`, `select`, `textarea`
- [X] T016 [US2] Implement `fieldLabels.ts` (`defaultFieldLabels` seeded only with field names the backend can actually report (first inspect the request DTOs under `backend/src/main/java` and the validation error mapper; expected candidates: startTime, endTime, roomId, name, capacity, floorId, buildingId); `fieldLabel`); make T014 pass
- [X] T017 [US2] Implement `ErrorMessage/ErrorMessage.tsx` + `ErrorMessage.css` (symbol from `lucide-react`, text color `var(--color-error)`, not color-only); make T013 pass
- [X] T018 [US2] Implement `Field/Field.tsx` + `Field/Field.css` (render-prop `children(control)`, `useId`, 44 px control height via `.ui-target`); make T015 pass
- [X] T019 [US2] Export `Field`, `ErrorMessage`, `fieldLabel`, `defaultFieldLabels` from `index.ts`

---

## Phase 5: User Story 3 – Skip link (P2)

**Goal**: Standalone skip-link block with documented main-content target contract (clarification 2026-10-14; NOT wired into any page).

**Independent Test**: Test-composed frame: Tab once → link visible; activate → focus on main.

- [X] T020 [P] [US3] Write failing `SkipLink/SkipLink.test.tsx`: first Tab focuses link and it has the visible class; activation (Enter) moves `document.activeElement` to `#inhalt` (adds `tabindex="-1"` if missing); not focused → hidden by CSS class but present in accessibility tree (not `display:none`); custom `targetId`; default text "Zum Inhalt springen"
- [X] T021 [US3] Implement `SkipLink/SkipLink.tsx` and `.ui-skip-link` rules in `ui.css` (hidden off-screen until `:focus`, tokens only, `.ui-target`); make T020 pass
- [X] T022 [US3] Add JSDoc on `SkipLink` stating the page contract (first focusable element; `<main id="inhalt">`); export from `index.ts`

---

## Phase 6: User Story 4 – Data list and table (P2)

**Goal**: Real lists/tables with captions, header scopes, worded empty state.

**Independent Test**: List item count, table caption/scope/row header, empty states.

- [X] T023 [P] [US4] Write failing `DataList/DataList.test.tsx`: `role=list` with N `listitem`s; `renderItem` output inside `li`; empty → worded `emptyText` and no empty `ul`; optional `aria-label`
- [X] T024 [P] [US4] Write failing `DataTable/DataTable.test.tsx`: `caption` present; column headers `scope="col"`; `rowHeader` column cells are `th scope="row"` (found by `getByRole('rowheader')`); empty → `emptyText` shown, no blank row box; wrapper has `role="region"`, accessible name from caption, `tabIndex=0`; long cell content keeps full text in DOM
- [X] T025 [US4] Implement `DataList/DataList.tsx` + `DataList.css` (long text wraps, `overflow-wrap: anywhere`); make T023 pass
- [X] T026 [US4] Implement `DataTable/DataTable.tsx` + `DataTable.css` (wrapper `overflow-x: auto`, hairline borders via `var(--color-hairline)`, mono for numeric/time columns via `column.mono`); make T024 pass
- [X] T027 [US4] Export `DataList`, `DataTable` from `index.ts`

---

## Phase 7: User Story 5 – Chip and wayfinding (P3)

**Goal**: Uniform status vocabulary and direction hints.

**Independent Test**: Each chip variant has word + pattern; wayfinding text carries direction and target.

- [X] T028 [P] [US5] Write failing `Chip/Chip.test.tsx`: each vocabulary state renders its word and distinct pattern class; `neutral` requires text; custom text overrides word; static → `span`, with `onClick` → `button` with `.ui-target` and focus class; no empty gap when no icon
- [X] T029 [P] [US5] Write failing `Wayfinding/Wayfinding.test.tsx`: text contains target and direction word (e.g. "Aufzug · hoch"), distance appended when given, icon `aria-hidden`; `href` → link, `onClick` → button, both with `.ui-target`; plain when neither
- [X] T030 [US5] Implement `Chip/Chip.tsx` + `Chip.css`; make T028 pass
- [X] T031 [US5] Implement `Wayfinding/Wayfinding.tsx` + `Wayfinding.css` (lucide arrow icons decorative); make T029 pass
- [X] T032 [US5] Export `Chip`, `Wayfinding` from `index.ts`

---

## Phase 8: Polish & cross-cutting

> SC-001, SC-004 and FR-017 (plus the forced-colors edge case) are verified manually in T036; their automated parts are static CSS checks only.

- [X] T033 Write `Reference.test.tsx` composing one reference page from the blocks only (SkipLink, Strip, Field+ErrorMessage, DataList, DataTable, Chip, Wayfinding) without any additional CSS class outside `ui-`; assert axe-style basics via roles (SC-006, FR-014)
- [X] T033a [P] Add `frontend/src/components/ui/README.md`: per block, one short usage example (copied from the block's test render), its props table from `contracts/ui-blocks.md`, and the a11y invariants; link it from each block's JSDoc (FR-014)
- [X] T034 [P] Add 200 %/phone-width guard to `ui.rules.test.ts`: block CSS has no fixed `width` > 100 % and no `white-space: nowrap` on text containers (FR-017)
- [X] T035 [P] Verify FR-015/SC-005: `git diff --stat main -- frontend/src/pages frontend/src/App.tsx frontend/src/index.css` is empty; run full `npm test`, `npm run lint`, `npm run build` in `frontend/`
- [ ] T036 Manual visual review per `quickstart.md` §3–4 (grayscale, keyboard, 200 % zoom, forced-colors emulation, screen reader); record results in `specs/018-shared-ui-building-blocks/checklists/visual-review.md` and mention them in the PR description
- [X] T037 Update `docs/DESIGN-UMSTELLUNG.md` §2: set layer 2 status to "erledigt"; remove SkipLink-wiring from "offen" only after pages adopt it (stays in §3)

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → stories. Phase 2 (vocabulary, shared CSS, rules test) blocks all stories.
- US1, US2, US3, US4 are independent of each other after Phase 2; US5 (Chip) uses the vocabulary only, so also independent.
- `index.ts` edits (T012, T019, T022, T027, T032) touch one file: do them sequentially or merge at the end.
- Within a story: test task(s) → confirm failing → implementation.
- Polish after all stories.

## Parallel Example

After Phase 2: T007, T008, T013, T014, T015, T020, T023, T024, T028, T029 all write distinct test files and can be authored in parallel; implementations then follow per story.

## Implementation Strategy

- **MVP**: Phases 1–3 (Strip + foundations), validated by `Strip.test.tsx`.
- **Increment 2**: US2 (fields/errors) – completes the two P1 stories.
- **Increment 3**: US3, US4 (P2), then US5 (P3), then Polish.
- Commit test and implementation separately per block so SC-007 (tests fail before) is visible in history.
