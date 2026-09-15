# Tasks: Room Management

**Input**: Design documents from `/specs/001-room-management/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md

**Tests**: Included and sequenced before their corresponding implementation task in every phase.
Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project — every
test task below MUST be written and observed to fail before its paired implementation task begins.

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent
implementation and testing of each story. Building, Floor, and Equipment Type catalog
management (FR-014–FR-023) are cross-cutting prerequisites every room story depends on, so
they live in the Foundational phase rather than under any single user story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4) — omitted for
  Setup/Foundational/Polish tasks
- Include exact file paths in descriptions

## Path Conventions

Web app split per `plan.md`: `backend/src/main/java/at/mci/igp/raumlotse/`, `backend/src/test/java/at/mci/igp/raumlotse/`, `frontend/src/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Add the dependencies and tooling this feature needs that aren't in the repo yet (see `research.md`).

- [X] T001 Add `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `flyway-core`, and `flyway-database-postgresql` dependencies to `backend/pom.xml` (research.md §1–2)
- [X] T002 [P] Add `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` test-scoped dependencies to `backend/pom.xml` (research.md §4)
- [X] T003 [P] Add `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, and `jsdom` devDependencies plus a `test` script to `frontend/package.json` (research.md §5)
- [X] T004 [P] Configure Vitest in `frontend/vite.config.ts` (`test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'] }`) and create `frontend/src/test/setup.ts` importing `@testing-library/jest-dom`
- [X] T005 [P] Add local datasource defaults to `backend/src/main/resources/application.yaml` (`spring.datasource.url=jdbc:postgresql://localhost:5432/raumlotse`, username/password `raumlotse`/`raumlotse`, matching `docker-compose.yml`) so `./mvnw spring-boot:run` works against `docker compose up -d db`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema, domain entities, and the full Building/Floor/Equipment Type
catalog stack (backend + frontend). No room can be created, viewed, updated, or removed
without these, and no user story below is independently testable until this phase is done.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Schema

- [X] T006 Create Flyway migration `backend/src/main/resources/db/migration/V1__create_building_and_floor_tables.sql`: `building` table (`id UUID PK`, `name TEXT NOT NULL`, `status TEXT NOT NULL DEFAULT 'ACTIVE'`, unique index on `lower(name)` per FR-023); `floor` table (`id UUID PK`, `building_id UUID NOT NULL REFERENCES building(id)`, `name TEXT NOT NULL`, `status TEXT NOT NULL DEFAULT 'ACTIVE'`, unique index on `(building_id, lower(name))` per FR-023)
- [X] T007 Create Flyway migration `backend/src/main/resources/db/migration/V2__create_room_tables.sql`: `room` table (`id UUID PK`, `name TEXT NOT NULL`, `floor_id UUID NOT NULL REFERENCES floor(id)`, `status TEXT NOT NULL DEFAULT 'ACTIVE'`, `version BIGINT NOT NULL DEFAULT 0`, `created_at`/`updated_at`); `seating_arrangement` table (`id UUID PK`, `room_id UUID NOT NULL REFERENCES room(id) ON DELETE CASCADE`, `name TEXT NOT NULL`, `max_capacity INT NOT NULL CHECK (max_capacity > 0)` per FR-004/FR-007, unique index on `(room_id, lower(name))`)
- [X] T008 Create Flyway migration `backend/src/main/resources/db/migration/V3__create_equipment_type_tables.sql`: `equipment_type` table (`id UUID PK`, `name TEXT NOT NULL`, `status TEXT NOT NULL DEFAULT 'ACTIVE'`, unique index on `lower(name)` per FR-023); `room_equipment` join table (`room_id UUID REFERENCES room(id) ON DELETE CASCADE`, `equipment_type_id UUID REFERENCES equipment_type(id)`, composite PK); seed rows for `'Projector'` and `'Whiteboard'` (research.md §8)

### Domain entities

