---

description: "Task list for feature 016 – Admin-Belegungsübersicht"
---

# Tasks: Admin-Belegungsübersicht

**Input**: Design documents from `/specs/016-admin-occupancy-overview/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/admin-occupancy.yaml, quickstart.md

**Tests**: Required. Constitution Principle I (Test-First, non-negotiable): every test task must be written and seen failing before the implementation task that follows it.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1–US4)

## Path Conventions

- `B` = `backend/src/main/java/at/mci/igp/raumlotse`
- `T` = `backend/src/test/java/at/mci/igp/raumlotse`
- `F` = `frontend/src`
- Reference implementation to copy patterns from: feature 011 (`B/controller/AdminStatisticsController.java`, `B/service/AdminStatisticsService.java`, `B/repository/StatisticsRepository.java`, `F/pages/AdminStatisticsPage.tsx` and their tests).

## Shared rules (quoted from data-model.md – apply in every task)

- **Effective start** = `coalesce(checkedInAt, startTime)` for `ACTIVE`, otherwise `startTime`.
- **Covers now**: `now < endTime` and effective start `<= now`, with status `RESERVED` or `ACTIVE` (same as `ReservationRepository.findCovering`).
- **Checked-in persons** = Σ `expectedAttendees` of covering `ACTIVE` bookings. **Occupied rooms** = |distinct room ids| of covering `ACTIVE` bookings.
- **Hour value**: bookings with status `RESERVED` or `ACTIVE` whose `[effective start, endTime)` overlaps `[hourStart, hourEnd)`. `expectedPersons` = max simultaneous Σ `expectedAttendees` within the hour; `bookedRooms` = |distinct room ids|. The same rule is applied per building and per floor.
- **Peak** = all non-past hours whose `expectedPersons` equals the day's maximum, provided the maximum is > 0; evaluated separately for the total and for each building.
- Time zone: `AdminStatisticsPeriod.ZONE` (`Europe/Berlin`). Time source: injected `java.time.Clock` (bean in `B/config/ClockConfig.java`).
- Responses MUST NOT contain `reservedFor`, `createdBy`, `createdByUserId`, `note` or e-mail addresses (FR-008).

---

## Phase 1: Setup

**Purpose**: Confirm a green baseline before changing anything.

- [X] T001 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint` on branch `016-admin-occupancy-overview`, and note any failures that already exist so they are not attributed to this feature

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Response types, data access and access control used by every story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Create the response DTOs as nested Java records in `B/dto/AdminOccupancyResponse.java`, exactly matching `specs/016-admin-occupancy-overview/contracts/admin-occupancy.yaml`: `Snapshot(Instant generatedAt, UUID buildingId, Totals totals, List<BuildingOccupancy> buildings, List<BookingEntry> runningBookings)`, `Totals(int checkedInPersons, int occupiedRooms)`, `BuildingOccupancy(UUID buildingId, String buildingName, boolean buildingActive, int checkedInPersons, int occupiedRooms)`, `BookingEntry(UUID reservationId, UUID roomId, String roomName, UUID buildingId, String buildingName, UUID floorId, String floorName, Instant startTime, Instant endTime, int expectedAttendees, CheckInStatus checkInStatus, Instant checkedInAt)` with `enum CheckInStatus { CHECKED_IN, NOT_CHECKED_IN }`, `Forecast(LocalDate date, String zone, Instant generatedAt, UUID buildingId, Integer peakExpectedPersons, List<ForecastBuilding> buildings, List<HourForecast> hours)`, `ForecastBuilding(UUID buildingId, String buildingName, boolean buildingActive, Integer peakExpectedPersons)`, `HourForecast(Instant start, Instant end, String localLabel, boolean past, Integer expectedPersons, Integer bookedRooms, boolean peak, List<HourBuildingValue> buildings, List<FloorGroup> floors)`, `HourBuildingValue(UUID buildingId, Integer expectedPersons, Integer bookedRooms, boolean peak)`, `FloorGroup(UUID buildingId, String buildingName, UUID floorId, String floorName, int expectedPersons, List<BookingEntry> bookings)`
- [X] T003 [P] Create matching TypeScript types in `F/types/adminOccupancy.ts` (`OccupancySnapshot`, `BuildingOccupancy`, `BookingEntry`, `CheckInStatus = 'CHECKED_IN' | 'NOT_CHECKED_IN'`, `OccupancyForecast`, `ForecastBuilding`, `HourForecast`, `HourBuildingValue`, `FloorGroup`); nullable contract fields (`type: [x, 'null']`) become `T | null`; instants and dates are `string`
- [X] T004 [P] Create `F/API/adminOccupancy.ts` following `F/API/adminStatistics.ts`: `getOccupancySnapshot(buildingId?: string): Promise<OccupancySnapshot>` → `GET /api/admin/occupancy[?buildingId=]` and `getOccupancyForecast(date: string, buildingId?: string): Promise<OccupancyForecast>` → `GET /api/admin/occupancy/forecast?date=YYYY-MM-DD[&buildingId=]`, both via `apiRequest` from `F/API/client.ts`
- [X] T005 Write the failing integration test `T/repository/OccupancyRepositoryIntegrationTest.java` extending `AbstractIntegrationTest` (create data through `BuildingService`, `FloorService`, `RoomService`, `ReservationService` as in `T/repository/ReservationOccupancyIntegrationTest.java`). Cover: `findCovering(now, null)` returns a `RESERVED` booking with `startTime <= now < endTime`, an `ACTIVE` booking checked in early (`checkedInAt <= now < startTime`), and excludes an `ACTIVE` booking with `endTime <= now` and any `CANCELLED`/`EXPIRED`/`COMPLETED` booking; `findCovering(now, buildingId)` returns only that building's bookings; `findOverlapping(from, to, buildingId)` uses half-open overlap on `[effective start, endTime)` and returns only `RESERVED`/`ACTIVE`; `findBuildings()` returns all buildings with status; `buildingExists(id)` is true/false correctly; row fields are populated with room, floor and building names and ids
- [X] T006 Implement `B/repository/OccupancyRepository.java` as a `@Repository` class using `EntityManager` with JPQL and bound parameters (pattern of `B/repository/StatisticsRepository.java`; methods non-final so tests can override them). Records: `BookingRow(UUID reservationId, UUID roomId, String roomName, UUID buildingId, String buildingName, EntityStatus buildingStatus, UUID floorId, String floorName, Instant startTime, Instant endTime, Instant effectiveStart, ReservationStatus status, int expectedAttendees, Instant checkedInAt)` and `BuildingRow(UUID id, String name, EntityStatus status)`. Methods: `List<BookingRow> findCovering(Instant now, UUID buildingIdOrNull)`, `List<BookingRow> findOverlapping(Instant from, Instant to, UUID buildingIdOrNull)`, `List<BuildingRow> findBuildings()` (ordered by name), `boolean buildingExists(UUID id)`. Join `r.room.floor.building` with fetch joins so no lazy loading happens outside the transaction. Make T005 pass
- [X] T007 [P] Add failing assertions for the new routes: in `T/service/AuthorizationMatrixTest.java` assert `RoleAccessFilter.classify("GET", "/api/admin/occupancy")` and `classify("GET", "/api/admin/occupancy/forecast")` return permission `STATISTICS_READ`; in `T/security/ProtectedBusinessRouteTest.java` assert both routes return 401 without a session and 403 for a user without `STATISTICS_READ` (follow the existing `/api/admin/statistics` cases) — *done instead in `T/ManagementPermissionIntegrationTest.java`, where the existing `/api/admin/statistics` cases live (403 without the right, 2xx with it, admin mode on and off)*
- [X] T008 Add `add(result, "GET", "/api/admin/occupancy(?:/forecast)?", PermissionCode.STATISTICS_READ);` next to the statistics rule in `B/service/RoleAccessFilter.java`. Make T007 pass

