---

description: "Task list for feature 020 – Pages in Papier & Tinte, Part 2"
---

# Tasks: Pages in "Papier & Tinte", Part 2

**Input**: Design documents from `/specs/020-pages-paper-ink-part-2/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ui-pages.md

**Tests**: Required (constitution I, test-first). In each phase, write the test tasks first and watch them fail before the implementation tasks.

**Format**: `- [ ] T### [P?] [US?] Description with file path` — `[P]` = different files, no dependency on an unfinished task. All paths are relative to `frontend/src/` unless they start with `specs/` or `docs/`.

## Phase 1: Setup

- [x] T001 Run baseline `npx vitest run`, `npx tsc --noEmit`, `npm run lint` in `frontend/`; note the pass count for the PR description
- [x] T002 [P] Add devDependency `axe-core` in `frontend/package.json` (`npm install -D axe-core`, pin the version, commit the lockfile); justification for the PR: test-only engine for the accessibility suite (research R9, constitution V)
- [x] T003 [P] Create `specs/020-pages-paper-ink-part-2/checklists/visual-review.md` (structure from `specs/019-pages-paper-ink-part-1/checklists/visual-review.md`) with the browser checks of `quickstart.md` (room detail 1280/360 px, composed lines, lists, footer/help, navigation at 1280 px, `prefers-reduced-motion`)

## Phase 2: Foundational (blocks all stories)

**Purpose**: Small shared pieces and rule tests used by several stories.

- [x] T004 [P] Write failing tests `components/ui/Typography/Typography.test.tsx`: `RoomName` renders `<span class="room-name">` with the text, `TimeText` renders `<span class="mono">`; both pass `children` unchanged and add no wrapper text
- [x] T005 Implement `RoomName` and `TimeText` in `components/ui/Typography/Typography.tsx`, export from `components/ui/index.ts`, add a row to `components/ui/README.md`
- [x] T006 [P] Add rule tests to `pages.rules.test.ts`, initially `it.skip` and enabled in the story that fixes them: (a) no `<ul`/`<ol` with class `list-plain` in `components/*Catalog` and `pages/UserRoleListPage.tsx` (enabled in T026); (b) no CSS rule with `text-transform: uppercase` together with `letter-spacing` (enabled in T034); (c) every non-test `.tsx` with a `<form` and a `Pflichtfeld`-marked `Field required` also renders `form-required-hint` (enabled in T033)

**Checkpoint**: Typography blocks and rule tests exist.

## Phase 3: User Story 1 – Room detail in the "Lektorat" layout (P1) 🎯 MVP

**Goal**: Mine / day occupancy / room overview left, booking form right; same data, no new request.

**Independent Test**: Open a room as lecturer with own and foreign bookings today; check section order, form beside at 1280 px, stacked order at 360 px, "keine Angabe" for missing data.