- [X] T009 [P] Create shared `EntityStatus` enum (`ACTIVE`, `DEACTIVATED`) in `backend/src/main/java/at/mci/igp/raumlotse/domain/EntityStatus.java`, reused by Room, Building, Floor, and EquipmentType
- [X] T010 [P] Create `Building` JPA entity in `backend/src/main/java/at/mci/igp/raumlotse/domain/Building.java` — `name`: "Required, non-empty, unique across all buildings (case-insensitive) (FR-023)"; `status`: "Defaults to ACTIVE on creation"
- [X] T011 [P] Create `Floor` JPA entity in `backend/src/main/java/at/mci/igp/raumlotse/domain/Floor.java` — `buildingId`: "Required, immutable after creation — a floor cannot be moved to a different building"; `name`: "Required, non-empty, unique within its building (case-insensitive) (FR-023)"; `status`: "Defaults to ACTIVE on creation. Effectively inactive if either the floor itself or its building is DEACTIVATED"
- [X] T012 [P] Create `EquipmentType` JPA entity in `backend/src/main/java/at/mci/igp/raumlotse/domain/EquipmentType.java` — `name`: "Required, non-empty, unique across the catalog (case-insensitive) (FR-023)"; `status`: "Defaults to ACTIVE on creation"
- [X] T013 [P] Create `Room` JPA entity in `backend/src/main/java/at/mci/igp/raumlotse/domain/Room.java` — `name`: "Required, non-empty (FR-007). Unique (case-insensitively) together with the building reached via floor.building (FR-013)"; `floorId`: "Required (FR-020)"; `status`: defaults ACTIVE; `version`: JPA `@Version` field for optimistic-locking conflict detection (FR-017)
- [X] T014 [P] Create `SeatingArrangement` JPA entity in `backend/src/main/java/at/mci/igp/raumlotse/domain/SeatingArrangement.java` — `name`: "Required, non-empty (FR-007). Unique within its room"; `maxCapacity`: "Required, > 0 (FR-004, FR-007)"
- [X] T015 Wire the `Room` ↔ `EquipmentType` many-to-many relationship via the `room_equipment` join table (`@ManyToMany` + `@JoinTable`) on `Room` (FR-015) (depends on T012, T013)

### Repositories

- [X] T016 [P] Create `BuildingRepository` in `backend/src/main/java/at/mci/igp/raumlotse/repository/BuildingRepository.java` (`existsByNameIgnoreCase`, `findByStatus`)
- [X] T017 [P] Create `FloorRepository` in `backend/src/main/java/at/mci/igp/raumlotse/repository/FloorRepository.java` (`existsByBuildingIdAndNameIgnoreCase`, `findByBuildingId`, `countByBuildingId` for the FR-022 building-delete guard)
- [X] T018 [P] Create `EquipmentTypeRepository` in `backend/src/main/java/at/mci/igp/raumlotse/repository/EquipmentTypeRepository.java` (`existsByNameIgnoreCase`, `findByStatus`)
- [X] T019 [P] Create `RoomRepository` in `backend/src/main/java/at/mci/igp/raumlotse/repository/RoomRepository.java` (`existsByFloor_Building_IdAndNameIgnoreCase` for FR-013, `findByStatus`, `existsByFloorId` for the FR-022 floor-delete guard)
- [X] T020 [P] Create `SeatingArrangementRepository` in `backend/src/main/java/at/mci/igp/raumlotse/repository/SeatingArrangementRepository.java`

### Cross-cutting error handling & test infra

- [X] T021 [P] Create `ConflictException` (maps to 409) in `backend/src/main/java/at/mci/igp/raumlotse/exception/ConflictException.java`
- [X] T022 [P] Create `NotFoundException` (maps to 404) in `backend/src/main/java/at/mci/igp/raumlotse/exception/NotFoundException.java`
- [X] T023 Create `GlobalExceptionHandler` (`@RestControllerAdvice`) in `backend/src/main/java/at/mci/igp/raumlotse/exception/GlobalExceptionHandler.java` mapping `ConflictException`→409, `NotFoundException`→404, `MethodArgumentNotValidException`→400, `OptimisticLockingFailureException`→409, `DataIntegrityViolationException`→409 (catches unique-constraint violations not already caught by an application-level pre-check, e.g. duplicate seating-arrangement name within a room), producing the `Problem` shape from `contracts/openapi.yaml` (depends on T021, T022)
- [X] T024 [P] Create a shared Testcontainers base class `AbstractIntegrationTest` in `backend/src/test/java/at/mci/igp/raumlotse/AbstractIntegrationTest.java` (`@Testcontainers`, PostgreSQL 17-alpine, per research.md §4)