**Checkpoint**: Types, client, repository and access rule are ready; stories can start.

---

## Phase 3: User Story 1 – Aktuelle Gesamtsituation auf einen Blick (Priority: P1) 🎯 MVP

**Goal**: An admin opens "Belegung" and sees checked-in persons and occupied rooms, refreshed every 30 s, with the data timestamp.

**Independent Test**: Two `ACTIVE` bookings (4 and 10 attendees, two rooms) and one `RESERVED` booking → page shows "14" checked-in persons and "2" occupied rooms; a user without `STATISTICS_READ` gets no data.

### Tests for User Story 1 ⚠️ write first, must fail

- [X] T009 [P] [US1] Write `T/service/AdminOccupancyServiceTest.java` (plain unit test, anonymous subclass of `OccupancyRepository` as in `T/service/AdminStatisticsServiceTest.java`, `Clock.fixed(...)`): `snapshot(null)` returns `totals.checkedInPersons` = Σ attendees of `ACTIVE` rows only (`RESERVED` rows ignored); `occupiedRooms` counts a room with two `ACTIVE` rows once while both rows' persons are summed; `generatedAt` equals the clock instant; with no rows the totals are 0/0; `snapshot(unknownId)` throws `NotFoundException` when `buildingExists` is false
- [X] T010 [P] [US1] Write `T/controller/AdminOccupancyControllerTest.java` (standalone `MockMvc` with `GlobalExceptionHandler`, as in `T/controller/AdminStatisticsControllerTest.java`): `GET /api/admin/occupancy` → 200 with header `Cache-Control: no-store` and JSON fields `generatedAt`, `totals.checkedInPersons`, `totals.occupiedRooms`; `?buildingId=not-a-uuid` → 400; unknown building → 404
- [X] T011 [P] [US1] Write `F/pages/AdminOccupancyPage.test.tsx` (mock `../API/adminOccupancy` and `../auth/useCurrentRoles` as in `F/pages/AdminStatisticsPage.test.tsx`): without `STATISTICS_READ` the route shows the "nicht verfügbar" alert and the API is not called; with the right it shows "14" Personen and "2" Räume and the formatted `generatedAt`; with 0/0 it shows the empty-state hint that no room is in use; with fake timers a second call happens after 30 s and new values appear; when that call rejects with a network or 5xx error, the old values and timestamp stay and a notice that the data may be stale is shown; when it rejects with 401 or 403, all occupancy data disappears and an alert says access is no longer allowed
- [X] T012 [P] [US1] Add a case to `F/App.protected-routes.test.tsx` and `F/components/Navigation/Navigation.test.tsx`: `/admin/occupancy` is reachable and the nav entry "Belegung" is visible only with `STATISTICS_READ` (and admin mode on, like "Statistiken")

