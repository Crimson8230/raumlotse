---

description: "Task list for feature 008 – Room Search with Filters"
---

# Tasks: Room Search with Filters

**Input**: Design documents from `specs/008-room-search-filter/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/room-search-api.yaml](contracts/room-search-api.yaml), [contracts/ui.md](contracts/ui.md), [quickstart.md](quickstart.md)

**Tests**: REQUIRED. Constitution Principle I (Test-First, NON-NEGOTIABLE) applies. Every test task comes before the implementation it covers. Run it and confirm it **fails** before starting the implementation task, then make it pass.

**Organization**: Tasks are grouped by user story, and each story is an independently testable increment.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US4 from spec.md
- Paths are repository-relative. Backend Java root: `backend/src/main/java/at/mci/igp/raumlotse/` (abbreviated **`BE/`**). Backend test root: `backend/src/test/java/at/mci/igp/raumlotse/` (abbreviated **`BT/`**). Frontend root: `frontend/src/`.

## Conventions every task must follow

- Backend: constructor injection, controller → service → repository layering, Bean Validation on inputs, bound parameters only (no string-built JPQL/SQL), errors as `Problem` JSON via `GlobalExceptionHandler`, logs without personal data or filter free text.
- Controller tests use the existing `@WebMvcTest(...)` + `@Import(SecurityConfig.class)` + `@MockitoBean` style (see `BT/controller/RoomControllerTest.java`). Integration tests extend `BT/AbstractIntegrationTest.java` (Testcontainers PostgreSQL 17).
- Frontend: strict TypeScript, functional components, no `any`, API access only via `frontend/src/API/*` over `apiRequest`. Tests use Vitest + Testing Library with `vi.mock('../API/…')` as in `frontend/src/pages/RoomListPage.test.tsx`. Styling uses only existing design tokens from `frontend/src/index.css`.
- UI texts are German, exactly as specified in [contracts/ui.md](contracts/ui.md).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish a known-good baseline before changing anything.

- [X] T001 Run the baseline: `cd backend && ./mvnw test` (Docker required) and `cd frontend && npm install && npm test && npm run lint && npm run build`. Record any pre-existing failures in the PR description so they are not attributed to this feature. Code changes: none.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Generic 400 handling for malformed query parameters, which every search story relies on (research.md R5).

**⚠️ CRITICAL**: Complete before any user story.

- [X] T002 Write failing tests in `BT/controller/RoomControllerTest.java` asserting that `GET /api/rooms?status=all` still works, and that a malformed path UUID (`GET /api/rooms/not-a-uuid` as an authenticated user) returns `400` with JSON body `code = "VALIDATION_FAILED"` and a `Cache-Control: no-store` header. Today this is not handled by `GlobalExceptionHandler`.
- [X] T003 In `BE/exception/GlobalExceptionHandler.java`, make every parameter-binding error return an actionable `400`:
  - (a) In the **existing** `handleValidation(MethodArgumentNotValidException)` (Spring ≥ 6.1 raises it for `@Valid @ModelAttribute`, including type mismatches), map each `FieldError` whose `getCode()` is `"typeMismatch"` to the message `"has an invalid format"` instead of `getDefaultMessage()`, so Spring conversion texts ("Failed to convert property value…") never reach the client.
  - (b) Add a handler for `MethodArgumentTypeMismatchException` (path/`@RequestParam` mismatches) → `400`, `Problem.of(400, "Validation Failed", "One or more parameters are invalid.", List.of(new Problem.FieldError(ex.getName(), "has an invalid format")), "VALIDATION_FAILED", null)`.
  - (c) Add a fallback handler for `org.springframework.validation.BindException` with the same mapping as (a).
  - All use `CacheControl.noStore()` and `log.warn("validation_failed fields={}", <field names only>)` — no values.
  - T002 must pass.

**Checkpoint**: Foundation ready. User stories can start.

---

## Phase 3: User Story 1 – Find rooms that fit my group size and location (Priority: P1) 🎯 MVP

**Goal**: Signed-in users search fully active rooms by min/max persons and building on the "Räume" page, with an empty state, a reset action, and filters kept in the URL.

**Independent Test**: With rooms of different capacities in two buildings, set min/max persons and a building on `/rooms` and verify that exactly the matching active rooms are listed (quickstart §4 scenarios 1–5, 10).

### Tests for User Story 1 (write first, must fail) ⚠️

- [X] T004 [P] [US1] Create `BT/domain/RoomSearchCriteriaTest.java`. Plain JUnit, no Spring. Build `Room`/`Floor`/`Building`/`SeatingArrangement` in memory. Cover:
  - No criteria matches.
  - `minPersons` matches when **some** arrangement has `maxCapacity ≥ minPersons`, and fails otherwise.
  - `maxPersons` matches when some arrangement has `maxCapacity ≤ maxPersons`.
  - When both are given, a **single** arrangement must satisfy both. A room with arrangements 10 and 60 and range 20–50 does **not** match.
  - Boundaries: equal values match.
- [X] T005 [P] [US1] Create `BT/service/RoomSearchServiceTest.java` (Mockito). Cover:
  - `minPersons > maxPersons` throws `IllegalArgumentException` with message `"minPersons must not be greater than maxPersons."`.
  - Without `buildingId` it calls `RoomRepository.findSearchCandidates()`; with `buildingId` it calls `findSearchCandidatesInBuilding(buildingId)`.
  - Results are sorted by building name, then room name, both case-insensitive.
  - Results are mapped with `RoomResponse.from`.
- [X] T006 [P] [US1] Create `BT/controller/RoomSearchControllerTest.java` (`@WebMvcTest(RoomSearchController.class)`, `@Import(SecurityConfig.class)`, `@MockitoBean RoomSearchService`; same setup as `RoomControllerTest`, which mocks only its service because `SecurityConfig` tolerates the missing optional beans). Cover:
  - `GET /api/rooms/search` without a session → `401` `AUTH_REQUIRED`.
  - Authenticated with `minPersons=20&maxPersons=50&buildingId=<uuid>` → `200`, and the service receives exactly these values.
  - `minPersons=0` → `400` `VALIDATION_FAILED` with field `minPersons`.
  - `minPersons=abc` → `400` `VALIDATION_FAILED` with `errors[0] = {field: "minPersons", message: "has an invalid format"}`; the body must **not** contain `"Failed to convert"`.
  - `buildingId=xyz` → `400`.
  - Service throws `IllegalArgumentException` → `400`.
  - `/api/rooms/search` is not routed to `RoomController.get(UUID)`.
- [X] T007 [P] [US1] Create `BT/RoomSearchIntegrationTest.java` extending `AbstractIntegrationTest`. Seed data via repositories: two buildings, floors, rooms with capacities 40/15/120, one deactivated room, one room on a deactivated floor, one room in a deactivated building. Authenticate as a real account, reusing the login helper pattern from `BT/ReservationCreationIntegrationTest.java`. Assert:
  - Unfiltered search returns only fully active rooms (FR-002), in sorted order.
  - `minPersons=20` excludes the 15-seat room.
  - `maxPersons=50` excludes the 120-seat room.
  - `buildingId` restricts to that building.
  - A filter set nothing matches returns `[]`.
- [X] T008 [P] [US1] Create `frontend/src/components/RoomSearch/roomSearchParams.test.ts` for the pure helpers (to be created in T018):
  - `parseSearchParams(URLSearchParams) → RoomSearchFormState` round-trips with `toSearchParams(state)`.
  - `roomSearchSchema.safeParse(state)` (zod, pattern of `frontend/src/auth/loginSchema.ts`) fails with the German messages from contracts/ui.md: `"Bitte eine ganze Zahl ab 1 eingeben."` for `0`, `-1`, `1.5`, and `abc` in `minPersons`/`maxPersons`, and `"Min. Personen darf nicht größer als Max. Personen sein."` (issue path `maxPersons`) for min > max. `validate(state)` returns these issues as a `Record<field, message>`.
  - `toApiQuery(state)` omits empty fields and emits `minPersons`, `maxPersons`, `buildingId`.
- [X] T009 [P] [US1] Create `frontend/src/components/RoomSearch/RoomSearchPanel.test.tsx`:
  - Renders labeled inputs "Personen min.", "Personen max.", and select "Gebäude" with option "Alle Gebäude" plus the given active buildings.
  - "Suchen" calls `onSearch` with the form state.
  - Invalid input shows the inline error and does **not** call `onSearch`.
  - "Filter zurücksetzen" calls `onReset`.
  - `disabled` prop disables all controls and shows "Die Suche umfasst nur aktive Räume.".
- [X] T010 [P] [US1] Extend `frontend/src/pages/RoomListPage.test.tsx`. Mock `searchRooms`, `listRooms`, `listBuildings`, and `listEquipmentTypes`. Cover:
  - With status "Active", the page calls `searchRooms` with the criteria from the URL (render inside `MemoryRouter initialEntries={['/rooms?minPersons=20']}`) and lists the results.
  - Each row shows the room link, building, floor, seating arrangements as `Name (max N)`, and equipment names resolved from `listEquipmentTypes('all')`.
  - Submitting the panel updates the URL and reloads.
  - An empty result shows `"Keine passenden Räume gefunden."` and a "Filter zurücksetzen" button, which clears the URL parameters.
  - Switching status to "Deactivated" calls `listRooms('deactivated')` and disables the panel.
  - When two searches start in quick succession and the **first** response resolves **after** the second, only the second result is shown. Mock `searchRooms` with two deferred promises resolved in reverse order.
  - Existing deactivate/reactivate/delete tests still pass.

### Implementation for User Story 1

- [X] T011 [US1] Create `BE/domain/RoomSearchCriteria.java` as a record `(Integer minPersons, Integer maxPersons, UUID buildingId)`. Add a method `boolean matches(Room room)` implementing data-model.md rule 3 for persons only: if `minPersons` or `maxPersons` is non-null, at least one `SeatingArrangement` satisfies `(minPersons == null || maxCapacity >= minPersons) && (maxPersons == null || maxCapacity <= maxPersons)`. The building restriction is done by the query, not here. Makes T004 pass.
- [X] T012 [US1] In `BE/repository/RoomRepository.java`, add two parameterized JPQL queries with fetch joins:
  - `findSearchCandidates()`: `select r from Room r join fetch r.floor f join fetch f.building b where r.status = ACTIVE and f.status = ACTIVE and b.status = ACTIVE`.
  - `findSearchCandidatesInBuilding(@Param("buildingId") UUID buildingId)`: the same query plus `and b.id = :buildingId`.
  - Use two methods instead of a nullable parameter, to avoid null-typed parameter issues in Hibernate.
  - Use the fully qualified enum literal `at.mci.igp.raumlotse.domain.EntityStatus.ACTIVE`, as `ReservationRepository` does.
- [X] T013 [US1] Create `BE/dto/RoomSearchRequest.java` as a record bound from query parameters: `@Min(value = 1, message = "must be a whole number of at least 1") Integer minPersons`, the same for `Integer maxPersons`, and `UUID buildingId`. Add `toCriteria()` returning `RoomSearchCriteria`.
- [X] T014 [US1] Create `BE/service/RoomSearchService.java` (`@Service`, `@Transactional(readOnly = true)`) with `List<RoomResponse> search(RoomSearchCriteria criteria)`:
  - Validate `minPersons ≤ maxPersons`, else throw `IllegalArgumentException("minPersons must not be greater than maxPersons.")`.
  - Load the candidates (T012), filter them with `criteria.matches(room)`, and sort with `Comparator.comparing(building name, CASE_INSENSITIVE_ORDER).thenComparing(room name, CASE_INSENSITIVE_ORDER)`.
  - Map the results with `RoomResponse.from`.
  - Log `log.info("room_search result_count={}", n)`.
  - Makes T005 pass.
- [X] T015 [US1] Create `BE/controller/RoomSearchController.java` (`@RestController`). Add `@GetMapping("/api/rooms/search") List<RoomResponse> search(@Valid @ModelAttribute RoomSearchRequest request)`, which delegates to `RoomSearchService.search(request.toCriteria())`. No security config change is needed: `anyRequest().authenticated()` already applies. Makes T006 and T007 pass.
- [X] T016 [P] [US1] In `frontend/src/types/room.ts`, add the `RoomSearchFormState` interface (string fields: `minPersons`, `maxPersons`, `buildingId`, `seatingArrangement`, `equipmentTypeIds: string[]`, `barrierFree: boolean`, `date`, `startTime`, `endTime`) and `emptySearchFormState`. Later stories use the extra fields; US1 only reads and writes `minPersons`, `maxPersons`, and `buildingId`.
- [X] T017 [P] [US1] In `frontend/src/API/rooms.ts`, add `searchRooms(query: URLSearchParams): Promise<Room[]>`, which calls `apiRequest<Room[]>('/api/rooms/search?' + query.toString())`.
- [X] T018 [US1] Create `frontend/src/components/RoomSearch/roomSearchParams.ts`:
  - `roomSearchSchema`, a zod `z.object` over `RoomSearchFormState`. Empty strings are allowed. Person fields use `z.string().regex(/^\d*$/).refine(v => v === '' || Number(v) >= 1, …)`. Cross-field rules go in `.superRefine`.
  - `validate(state)`, which flattens the `safeParse` issues to `Record<field, message>`.
  - `parseSearchParams`, `toSearchParams`, and `toApiQuery` for the US1 fields.
  - No new dependency: zod is already present (Constitution IV, schema validation on frontend forms).
  - Makes T008 pass.
- [X] T019 [US1] Create `frontend/src/components/RoomSearch/RoomSearchPanel.tsx` and `RoomSearchPanel.css` (design tokens only). It is a controlled form with props `{ value, buildings, onSearch, onReset, disabled }`. Inline errors use the `feedback-error` class, and the layout stays usable at 375px width. Makes T009 pass.
- [X] T020 [US1] Integrate the search into `frontend/src/pages/RoomListPage.tsx`:
  - Read and write the filters via `useSearchParams`.
  - Load active buildings (`listBuildings('active')`) and all equipment types (`listEquipmentTypes('all')`) for name lookup.
  - With status `active`, call `searchRooms(toApiQuery(state))`. Otherwise use the existing `listRooms(status)` and pass `disabled` to the panel.
  - Render the result rows as in contracts/ui.md §1, the empty state `"Keine passenden Räume gefunden."` with a reset button, and keep the existing admin actions.
  - The search effect uses the existing `ignore` cleanup pattern of `RoomListPage`, so a superseded request never updates state.
  - Makes T010 pass.

**Checkpoint**: MVP complete. Search by persons and building works end to end. Validate quickstart §4 scenarios 1–5 and 10.

---

## Phase 4: User Story 2 – Filter by seating layout and equipment (Priority: P2)

**Goal**: Add the seating arrangement (name) filter and the equipment filter, which requires **all** selected active catalog types. The arrangement filter must be satisfied together with the person range by a single arrangement (FR-007).

**Independent Test**: quickstart §4 scenarios 6–8, plus a newly added catalog type (e.g., "Microphone") appears as a filter and works (US2-5).

### Tests for User Story 2 (write first, must fail) ⚠️

- [X] T021 [P] [US2] Extend `BT/domain/RoomSearchCriteriaTest.java`:
  - `seatingArrangement` matches trimmed and case-insensitively (`" u-shape "` matches `"U-Shape"`).
  - FR-007: arrangements Theater 60 / U-Shape 20 with `seatingArrangement=U-Shape, minPersons=30` → **no** match; `minPersons=20` → match.
  - `equipmentTypeIds` requires **all** of them: a room with {P, W} matches {P, W} and {P}, and a room with {P} does not match {P, W}.
  - An unknown equipment ID never matches.
  - A blank `seatingArrangement` is treated as absent.
- [X] T022 [P] [US2] Extend `BT/service/RoomSearchServiceTest.java`: `listSeatingArrangementNames()` returns the case-insensitively distinct names of the search candidates (first-seen spelling wins), sorted with `String.CASE_INSENSITIVE_ORDER`.
- [X] T023 [P] [US2] Extend `BT/controller/RoomSearchControllerTest.java`:
  - `seatingArrangement` longer than 100 characters → `400` (field `seatingArrangement`).
  - A repeated `equipmentTypeId=<a>&equipmentTypeId=<b>` binds to a set with both.
  - `GET /api/rooms/search/seating-arrangements` → `200` JSON string array, and `401` without a session.
- [X] T024 [P] [US2] Extend `BT/RoomSearchIntegrationTest.java` with the quickstart data for rooms A–D (arrangements and equipment). Assert scenarios 6 (A, D), 7 (D only), and 8 (A). Also cover a deactivated equipment type still assigned to a room: that room is still returned when no equipment filter is set.
- [X] T025 [P] [US2] Extend `frontend/src/components/RoomSearch/roomSearchParams.test.ts`: `seatingArrangement` round-trips, `equipmentTypeIds` round-trip as repeated `equipmentTypeId` parameters, and `toApiQuery` emits one `equipmentTypeId` per selected ID.
- [X] T026 [P] [US2] Extend `frontend/src/components/RoomSearch/RoomSearchPanel.test.tsx`:
  - The "Bestuhlung" select shows "Alle" plus the given names.
  - "Ausstattung" shows one checkbox per given **active** equipment type.
  - Toggling the checkboxes updates the state passed to `onSearch`.
- [X] T027 [P] [US2] Extend `frontend/src/pages/RoomListPage.test.tsx`: the page loads `listSeatingArrangementNames()` and `listEquipmentTypes('all')`, passes only `ACTIVE` types to the panel, and `?equipmentTypeId=a&equipmentTypeId=b` in the URL reaches `searchRooms`.

### Implementation for User Story 2

- [X] T028 [US2] Extend `BE/domain/RoomSearchCriteria.java` with `String seatingArrangement` and `Set<UUID> equipmentTypeIds`:
  - Normalize in the compact constructor: trim, blank → null; null set → empty set.
  - Extend `matches` so the arrangement predicate also checks `name.equalsIgnoreCase(seatingArrangement)` within the **same** arrangement.
  - Add the check that all `equipmentTypeIds` are contained in the room's equipment type IDs.
  - Makes T021 pass.
- [X] T029 [US2] Extend `BE/dto/RoomSearchRequest.java` with `@Size(max = 100, message = "must be at most 100 characters") String seatingArrangement` and `List<UUID> equipmentTypeId`, which binds from the repeated query parameter named `equipmentTypeId`. Map both in `toCriteria()`.
- [X] T030 [US2] In `BE/service/RoomSearchService.java`, add `List<String> listSeatingArrangementNames()` over `findSearchCandidates()`. Makes T022 pass.
- [X] T031 [US2] In `BE/controller/RoomSearchController.java`, add `@GetMapping("/api/rooms/search/seating-arrangements")`. Makes T023 and T024 pass.
- [X] T032 [P] [US2] In `frontend/src/API/rooms.ts`, add `listSeatingArrangementNames(): Promise<string[]>`, which calls `/api/rooms/search/seating-arrangements`.
- [X] T033 [US2] In `frontend/src/components/RoomSearch/roomSearchParams.ts`, add `seatingArrangement` and repeated `equipmentTypeId` handling. Makes T025 pass.
- [X] T034 [US2] In `frontend/src/components/RoomSearch/RoomSearchPanel.tsx`, add the "Bestuhlung" select and the "Ausstattung" checkbox group, with new props `seatingArrangementNames: string[]` and `equipmentTypes: EquipmentType[]`. Makes T026 pass.
- [X] T035 [US2] In `frontend/src/pages/RoomListPage.tsx`, load the arrangement names and pass the active equipment types to the panel. Makes T027 pass.

**Checkpoint**: US1 and US2 work together and independently. Validate quickstart §4 scenarios 6–8.

---

## Phase 5: User Story 3 – Filter for barrier-free reachable rooms (Priority: P2)

**Goal**: Administrators record elevator (building), ground floor (floor), and a "not barrier-free" exclusion (room). The search filters with `(floor.groundFloor OR building.hasElevator) AND NOT room.notBarrierFree`, and the UI shows the derived value.

**Independent Test**: quickstart §4 scenario 9 (A and D listed; B excluded for upper floor without elevator; C excluded by the room exclusion). Also: toggling the building's elevator changes the result immediately.

### Tests for User Story 3 (write first, must fail) ⚠️

- [X] T036 [P] [US3] Extend `BT/repository/CatalogRepositoryIntegrationTest.java`: after migrations, freshly inserted buildings, floors, and rooms have `hasElevator == false`, `groundFloor == false`, and `notBarrierFree == false` (V9 defaults `BOOLEAN NOT NULL DEFAULT false`). Persisting `true` round-trips.
- [X] T037 [P] [US3] Create `BT/domain/RoomBarrierFreeTest.java` with the full truth table for `Room.isBarrierFreeReachable()`: `groundFloor × hasElevator × notBarrierFree`. The result is true only for `(gf || elev) && !nbf`.
- [X] T038 [P] [US3] Extend `BT/controller/BuildingControllerTest.java`:
  - `POST {"name":"Haus 1","hasElevator":true}` → response `hasElevator: true`.
  - `POST` without `hasElevator` → `false`.
  - `PUT {"name":"Haus 1b"}` without `hasElevator` → the service is called with `null`, which means unchanged.
  - `PUT {"name":"Haus 1","hasElevator":false}` → the service is called with `false`.
- [X] T039 [P] [US3] Extend `BT/controller/FloorControllerTest.java` with the same four cases for `groundFloor` on `POST /api/buildings/{id}/floors` and `PUT /api/floors/{id}`.
- [X] T040 [P] [US3] Extend `BT/controller/RoomControllerTest.java` and `BT/service/RoomServiceTest.java`:
  - Create without `notBarrierFree` → `false`.
  - Create with `true` → `true`.
  - Update with `notBarrierFree` null → unchanged.
  - Update with `false` → `false`.
  - `RoomResponse` contains `notBarrierFree` and `barrierFreeReachable`.
- [X] T041 [P] [US3] Extend `BT/domain/RoomSearchCriteriaTest.java`: `barrierFree = true` keeps only rooms with `isBarrierFreeReachable()`, and `barrierFree = false` does not restrict.
- [X] T042 [P] [US3] Extend `BT/RoomSearchIntegrationTest.java` with scenario 9 (Haus 1 with elevator, Haus 2 without; EG marked; room C excluded). Add a second floor "EG Nord" in Haus 2, also marked as ground floor, with one room: that room is listed too. Then set Haus 1 `hasElevator=false` via `PUT /api/buildings/{id}` and assert that A disappears from `barrierFree=true`.
- [X] T043 [P] [US3] Extend `frontend/src/components/BuildingCatalog/BuildingCatalog.test.tsx`:
  - The create-building form has the checkbox "Aufzug vorhanden", and its value is sent to `createBuilding(name, hasElevator)`.
  - The building row shows "Aufzug" / "kein Aufzug", and its toggle calls `updateBuilding(id, currentName, !hasElevator)`.
  - The create-floor form has "Erdgeschoss (stufenloser Zugang)", and the floor-row toggle calls `updateFloor(id, currentName, !groundFloor)`.
- [X] T044 [P] [US3] Extend `frontend/src/components/RoomForm/RoomForm.test.tsx`: the checkbox "Nicht barrierefrei (Ausnahme)" with the helper text from contracts/ui.md §4 defaults to unchecked, is pre-checked when editing a room with `notBarrierFree: true`, and its value is sent in the create and update payloads.
- [X] T045 [P] [US3] Extend `frontend/src/pages/RoomDetailPage.test.tsx` (metadata shows "Barrierefrei erreichbar" with "Ja"/"Nein" from `barrierFreeReachable`), `RoomSearchPanel.test.tsx` (checkbox "Barrierefrei erreichbar" → `barrierFree: true`), `roomSearchParams.test.ts` (`barrierFree=true` round-trip; `false` omitted), and `RoomListPage.test.tsx` (the result row shows the marker "Barrierefrei erreichbar" only when `barrierFreeReachable`).

### Implementation for User Story 3

- [X] T046 [US3] Create `backend/src/main/resources/db/migration/V9__add_barrier_free_attributes.sql` with `ALTER TABLE building ADD COLUMN has_elevator BOOLEAN NOT NULL DEFAULT false;`, `ALTER TABLE floor ADD COLUMN ground_floor BOOLEAN NOT NULL DEFAULT false;` and `ALTER TABLE room ADD COLUMN not_barrier_free BOOLEAN NOT NULL DEFAULT false;`. `BT/MigrationVersionTest.java` must stay green.
- [X] T047 [US3] Add the entity fields:
  - `BE/domain/Building.java`: `@Column(name = "has_elevator", nullable = false) private boolean hasElevator;` with getter and setter.
  - `BE/domain/Floor.java`: `@Column(name = "ground_floor", nullable = false) private boolean groundFloor;` with getter and setter.
  - `BE/domain/Room.java`: `@Column(name = "not_barrier_free", nullable = false) private boolean notBarrierFree;` with getter and setter, plus `public boolean isBarrierFreeReachable() { return (floor.isGroundFloor() || floor.getBuilding().isHasElevator()) && !notBarrierFree; }`. You may name the building getter `hasElevator()`; stay consistent.
  - Makes T036 and T037 pass.
- [X] T048 [US3] Add the DTO fields (additive, backward compatible):
  - `BE/dto/BuildingRequest.java`: add `Boolean hasElevator` (nullable).
  - `BE/dto/BuildingResponse.java`: add `boolean hasElevator`.
  - `BE/dto/FloorRequest.java`: add `Boolean groundFloor` (nullable).
  - `BE/dto/FloorResponse.java`: add `boolean groundFloor`.
  - `BE/dto/RoomCreateRequest.java` and `BE/dto/RoomUpdateRequest.java`: add `Boolean notBarrierFree` (nullable).
  - `BE/dto/RoomResponse.java`: add `boolean notBarrierFree` and `boolean barrierFreeReachable` (from `room.isBarrierFreeReachable()`).
  - Update every `new …Request(...)` / `new …Response(...)` call site in main and test code so they compile.
- [X] T049 [P] [US3] Amend `specs/001-room-management/contracts/openapi.yaml` additively: add `hasElevator` to `Building`/`BuildingRequest`, `groundFloor` to `Floor`/`FloorRequest`, `notBarrierFree` to `RoomCreateRequest` (and via `allOf` to `RoomUpdateRequest`), and `notBarrierFree`/`barrierFreeReachable` to `Room`. Document the null semantics, bump `info.version` by a minor version, and add a reference to `specs/008-room-search-filter/contracts/room-search-api.yaml` (Constitution III).
- [X] T050 [US3] Make the services respect the "null" rules:
  - `BE/service/BuildingService.java`: `create(String name, Boolean hasElevator)`, where null → false. Replace `rename(UUID, String)` with `update(UUID id, String name, Boolean hasElevator)`: rename as before, and set the flag only if it is non-null.
  - `BE/service/FloorService.java`: the same pattern with `create(UUID buildingId, String name, Boolean groundFloor)` and `update(UUID id, String name, Boolean groundFloor)`.
  - `BE/service/RoomService.java`: in `create`, null → false; in `update`, null → unchanged.
  - In `BE/controller/BuildingController.java` and `BE/controller/FloorController.java`, pass the new request fields through. Keep the paths and HTTP methods.
  - Makes T038, T039, and T040 pass.
- [X] T051 [US3] Extend `BE/domain/RoomSearchCriteria.java` with `boolean barrierFree`: when it is true, the room must satisfy `room.isBarrierFreeReachable()`. Extend `BE/dto/RoomSearchRequest.java` with `Boolean barrierFree` (null → false). Makes T041 and T042 pass.
- [X] T052 [P] [US3] Update `frontend/src/types/room.ts`: `Building.hasElevator: boolean`, `Floor.groundFloor: boolean`, `Room.notBarrierFree: boolean`, `Room.barrierFreeReachable: boolean`, and `RoomCreateRequest.notBarrierFree?: boolean`. Then fix the test fixtures that construct these types.
- [X] T053 [P] [US3] Update the API wrappers:
  - `frontend/src/API/buildings.ts`: `createBuilding(name: string, hasElevator = false)` and `updateBuilding(id: string, name: string, hasElevator?: boolean)`, with `renameBuilding` delegating to `updateBuilding(id, name)`.
  - `frontend/src/API/floors.ts`: `createFloor(buildingId, name, groundFloor = false)` and `updateFloor(id, name, groundFloor?)`, with `renameFloor` delegating the same way.
  - Keep existing call sites working.
- [X] T054 [US3] In `frontend/src/components/BuildingCatalog/BuildingCatalog.tsx`, add the elevator and ground-floor checkboxes and the row toggles per contracts/ui.md §2. Makes T043 pass.
- [X] T055 [US3] In `frontend/src/components/RoomForm/RoomForm.tsx`, add the checkbox "Nicht barrierefrei (Ausnahme)" with the helper text "Nur setzen, wenn der Raum trotz Erdgeschoss oder Aufzug nicht barrierefrei ist." and include `notBarrierFree` in the create and update payloads. Makes T044 pass.
- [X] T056 [US3] Add the "Barrierefrei erreichbar" display and filter:
  - Show "Barrierefrei erreichbar: Ja/Nein" in `frontend/src/pages/RoomDetailPage.tsx`.
  - Add the checkbox "Barrierefrei erreichbar" in `RoomSearchPanel.tsx`.
  - Add `barrierFree` handling in `roomSearchParams.ts`: emit `barrierFree=true` only when checked.
  - Show the result-row marker in `RoomListPage.tsx`.
  - Makes T045 pass.

**Checkpoint**: US1–US3 work. Validate quickstart §4 scenario 9 and the elevator toggle.

---

## Phase 6: User Story 4 – Find rooms free at a given date and time (Priority: P3)

**Goal**: An optional date + start/end time excludes rooms with a `RESERVED`/`ACTIVE` reservation overlapping `[from, to)`. Results then link to the room with a pre-filled booking form (FR-011a).

**Independent Test**: quickstart §5 steps 1–7.

### Tests for User Story 4 (write first, must fail) ⚠️

- [X] T057 [P] [US4] Create `BT/repository/ReservationOccupancyIntegrationTest.java` extending `AbstractIntegrationTest`. Test `ReservationRepository.findOccupiedRoomIds(from, to)` for one room with one reservation 10:00–12:00:
  - A window of 11:00–13:00 → occupied.
  - A window of 12:00–13:00 → not occupied (half-open interval).
  - A window of 09:00–10:00 → not occupied.
  - A window of 09:00–10:01 → occupied.
  - The same windows with the reservation in `CANCELLED`, `EXPIRED`, or `COMPLETED` → not occupied.
  - `ACTIVE` → occupied.
- [X] T058 [P] [US4] Extend `BT/domain/RoomSearchCriteriaTest.java`: with `from`/`to` set, a room whose ID is in `occupiedRoomIds` does not match. Without `from`/`to`, occupancy is ignored.
- [X] T059 [P] [US4] Extend `BT/service/RoomSearchServiceTest.java`:
  - Only one of `from`/`to` given → `IllegalArgumentException("from and to must be given together.")`.
  - `to` not after `from` → `IllegalArgumentException("to must be after from.")`.
  - A past window is accepted.
  - `findOccupiedRoomIds` is called only when the window is set, and its result excludes rooms.
- [X] T060 [P] [US4] Extend `BT/controller/RoomSearchControllerTest.java`: valid ISO-8601 `from`/`to` bind to `Instant`, `from=not-a-date` → `400` `VALIDATION_FAILED`.
- [X] T061 [P] [US4] Extend `BT/RoomSearchIntegrationTest.java` with quickstart §5 steps 1–4 via the API: book room A through `POST /api/rooms/{id}/reservations`, search the windows, cancel, and search again.
- [X] T062 [P] [US4] Extend `frontend/src/components/RoomSearch/roomSearchParams.test.ts`:
  - `date`, `startTime`, and `endTime` round-trip in the page URL.
  - `toApiQuery` converts them to `from`/`to` ISO instants via `new Date(\`${date}T${startTime}\`).toISOString()`.
  - `roomSearchSchema` rejects partial input → `"Bitte Datum, Von und Bis vollständig angeben."`.
  - `roomSearchSchema` rejects an end not after the start → `"Bis muss nach Von liegen."`.
  - Add a helper `roomLink(roomId, state)` that returns `/rooms/{id}?start=<ISO>&end=<ISO>` for a complete window and `/rooms/{id}` otherwise.
- [X] T063 [P] [US4] Extend `RoomSearchPanel.test.tsx` (inputs "Datum", "Von", and "Bis" of types `date` and `time`) and `RoomListPage.test.tsx` (with a window in the URL, the result links carry `?start=&end=`; without one they do not).
- [X] T064 [P] [US4] Extend `frontend/src/components/ReservationForm/ReservationForm.test.tsx`: with the new props `initialStartTime`/`initialEndTime` (ISO strings), the Start/End `datetime-local` inputs show the local-time equivalent (`YYYY-MM-DDTHH:mm`) and remain editable. Without the props, the fields are empty as before.
- [X] T065 [P] [US4] Extend `frontend/src/pages/RoomDetailPage.test.tsx`:
  - For `/rooms/room-1?start=<ISO>&end=<ISO>` with an ACTIVE room, the booking form is open and pre-filled.
  - Invalid or partial parameters, or `end <= start` → the form is closed.
  - A DEACTIVATED room → no form.
  - Submitting still calls `createReservation` with the (possibly edited) values.

### Implementation for User Story 4

- [X] T066 [US4] In `BE/repository/ReservationRepository.java`, add `@Query("select distinct r.room.id from Reservation r where r.status in (at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED, at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE) and r.startTime < :to and r.endTime > :from") Set<UUID> findOccupiedRoomIds(@Param("from") Instant from, @Param("to") Instant to);`. Makes T057 pass.
- [X] T067 [US4] Extend `BE/domain/RoomSearchCriteria.java` with `Instant from` and `Instant to`, and change the matcher to `matches(Room room, Set<UUID> occupiedRoomIds)`, excluding occupied rooms when a window is set. Update the US1–US3 call sites and tests to pass `Set.of()`. Makes T058 pass.
- [X] T068 [US4] Extend the request and service:
  - `BE/dto/RoomSearchRequest.java`: add `Instant from` and `Instant to`. Bind ISO-8601 with `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)`.
  - `BE/service/RoomSearchService.java`: validate the pair rules (messages as in T059), call `findOccupiedRoomIds` only when the window is set, and pass the set to `matches`.
  - Makes T059, T060, and T061 pass.
- [X] T069 [US4] Add the window to the frontend search:
  - `roomSearchParams.ts`: `date`/`startTime`/`endTime` handling, the date/time rules as additional `.superRefine` rules in `roomSearchSchema`, ISO conversion, and `roomLink`.
  - `RoomSearchPanel.tsx`: the "Datum", "Von", and "Bis" inputs.
  - `RoomListPage.tsx`: result links via `roomLink`.
  - Makes T062 and T063 pass.
- [X] T070 [US4] In `frontend/src/components/ReservationForm/ReservationForm.tsx`, add the optional props `initialStartTime?: string` and `initialEndTime?: string` (ISO). Initialize the `startTime`/`endTime` state with a local `YYYY-MM-DDTHH:mm` conversion helper, added to `frontend/src/utils/date.ts` as `toDateTimeLocalValue(iso: string): string` with a unit test in `frontend/src/utils/date.test.ts`. Makes T064 pass.
- [X] T071 [US4] In `frontend/src/pages/RoomDetailPage.tsx`, read `start`/`end` via `useSearchParams`. The room loads asynchronously, so the decision is made **after it has loaded**: in the `getRoom` success callback, set `showBookingForm` to true only if both parameters parse as valid dates, `end > start`, and the loaded `room.status === 'ACTIVE'`. Pass the values to `ReservationForm` only in that case. `showBookingForm` keeps its initial value `false`, so a deactivated room or invalid parameters never open the form, not even briefly. Otherwise the parameters are ignored. Makes T065 pass.

**Checkpoint**: All four stories work. Validate quickstart §5.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T072 [P] Update `README.md`: add `GET /api/rooms/search` and `GET /api/rooms/search/seating-arrangements` to the endpoint list, describe the room search in "Current Status", mention the barrier-free attributes and their `false` defaults, and remove "Room search and reservation workflow" from "Planned".
- [X] T073 Review logging: search logs only contain `result_count` and parameter-validation codes, never the seating arrangement text, IDs of other users, or personal data (Constitution IV). Check `BE/service/RoomSearchService.java` and `BE/exception/GlobalExceptionHandler.java`.
- [X] T074 Add `BT/RoomSearchPerformanceIntegrationTest.java` (extends `AbstractIntegrationTest`, `@Tag("performance")`), SC-003:
  - Seed 500 active rooms (3 seating arrangements and 2 equipment types each) across 10 buildings, plus 200 reservations, via repositories.
  - Warm up once, then run 20 searches via MockMvc with mixed filters (persons, building, equipment, barrierFree, time window). Assert that the 95th percentile is < 2000 ms.
  - Enable Hibernate statistics for the test (`hibernate.generate_statistics=true` as a test property) and assert that one search with a time window over the 500 rooms runs **≤ 15 SQL statements** (N+1 guard). Derivation: 1 candidate query + 1 occupancy query + ⌈500/100⌉ = 5 batch loads each for the two collections `seatingArrangements` and `equipmentTypes` = 12, plus a reserve of 3. Without batching the same search needs about 1,000 statements. Both collections are `List` mappings, so they cannot both be fetch-joined (`MultipleBagFetchException`); batch fetching is the intended mechanism.
  - Run it and observe the statement-count assertion **failing**. Only then add `spring.jpa.properties.hibernate.default_batch_fetch_size: 100` under the existing `spring:` key in `backend/src/main/resources/application.yaml`, and make the test pass (Constitution I).
- [X] T075 Run the full suites and gates: `cd backend && ./mvnw test`, and `cd frontend && npm test && npm run lint && npm run build`. Everything must be green, with no `any` and no lint suppressions without justification. The performance test may be excluded locally with `-DexcludedGroups=performance`, but it must run for the PR.
- [X] T076 Execute [quickstart.md](quickstart.md) §2–§6 manually against `docker compose up --build` + `npm run dev`, including the 375px mobile viewport check of the search panel (FR-010 of feature 002). Record the results in the PR description.

---

## Phase 8: Change Request – "Buchen" action in the result list (2026-09-28, FR-011b)

**Goal**: A direct "Buchen" action per active result that opens the booking form, requested after the quickstart validation.

**Independent Test**: In the search results, "Buchen" on room B opens its detail page with the booking form open; with a time window in the search the form is pre-filled, without one it is empty. Deactivated rooms in the "All" list show no "Buchen".

- [X] T077 [P] [CR] Write failing tests:
  - `frontend/src/components/RoomSearch/roomSearchParams.test.ts`: `bookingLink(roomId, state)` returns `/rooms/{id}?book=true` without a window and adds `start`/`end` (same ISO values as `roomLink`) with a complete window.
  - `frontend/src/pages/RoomListPage.test.tsx`: each `ACTIVE` result row has a link named "`<room name>` buchen" with visible text "Buchen" pointing to `bookingLink(...)`; a `DEACTIVATED` room in the "All" list has none.
  - `frontend/src/pages/RoomDetailPage.test.tsx`: `?book=true` opens an empty booking form for an `ACTIVE` room, never for a `DEACTIVATED` room; `?book=true&start=…&end=…` opens it pre-filled.
- [X] T078 [CR] Implement `bookingLink` in `frontend/src/components/RoomSearch/roomSearchParams.ts`, the "Buchen" link in `frontend/src/pages/RoomListPage.tsx` (button look via a class in `RoomListPage.css`, design tokens only), and the `book=true` rule in `frontend/src/pages/RoomDetailPage.tsx` (decided after the room has loaded, like T071). Makes T077 pass.
- [X] T079 [CR] Run `npm test`, `npm run lint`, `npm run build`, then re-check the "Buchen" flow in the running app.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: no dependencies.
- **Foundational (T002–T003)**: after Setup; blocks all stories (400 handling for query binding).
- **US1 (T004–T020)**: after Foundational. This is the MVP.
- **US2 (T021–T035)**: after US1. It extends `RoomSearchCriteria`, `RoomSearchRequest`, the panel, and the page created in US1.
- **US3 (T036–T056)**:
  - The data part (T036–T040, T046–T050, T052–T055) can start right after Foundational, in parallel with US1.
  - The search part (T041, T042, T045, T051, T056) needs US1.
- **US4 (T057–T071)**:
  - The repository part (T057, T066) and the pre-fill part (T064, T070) can start after Foundational.
  - The search part needs US1.
  - T067 changes the matcher signature, so run it after the US2 and US3 matcher tasks, or update their call sites.
- **Polish (T072–T076)**: after all desired stories. The performance task needs US1 and US4 (time-window search).

### Within Each Story

Tests (marked ⚠️) are written first and must fail. Then comes domain → repository → DTO → service → controller on the backend, and types → API → helpers → components → page on the frontend. Commit after each green test/implementation pair.

### Parallel Opportunities

- All tasks marked [P] inside a story's test block touch different files and can be written in parallel.
- Backend and frontend tracks of the same story can proceed in parallel once the contract is fixed. Both are defined in `contracts/`.
- US3's catalog and room-form work (T046–T050, T052–T055) is independent of the search code and can be done by a second developer during US1.

### Parallel Example: User Story 1

```text
# Tests in parallel (different files):
T004 BT/domain/RoomSearchCriteriaTest.java
T005 BT/service/RoomSearchServiceTest.java
T006 BT/controller/RoomSearchControllerTest.java
T007 BT/RoomSearchIntegrationTest.java
T008 frontend/src/components/RoomSearch/roomSearchParams.test.ts
T009 frontend/src/components/RoomSearch/RoomSearchPanel.test.tsx
T010 frontend/src/pages/RoomListPage.test.tsx

# Then frontend T016 + T017 in parallel, while backend runs T011 → T012 → T013 → T014 → T015.
```

### Parallel Example: User Story 3

```text
T036 CatalogRepositoryIntegrationTest | T037 RoomBarrierFreeTest | T038 BuildingControllerTest
T039 FloorControllerTest | T040 RoomControllerTest + RoomServiceTest | T043 BuildingCatalog.test.tsx | T044 RoomForm.test.tsx
```

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3 (T001–T020).
2. **Stop and validate** quickstart §4 scenarios 1–5 and 10. Demo the result, and optionally open a PR into `dev`.

### Incremental Delivery

1. MVP (US1): search by persons and building.
2. + US2: seating arrangement and equipment.
3. + US3: barrier-free, including the catalog and room form fields. This is the first story with a DB migration.
4. + US4: availability and booking pre-fill.
5. Polish: contracts, README, full validation.

Each increment keeps all previous stories green.

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- Verify that each test fails before implementing (Constitution I). Reviewers must be able to see the red → green step in the commit history.
- New request fields are always optional. On PUT, null means "unchanged" (Constitution III, research.md R6).
- Do not add dependencies. Do not touch `SecurityConfig`: the endpoints are covered by `anyRequest().authenticated()`.
