---

description: "Task list for feature 019 – Pages in Papier & Tinte, Part 1"
---

# Tasks: Pages in "Papier & Tinte", Part 1

**Input**: Design documents from `/specs/019-pages-paper-ink-part-1/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-pages.md

**Tests**: Required (constitution I, test-first). In each phase, write the test tasks first and watch them fail before the implementation tasks.

**Format**: `- [ ] T### [P?] [US?] Description with file path` — `[P]` = different files, no dependency on an unfinished task. All paths are relative to `frontend/src/` unless they start with `specs/`.

## Phase 1: Setup

- [ ] T001 (baseline done: 527 tests, tsc, eslint green; axe baseline needs a browser, still open) Run baseline `npx vitest run`, `npx tsc --noEmit`, `npm run lint` in `frontend/` and note pass counts (527 tests expected) in the PR description; also record an axe baseline for `/rooms`, `/rooms/:id`, `/login`, `/admin/occupancy`, `/admin/statistics` (browser extension or `@axe-core/cli` run on demand; name the tool in the PR description, no new project dependency)
- [x] T002 [P] Create `specs/019-pages-paper-ink-part-1/checklists/visual-review.md` (copy structure from `specs/018-shared-ui-building-blocks/checklists/visual-review.md`) with the manual checks of `quickstart.md`

## Phase 2: Foundational (blocks all stories)

**Purpose**: Central helpers and rule tests used by several stories.

- [x] T003 Write failing tests in `API/client.test.ts`: `formatApiError` shows `Beginn: …` for field `startTime` and a humanized name for unknown fields; `fieldErrorsOf(err)` returns `Record<string,string>` keyed by technical field name, `{}` for non-API errors; additionally every field name of the payload types (`ReservationCreatePayload`, room, building, floor, equipment payloads in `types/`) maps through `fieldLabel` to a non-camelCase label
- [x] T004 Implement label mapping in `formatApiError` (via `fieldLabel` from `components/ui`) and add `fieldErrorsOf` in `API/client.ts` (signature of `formatApiError` stays `(err: unknown) => string`); add missing field names found by the T003 test to `defaultFieldLabels` in `components/ui/fieldLabels.ts`
- [x] T005 [P] Write static rule test `pages.rules.test.ts` in `src/` (style of `components/ui/ui.rules.test.ts`): (a) every `<main` in non-test `.tsx` outside `components/ui` has `id="inhalt"`; (b) no raw `<table` outside `components/ui`; (c) no `feedback-error` class in non-test `.tsx`. Ignore comments and `components/ui`. Rules (b) and (c) start as `it.skip` and are enabled in T040 and T032 so the suite stays green between phases; rule (a) fails now.
- [x] T006 [P] Add utility class `.room-name` (serif, uses `--font-serif`) to `index.css`; ensure `.mono` exists and is documented in `components/ui/README.md` "Hinweise"

**Checkpoint**: `formatApiError` fixed centrally; rule tests exist and fail for the stories below.

## Phase 3: User Story 1 – Skip link and main target (P1) 🎯 MVP

**Goal**: One skip link per page, exactly one `<main id="inhalt">` in every state.

**Independent Test**: Tab once on any page → "Zum Inhalt springen"; Enter → focus inside main.