### Implementation for User Story 1

- [X] T013 [US1] Create `B/service/AdminOccupancyService.java` (`@Service`, `@Transactional(readOnly = true)`, constructor-injected `OccupancyRepository` and `Clock`) with `AdminOccupancyResponse.Snapshot snapshot(UUID buildingIdOrNull)`: validate building (`NotFoundException("Gebäude " + id + " nicht gefunden.")`), load `findCovering(clock.instant(), buildingId)`, compute totals from `ACTIVE` rows; return `buildings` and `runningBookings` as empty lists for now; they are filled in T027 (US3) and T031 (US4). **Intermediate state: until T031 is done the response does not meet the contract rule "sum of `buildings` = totals", so US1 alone must not be merged to `dev` without US3 and US4, or must be merged with this noted as a known gap in the PR.** Make T009 pass
- [X] T014 [US1] Create `B/controller/AdminOccupancyController.java` with `@GetMapping("/api/admin/occupancy")` taking `@RequestParam(required = false) UUID buildingId` and returning `ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(...)`. Make T010 pass
- [X] T015 [US1] Create `F/pages/AdminOccupancyPage.tsx` and `F/pages/AdminOccupancyPage.css`: section "Aktuelle Situation" with two KPI tiles ("Eingecheckte Personen" with the subtitle "erwartete Teilnehmende eingecheckter Buchungen", "Belegte Räume"), "Stand: <time>" from `generatedAt`, empty-state hint when both are 0, `window.setInterval` reload every 30 000 ms cleared on unmount (pattern of `F/pages/RoomDisplayPage.tsx`), on a failed reload: for 401/403 (status from the error thrown by `apiRequest` in `F/API/client.ts`) clear the data and show an access-denied alert, otherwise keep the previous data and show a stale-data notice (`role="status"`), error alert via `formatApiError` on the first failed load. Use existing design tokens from `F/index.css`. Make T011 pass
- [X] T016 [US1] Register the route `occupancy` → `<AdminOccupancyPage />` inside the `RequirePermission any={['STATISTICS_READ']}` group under `/admin` in `F/App.tsx`, and add `{ label: 'Belegung', path: '/admin/occupancy', icon: Users, rights: ['STATISTICS_READ'] }` before "Statistiken" in `F/components/Navigation/Navigation.tsx`. Make T012 pass

**Checkpoint**: US1 works end to end (quickstart scenarios 1, 2, 7, 8).

---

## Phase 4: User Story 2 – Tagesprognose je Stunde und Gebäude (Priority: P2)