- [x] T007 [P] [US1] Write failing tests `components/RoomOverview/RoomOverview.test.tsx`: labeled `dl` "Sitzordnungen" (`{name} (max {maxCapacity})`), "Ausstattung" (names from the `equipmentNames` prop, unknown id shown as id), "Zugang" with "Barrierefrei erreichbar: Ja/Nein", "Aufzug im Gebäude: Ja/Nein", "Ebene ohne Stufen: Ja/Nein"; empty seating/equipment read "keine Angabe"
- [x] T008 [P] [US1] Write failing tests `components/RoomDayOccupancy/RoomDayOccupancy.test.tsx`: from a `Reservation[]` shows only windows of today; `Strip` for the day; `DataList` with time range in `.mono`, state word (`belegt`, `meine`), "meine" chip for `ownedByMe`; cancelled/expired dropped; empty text "Heute keine Belegungen."; redacted reservations show only time and "Belegt"
- [x] T009 [P] [US1] Write failing tests in `components/ReservationList/ReservationList.test.tsx`: new prop `scope="mine"` lists only `ownedByMe` with empty text "Sie haben in diesem Raum keine Buchungen."; `scope="managed"` lists only not-own reservations, rendered only when the user has `OTHER_RESERVATION_MANAGE`; existing actions (edit, cancel, activate, complete, expire, details) unchanged
- [x] T010 [US1] Write failing tests in `pages/RoomDetailPage.test.tsx`: section headings in order "Meine Buchungen", "Belegungen des Tages", "Raum auf einen Blick"; booking form visible without a toggle when the room is active and the user has `RESERVE`; no form without `RESERVE` or for inactive rooms while all sections remain; query parameters `start`/`end`/`book` prefill the form; "Weitere Reservierungen" only in administration mode with `OTHER_RESERVATION_MANAGE`; "Check-in-Codes" after the overview; exactly one `main#inhalt` in loading/error states; only one `listRoomReservations` call per load
- [x] T011 [P] [US1] Implement `components/RoomOverview/RoomOverview.tsx` (+ `.css`, tokens only, `dl` with `dt`/`dd`, data per `data-model.md` RoomOverview)
- [x] T012 [P] [US1] Implement `components/RoomDayOccupancy/RoomDayOccupancy.tsx` (+ `.css`) using `Strip` (07:00–20:00 as in 019 `STRIP_DAY_START/END`), `DataList`, `Chip`, `TimeText`
- [x] T013 [US1] Add `scope` prop (`'mine' | 'managed'`, default keeps today's behavior for other callers) and make it accept an optional `reservations` source so the page loads once, in `components/ReservationList/ReservationList.tsx`
- [x] T014 [US1] Rebuild `pages/RoomDetailPage.tsx`: header (serif name via `RoomName`, favorite, links) + grid with areas `mine`, `form`, `day`, `overview`, then admin-only "Weitere Reservierungen" and "Check-in-Codes"; remove the toggle and "Buchungsformular schließen"; resolve equipment names with `listEquipmentTypes('all')` once; keep print rule for check-in codes
- [x] T015 [US1] Implement grid CSS in `pages/RoomDetailPage.css`: `minmax(0,1fr) minmax(20rem,26rem)` at ≥ 64rem, single column order mine → form → day → overview below; no fixed colors; keep `@media print` rule working with the new structure
- [x] T016 [US1] Run `RoomDetailPage` tests, fix adaptations (texts/roles of the removed toggle), confirm no test removed without replacement

**Checkpoint**: US1 independently shippable.

## Phase 4: User Story 2 – Serif and mono in composed lines (P2)

**Goal**: Room names serif, times and numbers mono inside the occupancy lines, text unchanged.

**Independent Test**: Occupancy page: inspect lines; screen-reader text identical to before.

- [x] T017 [P] [US2] Write failing tests in `pages/AdminOccupancyPage.test.tsx`: in a booking line the room name is inside `.room-name` and each time range inside `.mono`; `textContent` of the line equals the previous string (`{Raum} · {HH:MM}–{HH:MM} · {Personen} · {Status}`)
- [x] T018 [US2] Replace `bookingLine`/`runningLine` string builders in `pages/AdminOccupancyPage.tsx` with fragments using `RoomName` and `TimeText`; group headings `{Haus} · {Stockwerk} – {n}` keep text, numbers in `.mono`
- [x] T019 [P] [US2] Grep other composed lines (`ReservationList` header, `MyUpcomingReservations`, `AdminStatisticsPage`, `FavoriteToggle` labels excluded as aria-text) and apply `RoomName`/`TimeText` where a name or time sits inside a text string; add one test per touched file

**Checkpoint**: US2 independently shippable.

## Phase 5: User Story 3 – Lists on `DataList` (P2)

**Goal**: Catalog, role and occupancy lists share one list block with accessible name and empty text; behavior unchanged.

**Independent Test**: Each list with and without records; actions and confirmations as before.

- [x] T020 [P] [US3] Write failing tests in `components/BuildingCatalog/BuildingCatalog.test.tsx` for both lists: `list` role with accessible name ("Gebäude", "Stockwerke von {Gebäude}"), `listitem` per record, empty text; existing action tests untouched
- [x] T021 [P] [US3] Write failing tests in `components/EquipmentCatalog/EquipmentCatalog.test.tsx` and `components/ConnectionCatalog/ConnectionCatalog.test.tsx`: `list` with accessible name, empty text
- [x] T022 [P] [US3] Write failing tests in `pages/UserRoleListPage.test.tsx` (role users list) and `pages/AdminOccupancyPage.test.tsx` (running bookings list, hour detail lists): `list` with accessible name, empty text
- [x] T023 [US3] Convert `components/BuildingCatalog/BuildingCatalog.tsx` (lines ~124 and ~209) to `DataList`, keep buttons, confirmation and layout classes; adapt `BuildingCatalog.css` if panel spacing changes
- [x] T024 [P] [US3] Convert `components/EquipmentCatalog/EquipmentCatalog.tsx` and `components/ConnectionCatalog/ConnectionCatalog.tsx` to `DataList`
- [x] T025 [P] [US3] Convert the `role-users` list in `pages/UserRoleListPage.tsx` and the `<ul>` lists in `pages/AdminOccupancyPage.tsx` (lines ~74, ~243) to `DataList`; leave `Navigation`, `UnplacedRoomList`, `RoomDeviceIcons`, `ConnectionLayer` as they are (research R6)
- [x] T026 [US3] Enable rule (a) of T006 in `pages.rules.test.ts` and make it pass

**Checkpoint**: US3 independently shippable.

## Phase 6: User Story 4 – Footer, help page and form/label rules (P2)

**Goal**: Review items B1, C2 and required-field hint closed.

**Independent Test**: Footer on every page incl. login; `/hilfe` by keyboard; required hint in every form; no strong-letter-spaced capitals.

- [ ] T027 [P] [US4] Write failing tests `components/Footer/Footer.test.tsx`: `<footer>` with link "Hilfe" to `/hilfe`; and in `auth/RequireAuth.test.tsx` and `pages/LoginPage.test.tsx`: footer follows `main`, same text on both
- [ ] T028 [P] [US4] Write failing tests `pages/HelpPage.test.tsx`: one `main#inhalt`, `h1` "Hilfe", skip link first, sections (Raum finden und buchen, Bedeutung der Streifen/Muster, Kontakt) from `helpContent.ts`, contact address shown from `HELP_CONTACT`; and in `App.test.tsx`: `/hilfe` is reachable without sign-in
- [ ] T029 [US4] Implement `components/Footer/Footer.tsx` (+ `.css`), mount in `auth/RequireAuth.tsx` after `<Outlet />` and in `pages/LoginPage.tsx`
- [ ] T030 [US4] Implement `pages/HelpPage.tsx` and `pages/helpContent.ts` (fixed German text; `HELP_CONTACT` placeholder constant with a comment that the project sets it); add public route `/hilfe` in `App.tsx` outside `RequireAuth` with `SkipLink` and `main#inhalt`
- [ ] T031 [P] [US4] Write failing tests for the remaining forms that lack the required hint (list from rule (c) of T006: check `components/RoomSearch/RoomSearchPanel.tsx`, `components/UserRoles/UserRoleEditor.tsx`, catalog forms, `components/FloorMap/MapUploadControl.tsx`, `pages/AdminStatisticsPage.tsx`, `pages/UserRoleListPage.tsx`); only forms with at least one required field need the hint
- [ ] T032 [US4] Add `form-required-hint` to the forms found in T031
- [ ] T033 [US4] Enable rule (c) of T006 and make it pass
- [ ] T034 [US4] Remove `text-transform: uppercase` and `letter-spacing` from `.status-badge` in `components/ReservationList/ReservationList.css`; enable rule (b) of T006

**Checkpoint**: US4 independently shippable.

## Phase 7: User Story 5 – Accessibility run and browser verification (P3)

**Goal**: Automated structure checks plus recorded browser checks.

**Independent Test**: `npx vitest run a11y.test.tsx` green; visual-review checklist ticked.

- [ ] T035 [US5] Write `a11y.test.tsx` in `src/`: render each page in scope with mocked API (home, room list, room detail, check-in, login, help, admin occupancy, statistics, locations, role pages) and run `axe-core` with `color-contrast` disabled (jsdom); assert zero violations for label, list, listitem, table/th, landmark and region rules
- [ ] T036 [US5] Fix all violations found by T035 in the affected components (no rule suppression without an inline justification)
- [ ] T037 [US5] Verify `prefers-reduced-motion`: add a rule test that every CSS `transition`/`animation` outside the media query block is covered by the reduced-motion override in `index.css`, and fix gaps
- [ ] T038 [US5] Browser pass with `docs`-listed checks: navigation in administration mode at 1280 px (record result: one line or controlled wrap), room detail at 1280/360 px, contrast spot checks, Windows high-contrast note if available; tick `specs/020-pages-paper-ink-part-2/checklists/visual-review.md`

## Phase 8: Polish & Cross-Cutting

- [ ] T039 Update `docs/DESIGN-UMSTELLUNG.md`: Schicht 3 Teil 2 erledigt, move done items out of Abschnitt 3, mark B1 and C2 done in Abschnitt 5, record navigation finding, list remaining open points (Standorte-Master-Detail, Studierendenansicht, Raumnummer als Feld)
- [ ] T040 [P] Update `frontend/src/components/ui/README.md` (Typography blocks, `DataList` adoption) and `specs/020-pages-paper-ink-part-2/quickstart.md` if steps changed
- [ ] T041 Final run `npx vitest run`, `npx tsc --noEmit`, `npm run lint`; compare pass count with T001; no test removed without replacement (SC-007)

## Dependencies & Execution Order

- Phase 1 → Phase 2 → stories. US1 is the MVP; US2, US3, US4 are independent of each other and of US1 (US3 touches `AdminOccupancyPage.tsx` like US2: do US2 first or merge carefully).
- US5 runs after US1–US4 (it checks them).
- Within a story: tests first (fail), then implementation.

## Parallel Examples

- Phase 2: T004 ∥ T006.
- US1: T007 ∥ T008 ∥ T009, then T011 ∥ T012.
- US3: T020 ∥ T021 ∥ T022, then T024 ∥ T025.
- US4: T027 ∥ T028 ∥ T031.

## Implementation Strategy

1. MVP: Phases 1–3 (room detail layout). Ship.
2. Then US2, US3, US4 in any order (suggest US2 → US3 → US4 because of the shared file).
3. US5 and polish last; update docs in the same PR.