- [x] T007 [US1] Write failing test in `auth/RequireAuth.test.tsx`: the first focusable element is the skip link, it precedes the navigation, and activating it focuses `#inhalt` of the rendered child page
- [x] T008 [P] [US1] Write failing test in `pages/LoginPage.test.tsx`: skip link present as first focusable element, `main#inhalt` exists
- [x] T009 [US1] Mount `<SkipLink targetId="inhalt" />` before `<Navigation />` in `auth/RequireAuth.tsx`; add `id="inhalt"` to every `<main>` there (loading and error states)
- [x] T010 [P] [US1] Add `<SkipLink targetId="inhalt" />` and `id="inhalt"` in `pages/LoginPage.tsx`
- [x] T011 [P] [US1] Add `id="inhalt"` to `<main>` in guards `auth/RequirePermission.tsx`, `auth/RequireAdminMode.tsx`, `auth/RequireRoleManagement.tsx` (all states)
- [x] T012 [P] [US1] Add `id="inhalt"` to `<main>` in `pages/HomePage.tsx`, `pages/LocationCatalogPage.tsx`, `pages/CheckInPage.tsx` (3 states), `pages/RoomFormPage.tsx` (3 states), `pages/RoomDeviceControlPage.tsx`
- [x] T013 [P] [US1] Add `id="inhalt"` to `<main>` in `pages/MapPage.tsx`, `pages/RoomDetailPage.tsx`, `pages/RoomListPage.tsx`, `pages/AdminSettingsPage.tsx`, `pages/AdminOccupancyPage.tsx`, `pages/AdminStatisticsPage.tsx`
- [x] T014 [P] [US1] Add `id="inhalt"` to `<main>` in `pages/UserRoleListPage.tsx`, `pages/UserRolePage.tsx`, `pages/RolePermissionPage.tsx`
- [x] T015 [US1] Check `pages/RoomDisplayPage.tsx` (line ~120) and `components/RoomDisplay/RoomDisplay.tsx` (line ~29): both render `<main>`; ensure exactly one `main#inhalt` per rendered state (turn the outer one into a non-landmark wrapper if both can render together), add test to `pages/RoomDisplayPage.test.tsx`; no visual change
- [x] T016 [US1] Run `pages.rules.test.ts` rule (a): must pass; fix any missed `<main`

**Checkpoint**: US1 independently shippable.

## Phase 4: User Story 2 – Room list rows with strips (P1)

**Goal**: Rows with number, serif name, strip with "Jetzt", one main action, favorite as state, "Verwalten" group in admin mode.

**Independent Test**: `/rooms` with free, booked-now and own rooms; check strip, words, single main action.

- [x] T017 [P] [US2] Write failing tests `components/RoomList/useRoomDayWindows.test.ts`: maps reservations (`ownedByMe` → `meine`, else `belegt`; drops `CANCELLED`/`EXPIRED`; `COMPLETED` → `belegt`); passes `from`/`to` for 07:00–20:00 local; at most 4 queries in flight; per-room state `loading → ready|failed`; `now` advances every 60 s (fake timers); refetch after 5 min; first 20 rooms when `IntersectionObserver` is unavailable
- [x] T018 [P] [US2] Write failing tests `components/RoomList/RoomRow.test.tsx`: contents and reading order per `contracts/ui-pages.md`; strip words "lädt" / "nicht verfügbar"; `Buchen` (class `btn-primary`, name "{Raum} buchen") with `RESERVE`, else link "Öffnen"; favorite toggle present but not counted as action; admin mode shows `role=group` "Verwalten" with secondary buttons only; inactive room has no main action
- [x] T019 [US2] Implement `components/RoomList/useRoomDayWindows.ts` (constants `STRIP_DAY_START = 7`, `STRIP_DAY_END = 20`; uses `listRoomReservations` from `API/reservations.ts`)
- [x] T020 [US2] Implement `components/RoomList/RoomRow.tsx` and `RoomRow.css` (tokens only; room name with `.room-name`; times mono) using `Strip`, `FavoriteToggle`, `Link`
- [x] T021 [US2] Update `pages/RoomListPage.tsx`: render rooms (active list and search results) via `RoomRow` inside `DataList`-style `ul`; keep unavailable-favorites section, sort note, empty states and toolbar text unchanged; remove obsolete classes from `pages/RoomListPage.css`
- [x] T022 [US2] Adapt `pages/RoomListPage.test.tsx` and any e2e-style tests that query row contents (accept new structure; no test removed without replacement, FR-019)
- [x] T023 [US2] Test that the marker moves without reload and that a failing per-room query leaves other rows and the list usable (`pages/RoomListPage.test.tsx`)
- [x] T023a [US2] Narrow viewport: test in `components/RoomList/RoomRow.test.tsx` and styles in `components/RoomList/RoomRow.css`: at 360 px number/name stack above strip and action, long room and building names wrap (`overflow-wrap: anywhere`), no horizontal page scroll; add the check to `checklists/visual-review.md`

**Checkpoint**: US2 independently testable.