### Equipment Type catalog (FR-014, FR-015, FR-016, FR-023)

- [X] T025 [P] Create `EquipmentTypeRequest`/`EquipmentTypeResponse` DTOs in `backend/src/main/java/at/mci/igp/raumlotse/dto/EquipmentTypeRequest.java` and `EquipmentTypeResponse.java`, matching `contracts/openapi.yaml` `EquipmentTypeRequest`/`EquipmentType` schemas
- [X] T026 [P] Write MockMvc contract test `backend/src/test/java/at/mci/igp/raumlotse/controller/EquipmentTypeControllerTest.java` covering create (201), duplicate name case-insensitive (409, FR-023), list, rename, deactivate, reactivate, and delete-blocked-while-assigned (409, FR-016) — must fail before T027/T028 exist
- [X] T027 Implement `EquipmentTypeService` in `backend/src/main/java/at/mci/igp/raumlotse/service/EquipmentTypeService.java`: create/rename (non-empty name, case-insensitive uniqueness FR-023), deactivate/reactivate (FR-014), delete (throws `ConflictException` if any room references it, FR-016) (depends on T018, T021, T025, T026)
- [X] T028 Implement `EquipmentTypeController` in `backend/src/main/java/at/mci/igp/raumlotse/controller/EquipmentTypeController.java` exposing the `/equipment-types` paths from `contracts/openapi.yaml` (depends on T027)

### Building catalog (FR-018, FR-021, FR-022, FR-023)

- [X] T029 [P] Create `BuildingRequest`/`BuildingResponse` DTOs in `backend/src/main/java/at/mci/igp/raumlotse/dto/BuildingRequest.java` and `BuildingResponse.java`, matching `contracts/openapi.yaml` `BuildingRequest`/`Building` schemas
- [X] T030 [P] Write MockMvc contract test `backend/src/test/java/at/mci/igp/raumlotse/controller/BuildingControllerTest.java` covering create (201), duplicate name (409, FR-023), rename, deactivate (200, cascades to floors), reactivate (200, does not cascade), and delete-blocked-while-it-has-floors (409, FR-022) — must fail before T031/T032 exist
- [X] T031 Implement `BuildingService` in `backend/src/main/java/at/mci/igp/raumlotse/service/BuildingService.java`: create/rename (non-empty name, case-insensitive uniqueness FR-023), deactivate (cascades to deactivate every floor under this building in the same transaction, FR-021/research.md §7), reactivate (does NOT cascade to floors, FR-021), delete (throws `ConflictException` if the building has any floor, active or deactivated, FR-022) (depends on T016, T017, T021, T029, T030)
- [X] T032 Implement `BuildingController` in `backend/src/main/java/at/mci/igp/raumlotse/controller/BuildingController.java` exposing the `/buildings` paths from `contracts/openapi.yaml` (depends on T031)

### Floor catalog (FR-019, FR-021, FR-022, FR-023)

- [X] T033 [P] Create `FloorRequest`/`FloorResponse` DTOs in `backend/src/main/java/at/mci/igp/raumlotse/dto/FloorRequest.java` and `FloorResponse.java`, matching `contracts/openapi.yaml` `FloorRequest`/`Floor` schemas
- [X] T034 [P] Write MockMvc contract test `backend/src/test/java/at/mci/igp/raumlotse/controller/FloorControllerTest.java` covering create-under-building (201), duplicate name within building (409, FR-023), rename, deactivate, reactivate-blocked-while-building-deactivated (409, FR-021), and delete-blocked-while-referenced-by-a-room (409, FR-022) — must fail before T035/T036 exist
- [X] T035 Implement `FloorService` in `backend/src/main/java/at/mci/igp/raumlotse/service/FloorService.java`: create under an active building (non-empty name, case-insensitive uniqueness within building, FR-019/FR-023), rename, deactivate, reactivate (throws `ConflictException` if the parent building is still `DEACTIVATED`, FR-021), delete (throws `ConflictException` if any room references it, FR-022) (depends on T017, T019, T021, T033, T034)
- [X] T036 Implement `FloorController` in `backend/src/main/java/at/mci/igp/raumlotse/controller/FloorController.java` exposing the `/buildings/{buildingId}/floors` and `/floors/{floorId}` paths from `contracts/openapi.yaml` (depends on T035)