**Goal**: An admin picks today or a later day and sees, per hour, expected persons per building and in total (reserved and checked-in bookings count fully), with peaks, and a floor-grouped booking list per hour.

**Independent Test**: Quickstart scenario 4 and 5a: 10:00 → A 17, B 7, total 24 / 3 rooms (peak); hour 10:00 lists groups "A · 1. OG – 12", "B · EG – 7", "A · EG – 5".

### Tests for User Story 2 ⚠️ write first, must fail

- [X] T017 [P] [US2] Add forecast cases to `T/service/AdminOccupancyServiceTest.java` (fixed clock on a day before the requested date unless stated): hours are 07:00–21:00 local plus any other hour with bookings, ascending, `localLabel` `HH:mm`; a `RESERVED` and an `ACTIVE` row both count fully; back-to-back rows in one room 10:00–10:30 (8) and 10:30–11:00 (6) give hour 10:00 `expectedPersons` 8 and `bookedRooms` 1; a booking ending exactly at 11:00 is not in hour 11:00; per-building values and peaks (quickstart scenario 4 data: 10:00 total 24 peak, A 17 peak at 10:00 and 11:00, B 7 peak at 10:00); `peakExpectedPersons` is null and no hour is peak when all values are 0; floor groups sorted by `expectedPersons` desc then building name, floor name, bookings inside sorted by attendees desc then room name; with the clock at today 12:30, hours ending `<= 12:30` have `past=true`, `expectedPersons`/`bookedRooms` = null, `peak=false`, one `buildings` entry per `ForecastBuilding` with `expectedPersons`/`bookedRooms` = null and `peak=false`, and an empty `floors` list; a date before today throws `IllegalArgumentException` whose message mentions "Statistiken"; `2026-10-25` (DST end) yields correct UTC instants for 07:00–22:00 local; `ForecastBuilding` list contains active buildings with zeros and inactive buildings only if they have bookings that day; with `buildingId` only that building appears and the totals equal its values
- [X] T018 [P] [US2] Add forecast cases to `T/controller/AdminOccupancyControllerTest.java`: `GET /api/admin/occupancy/forecast?date=<tomorrow>` → 200 with `Cache-Control: no-store`; missing `date` → 400; `date=yesterday` → 400 Problem whose detail mentions "Statistiken"; `date=2026-13-01` → 400
- [X] T019 [P] [US2] Add forecast cases to `F/pages/AdminOccupancyPage.test.tsx`: the date input has `min` = today; choosing a date calls `getOccupancyForecast` with that date; the table renders one row per hour with one column per `ForecastBuilding` plus "Gesamt"; peak cells are marked (accessible text "Spitze"); values are labelled as "geplant"; past hours show "vergangen"; clicking an hour shows floor groups in API order as "<Gebäude> · <Stockwerk> – <n> Personen" with rooms, time range, attendees and "reserviert"/"eingecheckt" beneath; a button "Zur aktuellen Situation" hides the forecast detail; a day whose hours all have `expectedPersons` 0 and `peakExpectedPersons` null shows zeros, no peak marker and the hint "An diesem Tag sind keine Buchungen vorhanden."; a 400 from the API shows its message

### Implementation for User Story 2

- [X] T020 [US2] Add a package-private static helper `maxSimultaneous(List<OccupancyRepository.BookingRow> rows, Instant from, Instant to)` in `B/service/AdminOccupancyService.java`: clip each row's `[effectiveStart, endTime)` to `[from, to)`, sweep over start (+attendees) and end (−attendees) points with ends processed before starts at equal instants, return the maximum running sum (0 for no rows)
- [X] T021 [US2] Implement `AdminOccupancyResponse.Forecast forecast(LocalDate date, UUID buildingIdOrNull)` in `B/service/AdminOccupancyService.java`: reject `date` before `LocalDate.now(clock.withZone(ZONE))` with `IllegalArgumentException("Vergangene Tage sind im Bereich Statistiken auswertbar.")`; validate building; load `findOverlapping(dayStart, nextDayStart, buildingId)` and `findBuildings()`; build hours by stepping `ZonedDateTime` one hour from local midnight (handles 23/25-hour days), keep 07–21 plus hours with rows; per hour compute total, per-building values (via `maxSimultaneous` and distinct room ids) and `FloorGroup`s; mark past hours (`hourEnd <= now`); compute peaks for total and each building over non-past hours; build `ForecastBuilding` list (active buildings plus inactive ones with rows, or only the filtered building), sorted by name. Make T017 pass
- [X] T022 [US2] Add `@GetMapping("/api/admin/occupancy/forecast")` with `@RequestParam LocalDate date` and `@RequestParam(required = false) UUID buildingId` to `B/controller/AdminOccupancyController.java`, `Cache-Control: no-store`. Make T018 pass
- [X] T023 [US2] Add the section "Tagesprognose" to `F/pages/AdminOccupancyPage.tsx` (+ styles in `AdminOccupancyPage.css`): date input (`min` today, default today), hour × building table with "Gesamt" column and booked-rooms count, the hint "An diesem Tag sind keine Buchungen vorhanden." when `peakExpectedPersons` is null and no hour is past, peak marking with text not only colour, "geplant" label, "vergangen" for past hours, row click/Enter opens the floor-group list for that hour, button "Zur aktuellen Situation". The forecast loads on date change only (no polling). Make T019 pass