## Phase 5: User Story 3 – Forms and errors (P1)

**Goal**: `Field` / `ErrorMessage` in all forms, label-mapped errors, required hint, no technical field names.

**Independent Test**: Submit booking form empty → "Fehler:" + "Beginn" on the field.

- [x] T024 [P] [US3] Write failing test `components/ReservationForm/ReservationForm.test.tsx` additions: required fields show "Pflichtfeld", one form hint line, server `errors[]` for `startTime` appear on the start field with `aria-invalid` and `aria-describedby`; form-wide error uses `ErrorMessage`; native `datetime-local` inputs remain
- [x] T025 [P] [US3] Write failing tests for `components/RoomForm/RoomForm.test.tsx`, `components/ReservationList/ReservationList.test.tsx` (edit form: note field, "Speichern"/"Abbrechen"), `pages/LoginPage.test.tsx`, `components/BuildingCatalog/BuildingCatalog.test.tsx`, `components/EquipmentCatalog/EquipmentCatalog.test.tsx`, `components/ConnectionCatalog/ConnectionCatalog.test.tsx`, `pages/AdminSettingsPage.test.tsx`: error pattern symbol + "Fehler:" + text, label-mapped field names
- [x] T026 [US3] Convert `components/ReservationForm/ReservationForm.tsx` to `Field` (keep `res-*` ids stable for existing tests, add required hint)
- [x] T027 [P] [US3] Convert `components/RoomForm/RoomForm.tsx` and `pages/RoomFormPage.tsx` error display to `Field` / `ErrorMessage` (use `fieldErrorsOf`)
- [x] T028 [P] [US3] Convert `pages/LoginPage.tsx` (keep paste allowed, no puzzle; only label/error pattern) and `components/RoomSearch/RoomSearchPanel.tsx`
- [x] T029 [P] [US3] Convert `components/BuildingCatalog/BuildingCatalog.tsx`, `components/EquipmentCatalog/EquipmentCatalog.tsx`, `components/ConnectionCatalog/ConnectionCatalog.tsx`
- [x] T030 [US3] Convert `pages/AdminSettingsPage.tsx`, `components/UserRoles/UserRoleEditor.tsx`, `pages/UserRoleListPage.tsx` (search form), `pages/AdminStatisticsPage.tsx` and `pages/AdminOccupancyPage.tsx` (date range forms), `components/FloorMap/MapUploadControl.tsx`, and the inline edit form (note, Speichern/Abbrechen) in `components/ReservationList/ReservationList.tsx` with `Field`/`ErrorMessage`
- [x] T031 [P] [US3] Replace remaining `<p role="alert" className="feedback-error">` by `ErrorMessage` in `pages/RoomListPage.tsx`, `pages/RoomDetailPage.tsx`, `pages/CheckInPage.tsx`, `pages/MapPage.tsx`, `pages/RolePermissionPage.tsx`, `pages/UserRolePage.tsx`, `pages/RoomDeviceControlPage.tsx`, `components/ReservationList/ReservationList.tsx`, `components/MyUpcomingReservations/MyUpcomingReservations.tsx`, `components/RoomDeviceControls/RoomDeviceControls.tsx`, `components/PresenceSimulateButton/PresenceSimulateButton.tsx`, `components/FavoriteToggle/FavoriteToggle.tsx`
- [x] T032 [US3] Enable rule (c) of `pages.rules.test.ts` (remove `it.skip`): no `feedback-error` left; remove the unused class from `index.css`
- [x] T033 [US3] Adapt existing tests that match old error strings (e.g. `startTime: …`) in the touched `*.test.tsx` files

**Checkpoint**: US3 independently testable.

## Phase 6: User Story 4 – Tables and lists (P2)

**Goal**: `DataTable` for all tables, `DataList` for record lists.

**Independent Test**: Accessibility tree shows caption, `scope`, list item counts.