### Real-database verification

- [X] T037 [P] Write Testcontainers integration test `backend/src/test/java/at/mci/igp/raumlotse/repository/CatalogRepositoryIntegrationTest.java` verifying the case-insensitive uniqueness constraints (building name, floor name within building, equipment type name) and the building-deactivation-cascades-to-floors behavior against real PostgreSQL (depends on T024, T031, T035)

### Frontend catalog UI

- [X] T038 [P] Create shared TS types (`Room`, `SeatingArrangement`, `Building`, `Floor`, `EquipmentType`, status union) in `frontend/src/types/room.ts`, matching `contracts/openapi.yaml` schemas
- [X] T039 [P] Create `buildings.ts` API client in `frontend/src/API/buildings.ts` (list/create/rename/deactivate/reactivate/delete against `/api/buildings`)
- [X] T040 [P] Create `floors.ts` API client in `frontend/src/API/floors.ts` (list-by-building/create/rename/deactivate/reactivate/delete against `/api/buildings/{id}/floors` and `/api/floors/{id}`)
- [X] T041 [P] Create `equipmentTypes.ts` API client in `frontend/src/API/equipmentTypes.ts` (list/create/rename/deactivate/delete against `/api/equipment-types`)
- [X] T042 [P] Write RTL component test `frontend/src/components/BuildingCatalog/BuildingCatalog.test.tsx`: create a building, add a floor under it, rename each, deactivate a building and confirm its floor shows deactivated, delete blocked while floors exist — must fail before T043 exists
- [X] T043 Implement `BuildingCatalog` component in `frontend/src/components/BuildingCatalog/BuildingCatalog.tsx` (list buildings + their floors; create/rename/deactivate/reactivate/delete for both; reusable from `LocationCatalogPage` and inline from `RoomForm`'s "add building/floor" flow) (depends on T039, T040, T042)
- [X] T044 [P] Write RTL component test `frontend/src/components/EquipmentCatalog/EquipmentCatalog.test.tsx`: create/rename/deactivate an equipment type, delete blocked with an explanatory message when assigned — must fail before T045 exists
- [X] T045 Implement `EquipmentCatalog` component in `frontend/src/components/EquipmentCatalog/EquipmentCatalog.tsx` (depends on T041, T044)
- [X] T046 Create `LocationCatalogPage` in `frontend/src/pages/LocationCatalogPage.tsx` composing `BuildingCatalog` + `EquipmentCatalog`, and register its route in `frontend/src/App.tsx` (depends on T043, T045)

**Checkpoint**: Foundation ready — Building, Floor, and Equipment Type catalogs are fully
manageable end-to-end (backend + UI). Room user stories can now be implemented.

---

## Phase 3: User Story 1 - Create a new room (Priority: P1) 🎯 MVP

**Goal**: An administrator can create a room with a name, an existing (or inline-created)
active building/floor, optional equipment, and at least one seating arrangement.

**Independent Test**: Submit a new room with a valid name, building, floor, and at least one
seating arrangement; verify it appears in the room list with a unique ID and "active" status.

### Tests for User Story 1 ⚠️

- [X] T047 [P] [US1] Write MockMvc contract test `backend/src/test/java/at/mci/igp/raumlotse/controller/RoomControllerTest.java` (create scenarios): 201 with seating arrangements + equipment, 400 for missing name/floor/zero seating arrangements (FR-001, FR-003, FR-007), 409 for a duplicate name within the same building (FR-013), 409 for two seating arrangements with the same name (data-model.md, `DataIntegrityViolationException` mapping) — must fail before T051/T052 exist
- [X] T048 [P] [US1] Write Testcontainers integration test `backend/src/test/java/at/mci/igp/raumlotse/RoomCreationIntegrationTest.java` covering the full room-creation happy path and the FR-013 duplicate-name rejection against real PostgreSQL — must fail before T051 exists
- [X] T049 [P] [US1] Write RTL component test `frontend/src/components/RoomForm/RoomForm.test.tsx` (create mode): fill name, select building/floor, add a seating arrangement, toggle equipment, submit; validation error when no seating arrangement is present; inline "create building/floor" flow when none exist yet (Acceptance Scenario 5) — must fail before T054 exists

### Implementation for User Story 1

- [X] T050 [US1] Create `RoomRequest`/`RoomResponse` and `SeatingArrangementRequest`/`SeatingArrangementResponse` DTOs in `backend/src/main/java/at/mci/igp/raumlotse/dto/RoomRequest.java`, `RoomResponse.java`, `SeatingArrangementRequest.java`, `SeatingArrangementResponse.java`, matching `contracts/openapi.yaml` `RoomCreateRequest`/`Room`/`SeatingArrangement` schemas
- [X] T051 [US1] Implement `RoomService.create()` in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomService.java`: non-empty name (FR-007); ≥1 seating arrangement, each with non-empty name and `maxCapacity > 0` (FR-003, FR-004, FR-007); `floorId` must reference an `ACTIVE` floor whose building is also `ACTIVE` (FR-020); reject any `equipmentTypeId` that is deactivated (FR-015); reject a name colliding case-insensitively with another room in the same building (FR-013); default status `ACTIVE` (FR-001) (depends on T013, T014, T019, T050, T047, T048)
- [X] T052 [US1] Implement `RoomController.create()` (`POST /api/rooms`) in `backend/src/main/java/at/mci/igp/raumlotse/controller/RoomController.java` per `contracts/openapi.yaml` (depends on T051)
- [X] T053 [P] [US1] Create `rooms.ts` API client with `createRoom` in `frontend/src/API/rooms.ts`, matching `contracts/openapi.yaml` `POST /rooms`
- [X] T054 [US1] Implement `RoomForm` component (create mode) in `frontend/src/components/RoomForm/RoomForm.tsx`: name field; building/floor selectors reusing `BuildingCatalog`'s inline-create affordance; equipment picker sourced from `EquipmentCatalog` data; seating-arrangement editor enforcing ≥1 row client-side (FR-003) (depends on T038, T043, T045, T053, T049)
- [X] T055 [US1] Create `RoomFormPage` (create mode) in `frontend/src/pages/RoomFormPage.tsx` and register its route in `frontend/src/App.tsx` (depends on T054)

**Checkpoint**: User Story 1 is fully functional and independently testable — rooms can be
created end-to-end via both the API and the UI.

---

## Phase 4: User Story 2 - View and configure existing rooms (Priority: P2)

**Goal**: An administrator can list rooms and open one to see its full configuration.

**Independent Test**: Open a previously created room's detail view and confirm all stored
attributes are displayed accurately.

### Tests for User Story 2 ⚠️

- [X] T056 [P] [US2] Write MockMvc contract test in `backend/src/test/java/at/mci/igp/raumlotse/controller/RoomControllerTest.java` (list/get scenarios): 200 list with `status` filter (FR-011), 200 detail with full configuration (FR-005), 404 for an unknown id — must fail before T058/T059 exist
- [X] T057 [P] [US2] Write RTL component test `frontend/src/pages/RoomListPage.test.tsx`: renders rooms with name/building/floor/status, active/deactivated filter — must fail before T061 exists

### Implementation for User Story 2

- [X] T058 [US2] Implement `RoomService.list()`/`get()` in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomService.java`: list with optional status filter (FR-011); get full detail by id, throwing `NotFoundException` when missing (FR-005) (depends on T019, T056)
- [X] T059 [US2] Implement `RoomController.list()`/`get()` (`GET /api/rooms`, `GET /api/rooms/{id}`) in `backend/src/main/java/at/mci/igp/raumlotse/controller/RoomController.java` (depends on T058)
- [X] T060 [P] [US2] Extend `rooms.ts` with `listRooms`/`getRoom` in `frontend/src/API/rooms.ts`
- [X] T061 [US2] Implement `RoomListPage` in `frontend/src/pages/RoomListPage.tsx` (name/building/floor/status columns, active/deactivated filter per FR-011) and register its route in `frontend/src/App.tsx` (depends on T060, T057)
- [X] T062 [US2] Add a full detail view (equipment, seating arrangements, capacities) to `RoomFormPage` in `frontend/src/pages/RoomFormPage.tsx` when opened for an existing room (depends on T060, T054)

**Checkpoint**: Rooms can be listed and inspected in full detail.

---

## Phase 5: User Story 3 - Update an existing room (Priority: P2)

**Goal**: An administrator can edit an existing room's attributes; a stale concurrent update is
rejected instead of silently overwriting another administrator's change.

**Independent Test**: Change one or more attributes of an existing room and verify the update
persists; verify a save based on stale data is rejected with a conflict error.

### Tests for User Story 3 ⚠️

- [X] T063 [P] [US3] Write MockMvc contract test in `backend/src/test/java/at/mci/igp/raumlotse/controller/RoomControllerTest.java` (update scenarios): 200 with incremented `version`, 400 when seating arrangements are emptied (FR-003), 409 for a stale `version` (FR-017), 409 for a colliding name (FR-013) — must fail before T066/T067 exist
- [X] T064 [P] [US3] Write Testcontainers integration test `backend/src/test/java/at/mci/igp/raumlotse/RoomConcurrentUpdateIntegrationTest.java` simulating two updates from the same stale read, asserting the first succeeds and the second is rejected with 409 (FR-017, Acceptance Scenario US3.3) — must fail before T066 exists
- [X] T065 [P] [US3] Write RTL component test `frontend/src/components/RoomForm/RoomForm.test.tsx` (edit mode): loads an existing room, edits and saves, shows a conflict message on a stale save — must fail before T069 exists

### Implementation for User Story 3

- [X] T066 [US3] Implement `RoomService.update()` in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomService.java`: same validation as create (FR-006, FR-007), including that `floorId` must reference an `ACTIVE` floor whose building is also `ACTIVE` (FR-020) and no assigned `equipmentTypeId` may be deactivated (FR-015); re-check name uniqueness excluding the room itself (FR-013); enforce ≥1 seating arrangement (FR-003); rely on the `@Version` optimistic lock, translating `OptimisticLockingFailureException` to `ConflictException` (FR-017) (depends on T051, T063, T064)
- [X] T067 [US3] Implement `RoomController.update()` (`PUT /api/rooms/{id}`) in `backend/src/main/java/at/mci/igp/raumlotse/controller/RoomController.java`, requiring `version` in the request body (depends on T066)
- [X] T068 [P] [US3] Extend `rooms.ts` with `updateRoom` (including `version`) in `frontend/src/API/rooms.ts`
- [X] T069 [US3] Wire edit mode into `RoomForm`/`RoomFormPage`: pre-fill from the loaded room, submit via `updateRoom` with the loaded `version`, surface a conflict message on 409 (FR-017) (depends on T068, T062, T065)

**Checkpoint**: Rooms can be edited safely, with concurrent-edit protection in place.

---

## Phase 6: User Story 4 - Deactivate or delete a room (Priority: P3)

**Goal**: An administrator can deactivate/reactivate a room, or permanently delete one that has
no dependent history.

**Independent Test**: Deactivate an existing room and confirm it is excluded from the active
list but still retrievable; delete a room with no dependent history and confirm it is gone.

### Tests for User Story 4 ⚠️

- [X] T070 [P] [US4] Write MockMvc contract test in `backend/src/test/java/at/mci/igp/raumlotse/controller/RoomControllerTest.java` (lifecycle scenarios): 200 deactivate excludes the room from the active list (FR-008), 200 reactivate restores it (FR-009), 204 delete when there is no dependent history (FR-010) — must fail before T072/T073 exist
- [X] T070a [P] [US4] Write a `RoomService` unit test in `backend/src/test/java/at/mci/igp/raumlotse/service/RoomServiceTest.java` with a mocked dependent-history check returning `true`, asserting `delete()` throws `ConflictException` (FR-010) — this scenario can't be exercised end-to-end yet since no feature currently produces dependent history (see spec.md Assumptions) — must fail before T072 exists
- [X] T071 [P] [US4] Write RTL component test additions to `frontend/src/pages/RoomListPage.test.tsx`: deactivate hides a room from the active filter, reactivate restores it, delete removes it — must fail before T075 exists

### Implementation for User Story 4

- [X] T072 [US4] Implement `RoomService.deactivate()`/`reactivate()`/`delete()` in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomService.java`: deactivate/reactivate flip status while preserving configuration (FR-008, FR-009); delete checks for dependent history (currently always absent per the Assumptions in spec.md, but implemented as a real guard so it activates automatically once another feature references rooms) before removing the row, otherwise throws `ConflictException` (FR-010) (depends on T058, T070, T070a)
- [X] T073 [US4] Implement `RoomController` deactivate/reactivate/delete endpoints (`POST /api/rooms/{id}/deactivate`, `POST /api/rooms/{id}/reactivate`, `DELETE /api/rooms/{id}`) in `backend/src/main/java/at/mci/igp/raumlotse/controller/RoomController.java` (depends on T072)
- [X] T074 [P] [US4] Extend `rooms.ts` with `deactivateRoom`/`reactivateRoom`/`deleteRoom` in `frontend/src/API/rooms.ts`
- [X] T075 [US4] Add deactivate/reactivate/delete actions to `RoomListPage` and `RoomFormPage` in `frontend/src/pages/RoomListPage.tsx` and `frontend/src/pages/RoomFormPage.tsx` (depends on T074, T061, T062, T071)

**Checkpoint**: All four user stories are independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Verification and hardening that spans every story.

- [X] T076 [P] Run `./mvnw test` (backend) and `npm run test` (frontend); fix any failures across all stories
- [X] T077 [P] Run `npm run lint` and fix violations per constitution Principle II
- [ ] T078 Execute the `quickstart.md` validation walkthrough end-to-end (`docker compose up -d db pgadmin`, `./mvnw spring-boot:run`, `npm run dev`) and fix any discrepancies found
- [X] T079 [P] Verify `GlobalExceptionHandler` produces structured, greppable log output for request failures per constitution Principle V, confirmed via `docker compose logs`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS every user story — Building/Floor/Equipment Type must exist before any room can reference them.
- **User Stories (Phase 3–6)**: All depend on Foundational completion.
  - US1 (Phase 3) has no dependency on the other room stories.
  - US2 (Phase 4) reuses `RoomService`/`RoomController` scaffolding from US1 but its list/get logic is additive, not a modification of US1's create path.
  - US3 (Phase 5) extends `RoomService`/`RoomController` with `update()`; depends on the `Room` entity and DTOs from US1 (T050, T051) existing, but is a separate, independently testable slice.
  - US4 (Phase 6) extends `RoomService`/`RoomController` with lifecycle operations; depends on US2's `RoomService.list()` (T058) for the "excluded from active list" check, but is otherwise independent.
  - In practice, ship in priority order (US1 → US2 → US3 → US4); US2–US4 are not meaningfully demoable before US1 exists.
- **Polish (Phase 7)**: Depends on all four user stories being complete.

### Within Each Phase

- Tests MUST be written and observed to FAIL before their paired implementation task (constitution Principle I).
- Entities/DTOs before services; services before controllers; backend endpoints before the frontend API client that calls them; API client before the UI component that uses it.

### Parallel Opportunities

- All Setup tasks marked [P] can run together.
- Within Foundational: all domain-entity tasks (T009–T014), all repository tasks (T016–T020), and the three catalogs' DTO/contract-test pairs (T025–T026, T029–T030, T033–T034) can run in parallel with each other, though each catalog's own service must wait on its DTOs + contract test.
- Once Foundational is done, US1–US4 backend work can proceed in parallel by different developers (each touches `RoomService`/`RoomController` in different methods); frontend work within a story should follow its own backend endpoints.
- All contract/integration/component test tasks marked [P] within a phase can run in parallel with each other (different files).

---

## Parallel Example: Foundational Phase

```bash
# Launch all domain entities together:
Task: "Create shared EntityStatus enum in backend/.../domain/EntityStatus.java"
Task: "Create Building JPA entity in backend/.../domain/Building.java"
Task: "Create Floor JPA entity in backend/.../domain/Floor.java"
Task: "Create EquipmentType JPA entity in backend/.../domain/EquipmentType.java"
Task: "Create Room JPA entity in backend/.../domain/Room.java"
Task: "Create SeatingArrangement JPA entity in backend/.../domain/SeatingArrangement.java"

# Launch all repositories together (after entities exist):
Task: "Create BuildingRepository in backend/.../repository/BuildingRepository.java"
Task: "Create FloorRepository in backend/.../repository/FloorRepository.java"
Task: "Create EquipmentTypeRepository in backend/.../repository/EquipmentTypeRepository.java"
Task: "Create RoomRepository in backend/.../repository/RoomRepository.java"
Task: "Create SeatingArrangementRepository in backend/.../repository/SeatingArrangementRepository.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories; this is also where most of
   the Building/Floor/Equipment Type functional requirements are actually delivered).
3. Complete Phase 3: User Story 1.
4. **STOP and VALIDATE**: run the relevant parts of `quickstart.md` for room creation.
5. Deploy/demo if ready — this alone delivers a usable room catalog.

### Incremental Delivery

1. Setup + Foundational → catalogs fully manageable, foundation ready.
2. Add US1 → test independently → deploy/demo (MVP!).
3. Add US2 → test independently → deploy/demo.
4. Add US3 → test independently → deploy/demo.
5. Add US4 → test independently → deploy/demo.
6. Polish.

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together (the three catalogs can be split one-per-developer within Phase 2, since Equipment Type/Building/Floor are independent of each other).
2. Once Foundational is done, US1 should land first (it defines `RoomService`/`RoomController`/`RoomForm` that US2–US4 extend); US2–US4 can then be split across developers, coordinating on the shared files noted above.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps a task to its user story for traceability; Setup/Foundational/Polish tasks carry no [Story] label by design.
- Constitution Principle I is NON-NEGOTIABLE: every test task must exist and fail before its paired implementation task is written.
- Commit after each task or logical group.
- Stop at any checkpoint to validate a story independently.
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break independence.

---

## Phase 8: Convergence

**Purpose**: Gaps found by `/speckit-converge` between the code as implemented and the intent
in `spec.md`/`plan.md`/`tasks.md`. See the Convergence Findings this phase was appended from
for full evidence.

- [X] T080 Reject creating a floor under a deactivated building in `FloorService.create()` (backend/src/main/java/at/mci/igp/raumlotse/service/FloorService.java) — check `building.getStatus() == ACTIVE` and throw `IllegalArgumentException` (400) otherwise per FR-019 (missing)
- [X] T081 Return 404 instead of an empty list from `FloorService.listByBuilding()` (backend/src/main/java/at/mci/igp/raumlotse/service/FloorService.java) when the given `buildingId` does not reference an existing building, per contracts/openapi.yaml `GET /buildings/{buildingId}/floors` (partial)
- [X] T082 Rewrite the JPQL in `EquipmentTypeRepository.isAssignedToAnyRoom()` (backend/src/main/java/at/mci/igp/raumlotse/repository/EquipmentTypeRepository.java) from `select count(r) > 0 ...` to the standard `select case when count(r) > 0 then true else false end ...` form, and add a repository test exercising it once DB/Docker access is available, per FR-016 (partial)
- [X] T083 [P] Surface backend per-field validation messages (`Problem.errors[]`) alongside the general error message in `RoomForm`, `BuildingCatalog`, and `EquipmentCatalog` (frontend/src/components/) error displays, per SC-005 (partial)