**Checkpoint**: US1 and US2 work independently (quickstart scenarios 4, 5, 5a, 6).

---

## Phase 5: User Story 3 – Liste der aktuell laufenden Buchungen (Priority: P2)

**Goal**: Under the KPIs, all running bookings with room, building, floor, times, attendees and check-in status, without personal data.

**Independent Test**: With known running bookings, every one is listed exactly once; a started but not checked-in booking shows "noch nicht eingecheckt" and does not change the KPIs.

### Tests for User Story 3 ⚠️ write first, must fail

- [X] T024 [P] [US3] Add cases to `T/service/AdminOccupancyServiceTest.java`: `snapshot(null).runningBookings` contains every covering row once, `RESERVED` as `NOT_CHECKED_IN` with `checkedInAt` null and `ACTIVE` as `CHECKED_IN` with `checkedInAt`, sorted by building name, room name, start time; totals unchanged by `RESERVED` rows
- [X] T025 [P] [US3] Add a case to `T/controller/AdminOccupancyControllerTest.java` that the JSON of both endpoints contains none of the keys `reservedFor`, `createdBy`, `createdByUserId`, `note` (string check on the response body)
- [X] T026 [P] [US3] Add cases to `F/pages/AdminOccupancyPage.test.tsx`: the list "Laufende Buchungen" shows room, building, floor, time range, attendees and "eingecheckt seit HH:mm" or "noch nicht eingecheckt"; empty list shows "Derzeit laufen keine Buchungen."

### Implementation for User Story 3

- [X] T027 [US3] Fill `runningBookings` in `snapshot` in `B/service/AdminOccupancyService.java` by mapping covering rows to `BookingEntry` (shared private mapper also used by `FloorGroup`). Make T024 and T025 pass
- [X] T028 [US3] Render the "Laufende Buchungen" list in `F/pages/AdminOccupancyPage.tsx` below the KPI tiles, refreshed with the same 30 s poll. Make T026 pass

**Checkpoint**: US1–US3 work (quickstart scenario 1 list part).

---

## Phase 6: User Story 4 – Aufschlüsselung nach Gebäude (Priority: P3)

**Goal**: KPIs per building and a building filter that applies to the current view and the forecast.

**Independent Test**: Checked-in bookings in A (8 persons, 1 room) and B (6, 2 rooms) → breakdown A 8/1, B 6/2, total 14/3; filter A shows only A everywhere.

### Tests for User Story 4 ⚠️ write first, must fail

- [X] T029 [P] [US4] Add cases to `T/service/AdminOccupancyServiceTest.java`: `snapshot(null).buildings` lists every active building (zeros included) and inactive buildings only when they have values, sorted by name, and its sums equal `totals`; `snapshot(buildingA)` returns only A in `buildings`, only A's rows in `runningBookings`, and `buildingId` = A
- [X] T030 [P] [US4] Add cases to `F/pages/AdminOccupancyPage.test.tsx`: the breakdown table shows one row per building with persons and rooms; selecting a building in the "Gebäude" select (options from `listBuildings('active')` in `F/API/buildings.ts`, plus "Alle Gebäude") calls `getOccupancySnapshot(id)` and, if a forecast is open, `getOccupancyForecast(date, id)`; the active filter is shown and "Alle Gebäude" resets it

### Implementation for User Story 4

- [X] T031 [US4] Fill `buildings` in `snapshot` in `B/service/AdminOccupancyService.java` from `findBuildings()` and the covering `ACTIVE` rows according to T029. Make T029 pass
- [X] T032 [US4] Add the building breakdown table and the "Gebäude" filter select to `F/pages/AdminOccupancyPage.tsx`; pass the selected id to both API calls and to the 30 s poll. Make T030 pass