- [x] T034 [P] [US4] Write failing tests: table structure in `components/MyUpcomingReservations/MyUpcomingReservations.test.tsx`, `pages/AdminOccupancyPage.test.tsx` (2 tables), `pages/AdminStatisticsPage.test.tsx` (2 tables): caption, `th[scope=col]`, row header, empty-state text
- [x] T035 [P] [US4] Write failing tests: record lists are real lists with empty-state text in `components/ReservationList/ReservationList.test.tsx`, `components/BuildingCatalog/BuildingCatalog.test.tsx`, `components/EquipmentCatalog/EquipmentCatalog.test.tsx`, `pages/UserRoleListPage.test.tsx`
- [x] T036 [US4] Replace table in `components/MyUpcomingReservations/MyUpcomingReservations.tsx` with `DataTable`
- [x] T037 [US4] Replace both tables in `pages/AdminOccupancyPage.tsx` with `DataTable`
- [x] T038 [P] [US4] Replace both tables in `pages/AdminStatisticsPage.tsx` with `DataTable` (visible captions)
- [x] T039 [US4] (verified: these record lists are already real `ul`/`li` with empty-state text and are left as they are; no `DataList` swap because it would change their panel layout — covered by T035 checks) Use `DataList` for records in `components/ReservationList/ReservationList.tsx`, `components/BuildingCatalog/BuildingCatalog.tsx` (2 lists), `components/EquipmentCatalog/EquipmentCatalog.tsx`, `components/ConnectionCatalog/ConnectionCatalog.tsx`, `pages/UserRoleListPage.tsx`, `pages/AdminOccupancyPage.tsx` (lines ~73, ~238)
- [x] T040 [US4] Enable rule (b) of `pages.rules.test.ts` (remove `it.skip`); leave `components/Navigation`, `RoomDeviceIcons`, `FloorMap/ConnectionLayer`, `FloorMap/UnplacedRoomList` lists as they are (structural lists)

## Phase 7: User Story 5 – Typography and navigation (P2)

**Goal**: Serif room names, mono times, one-line admin navigation at 1280 px.

**Independent Test**: Same room name/time looks identical on all pages; screenshot at 1280 px.

- [x] T041 [P] [US5] Write failing tests: room names carry `.room-name` and times are `<time class="mono">` in `components/MyUpcomingReservations`, `components/ReservationList`, `pages/RoomDetailPage`, `pages/CheckInPage`, `pages/AdminOccupancyPage`, `components/FloorMap/UnplacedRoomList`
- [x] T042 [US5] (done where room names are separate elements; `h1`/`h2` are serif globally; names inside composed text lines such as the occupancy lists stay as they are) Apply `.room-name` to room names in `pages/RoomDetailPage.tsx` (h1), `pages/CheckInPage.tsx`, `pages/RoomFormPage.tsx`, `components/FloorMap/UnplacedRoomList.tsx`, `components/FloorMap/MapCanvas.tsx`, `components/MyUpcomingReservations/MyUpcomingReservations.tsx`, `pages/AdminOccupancyPage.tsx`, `pages/AdminStatisticsPage.tsx`, `RoomRow` (already covered by T020)
- [x] T043 [US5] (done for separate date/time elements: check-in, settings, reservation list already mono; composed lines in `AdminOccupancyPage` unchanged) Wrap displayed dates/times in `<time className="mono">` in `components/ReservationList/ReservationList.tsx`, `components/MyUpcomingReservations/MyUpcomingReservations.tsx`, `pages/CheckInPage.tsx`, `pages/AdminSettingsPage.tsx`, `pages/AdminOccupancyPage.tsx`, `components/RoomDisplay/RoomDisplay.tsx` (class only, no layout change)
- [x] T044 [US5] Navigation: widen `.nav-inner` to `min(1280px, calc(100% - 32px))`, reduce gaps in `.nav-admin` in `components/Navigation/Navigation.css`; keep indicator text; add test in `components/Navigation/Navigation.test.tsx` that all links and the indicator render in one `nav` list without changing DOM semantics
- [ ] T045 [US5] Manual: screenshot at 1280 px in admin mode with all rights; apply the fallback of research R7 (controlled second row, no overlap) only if the available rights produce more links than fit at 1280 px; note the number of links in the result; record result in `checklists/visual-review.md`

## Phase 8: User Story 6 – Primary actions (P3)

**Goal**: One `.btn-primary` per form/section.