**Checkpoint**: All stories work (quickstart scenario 3).

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T033 [P] Add `T/AdminOccupancyPerformanceIntegrationTest.java` following `T/RoomSearchPerformanceIntegrationTest.java`: 100 rooms and 500 running bookings; `snapshot(null)` and `forecast(today, null)` each complete in under 3 s (SC-006)
- [X] T034 [P] Add an end-to-end permission case to `T/ManagementPermissionIntegrationTest.java` (or the closest existing permission integration test): a user whose roles lack `STATISTICS_READ` gets 403 on both occupancy routes; after granting the right to that role, 200 (FR-002, feature 015 rules)
- [X] T035 [P] Check that no new log statement in `B/service/AdminOccupancyService.java` and `B/controller/AdminOccupancyController.java` writes personal data (Principle IV); run the existing `T/config/AccessDeniedLogPrivacyTest.java`
- [X] T036 Run the full suites `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`; fix any failures
- [x] T037 Walk through `specs/016-admin-occupancy-overview/quickstart.md` manual scenarios 1–10 on `docker compose up -d` and note the results in the PR description

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → user stories → **Polish (Phase 7)**
- Within Phase 2: T005 → T006; T007 → T008; T002, T003, T004 are independent.

### User story dependencies

- **US1 (P1)**: needs Phase 2 only. Creates the service, controller and page that later stories extend.
- **US2 (P2)**: needs Phase 2. Shares the service, controller and page files with US1, so do it after US1 (or coordinate edits); its logic is independent of US1.
- **US3 (P2)**: needs US1 (extends `snapshot` and the page).
- **US4 (P3)**: needs US1; the forecast part of T030/T032 needs US2.

### Within each story

Tests first and failing → service → controller → frontend page.

### Parallel opportunities

- Phase 2: T002, T003, T004, T007 in parallel.
- US1: T009, T010, T011, T012 in parallel (four different files).
- US2: T017, T018, T019 in parallel.
- US3: T024, T025, T026 in parallel. US4: T029, T030 in parallel.
- Backend and frontend tracks of one story can run in parallel once the story's tests exist: e.g. T013→T014 alongside T015→T016.
- Polish: T033, T034, T035 in parallel.

## Parallel Example: User Story 1

```text
Task: "T009 [US1] AdminOccupancyServiceTest snapshot cases in T/service/AdminOccupancyServiceTest.java"
Task: "T010 [US1] AdminOccupancyControllerTest in T/controller/AdminOccupancyControllerTest.java"
Task: "T011 [US1] AdminOccupancyPage tests in F/pages/AdminOccupancyPage.test.tsx"
Task: "T012 [US1] route/nav cases in F/App.protected-routes.test.tsx and Navigation.test.tsx"
```

## Implementation Strategy

### MVP first (User Story 1 only)

1. Phase 1 → Phase 2.
2. Phase 3 (US1): the two acceptance criteria of the original story are met.
3. Stop and validate with quickstart scenarios 1, 2, 7, 8; demo.
4. Before merging the MVP alone, either finish T027 and T031 (cheap backend-only additions) or document the empty `buildings`/`runningBookings` lists as a known gap in the PR.

### Incremental delivery

1. MVP (US1).
2. + US2 forecast (the planning feature the product owner asked for).
3. + US3 running bookings list.
4. + US4 building breakdown and filter.
5. Polish (performance, permission integration, quickstart walk-through).

---

## Phase 8: Convergence

- [X] T038 In `frontend/src/pages/AdminOccupancyPage.tsx`, hide the "Gebäude" filter while access is denied and reset `accessDenied` to false after a successful snapshot load, so the forecast section reappears when the right is granted again; add a case to `frontend/src/pages/AdminOccupancyPage.test.tsx` (403 on reload → no filter, no forecast; later 200 → both visible again) per FR-012 (partial)
- [X] T039 Add a timing step to `specs/016-admin-occupancy-overview/quickstart.md` (scenario 1: with the T033 data volume of 100 rooms / 500 running bookings, the page shows figures, bookings list and forecast within 3 s of opening, measured in the browser's network/performance panel) and record the result in the T037 walkthrough per SC-006 (partial) — *step added as scenario 9; the measurement itself happens during T037*