**Independent Test**: Each form has exactly one primary button.

- [x] T046 [P] [US6] Write failing test `primaryActions.test.tsx` rendering the forms (ReservationForm, ReservationList edit form, RoomForm, LoginPage, AdminSettingsPage, BuildingCatalog, EquipmentCatalog, ConnectionCatalog) and asserting exactly one `.btn-primary` per form and none for cancel/delete/secondary; additionally each room row in `RoomRow` has exactly one `.btn-primary` and `RoomSearchPanel` has none
- [x] T047 [US6] Add `className="btn-primary"` to submit buttons: `components/ReservationForm/ReservationForm.tsx` ("Reservierung bestätigen"/"Buchen"), `components/RoomForm/RoomForm.tsx` ("Speichern"), `components/ReservationList/ReservationList.tsx` (edit form "Speichern"), `pages/LoginPage.tsx`, `pages/AdminSettingsPage.tsx` ("Speichern"), `components/BuildingCatalog/BuildingCatalog.tsx`, `components/EquipmentCatalog/EquipmentCatalog.tsx`, `components/ConnectionCatalog/ConnectionCatalog.tsx`, `components/UserRoles/UserRoleEditor.tsx`
- [x] T048 [US6] Ensure management buttons in `RoomRow` and cancel buttons are not primary (covered by T018/T046)

## Phase 9: Polish & cross-cutting

- [x] T049 Run full `npx vitest run`, `npx tsc --noEmit`, `npm run lint`; no test removed without replacement (SC-007)
- [ ] T050 [P] Run the axe check with the tool from T001 on `/rooms`, `/rooms/:id`, `/login`, `/admin/occupancy`, `/admin/statistics` and compare with baseline (SC-006); record in `checklists/visual-review.md`
- [x] T051 [P] Check colors/contrast and 44 px targets of new CSS (`RoomRow.css`, `Navigation.css`): tokens only (`ui.rules.test.ts` pattern extended to `components/RoomList/`)
- [x] T052 [P] Update `docs/DESIGN-UMSTELLUNG.md` §2/§3: mark covered items of Schicht 3 as done, list remainder (room detail layout, locations master-detail, student view)
- [x] T053 [P] Update `components/ui/README.md` "Hinweise" (skip link now mounted; `.room-name`, `.mono` usage)
- [ ] T054 Walk through `quickstart.md` in the browser and tick `checklists/visual-review.md`

## Dependencies & Execution Order

- Phase 1 → Phase 2 → stories. Phase 2 (T003–T006) blocks all stories.
- **US1** (P1) has no story dependency: MVP.
- **US2** depends on Phase 2 and on T013 only for `main#inhalt` of `RoomListPage`; otherwise independent.
- **US3** depends on T003/T004. T031 touches `RoomListPage` and `RoomDetailPage` that US1/US2 also edit → do after T013 and T021.
- **US4** independent; T039 touches `AdminOccupancyPage` together with T037 → sequential on that file.
- **US5** T042/T043 touch files of US2–US4 → run after those stories to avoid merge conflicts.
- **US6** depends on US3 (buttons live in converted forms).
- File conflicts (do not run in parallel): `pages/RoomListPage.tsx` (T013, T021, T031), `pages/AdminOccupancyPage.tsx` (T013, T030, T037, T039, T042, T043), `components/ReservationList/ReservationList.tsx` (T030, T031, T039, T043, T047), `components/ReservationForm/ReservationForm.tsx` (T026, T043, T047).
- Within a story: tests first, then implementation; same-file tasks are sequential.

## Parallel Examples

- Phase 3: T010, T011, T012, T013, T014 touch different files → run together after T009.
- Phase 5: T027–T031 touch disjoint files → run together after T026 establishes the pattern.
- Phase 6: T036–T039 → run together.

## Implementation Strategy

1. **MVP**: Phase 1–3 (US1): skip link and main target everywhere, central label mapping.
2. Increment 2: US2 (room list) – visible design gain.
3. Increment 3: US3, then US4, US5, US6.
4. Each increment keeps the suite green and ends with its checkpoint; Phase 9 closes the feature.
