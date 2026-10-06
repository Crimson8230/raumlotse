# Tasks: Room Map Placement

**Input**: Design documents from `/specs/012-room-map-placement/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [API contract](./contracts/room-map-api.yaml), [quickstart.md](./quickstart.md)

**Tests**: Required by the project constitution (Principle I). Every test task must be written first, observed failing, and then made green by the implementation tasks (Red-Green-Refactor).

**Organization**: Tasks are grouped by user story. Story numbers follow spec.md (US1–US5). Because a room can only be placed on an existing map, **US3 (Manage Maps) is implemented before US1** even though both are P1; US1 and US2 then build on it.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- Backend base path: `backend/src/main/java/at/mci/igp/raumlotse/` (abbrev. `…/raumlotse/`); backend tests: `backend/src/test/java/at/mci/igp/raumlotse/`
- Frontend base path: `frontend/src/`

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 [P] Verify existing backend test fixtures (Testcontainers `AbstractIntegrationTest`, authenticated admin/non-admin accounts, building/floor/room builders) in `backend/src/test/java/at/mci/igp/raumlotse/` and note any needed extensions in `specs/012-room-map-placement/quickstart.md`
- [X] T002 [P] Verify existing frontend test helpers (API client mocking, auth provider, route tests) in `frontend/src/API/client.ts`, `frontend/src/auth/`, and `frontend/src/test/`
- [X] T003 [P] Validate `specs/012-room-map-placement/contracts/room-map-api.yaml` parses as OpenAPI 3.0.3 and that every status code in it has a matching backend task below

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, entities, admin-only write enforcement, and upload limits required by every story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 [P] Add failing migration test asserting V13 creates `floor_map`, `room_placement`, `connection`, `connection_point` with the constraints from data-model.md (unique `floor_map.floor_id`; `room_placement.room_id` PK; `x`,`y` `NUMERIC(6,5)` with `CHECK 0 ≤ v ≤ 1`; `connection.type` `CHECK IN ('STAIRS','ELEVATOR')`; `UNIQUE (connection_id, map_id)`; `content_type` CHECK `image/png`|`image/jpeg`) in `backend/src/test/java/at/mci/igp/raumlotse/MapMigrationIntegrationTest.java`; extend `MigrationVersionTest.java` if it pins the latest version
- [X] T005 [P] Add failing tests that non-GET requests under `/api/maps/**`, `/api/floors/{id}/map`, and `/api/connections/**` return 401 unauthenticated and 403 for non-admins (Problem body, no handler executed), while GETs remain allowed for any signed-in user, in `backend/src/test/java/at/mci/igp/raumlotse/MapAuthorizationIntegrationTest.java`
- [X] T006 Add Flyway migration `backend/src/main/resources/db/migration/V13__create_room_map_tables.sql` exactly per [data-model.md](./data-model.md): `floor_map` (`id UUID PK`, `floor_id UUID NOT NULL UNIQUE → floor(id)`, `image BYTEA NOT NULL`, `content_type`, `width_px`/`height_px INT > 0`, `image_version BIGINT NOT NULL DEFAULT 1`, `version`, timestamps), `room_placement` (`room_id` PK → `room(id) ON DELETE CASCADE`, `map_id → floor_map(id) ON DELETE CASCADE`, `x`,`y` `NUMERIC(6,5)`, no version column, index on `map_id`), `connection` (`name TEXT NOT NULL` with unique `lower(name)` index, `type` CHECK), `connection_point` (`connection_id`/`map_id` both `ON DELETE CASCADE`, `x`,`y`, `UNIQUE (connection_id, map_id)`)
- [X] T007 [P] Create JPA entities `FloorMap`, `RoomPlacement`, `Connection`, `ConnectionPoint` and enum `ConnectionType {STAIRS, ELEVATOR}` in `…/raumlotse/domain/` following the style of `Floor.java`/`Room.java` (UUID ids, `@Version`, `@Enumerated(EnumType.STRING)`)
- [X] T008 Create repositories `FloorMapRepository`, `RoomPlacementRepository`, `ConnectionRepository`, `ConnectionPointRepository` in `…/raumlotse/repository/` (after T007; include `findByFloorId`, `existsByFloorId`, placements by `mapId`, rooms of a floor without placement, connections having a point on a map, case-insensitive name lookups)
- [X] T009 Extend `…/raumlotse/service/RoleAccessFilter.java` `shouldNotFilter` so it additionally applies `requireAdmin` to non-GET/HEAD/OPTIONS requests on `/api/maps/**`, `/api/floors/*/map`, and `/api/connections/**` (existing `/api/admin/users` behavior unchanged); make T005 pass
- [X] T010 [P] Add multipart limits `spring.servlet.multipart.max-file-size=10MB` and `max-request-size=10MB` to `backend/src/main/resources/application.yaml` and map `MaxUploadSizeExceededException` to a 413 Problem (`code: MAP_IMAGE_TOO_LARGE`) in `…/raumlotse/exception/GlobalExceptionHandler.java`
- [X] T011 [P] Add DTO records in `…/raumlotse/dto/`: `MapSummaryResponse`, `MapDetailResponse`, `PlacementResponse`, `RoomRefResponse`, `PositionRequest` (`x`,`y` annotated `@NotNull @DecimalMin("0.0") @DecimalMax("1.0")`), `ConnectionRequest` (`name` `@NotBlank @Size(max=100)`, `type` `@NotNull`), `ConnectionResponse`, `ConnectionPointResponse`
- [X] T012 [P] Add TypeScript types in `frontend/src/types/map.ts` mirroring the contract (`MapSummary`, `MapDetail`, `Placement`, `RoomRef`, `Connection`, `ConnectionPoint`, `Position`, `ConnectionType`)

**Checkpoint**: Schema migrates, entities load, admin-only write enforcement works.

---

## Phase 3: User Story 3 - Manage Maps (Priority: P1) 🎯 prerequisite of US1

**Goal**: Admins create a map for a floor from a PNG/JPEG, replace its image, delete it; everyone signed in can list and view maps.

**Independent Test**: Upload a PNG for floor A and floor B, list both, switch between them, replace A's image with another resolution and verify metadata, upload a `.txt`/SVG/oversized file and verify rejection with the map unchanged, delete a map.

### Tests for User Story 3 (write first, must fail)

- [X] T013 [P] [US3] Add failing unit tests for `MapImageValidator` (accepts PNG/JPEG, derives width/height and content type from decoded content not client header, rejects SVG/text/corrupt data, rejects > 10 MB) in `backend/src/test/java/at/mci/igp/raumlotse/service/MapImageValidatorTest.java`
- [X] T014 [P] [US3] Add failing service tests for `FloorMapService` (create for existing floor → 201 semantics, second upload replaces and increments `imageVersion`, `aspectRatioChanged` when ratio differs, unknown floor → NotFound, delete cascades) in `backend/src/test/java/at/mci/igp/raumlotse/service/FloorMapServiceTest.java`
- [X] T014a [P] [US3] Add failing test that deleting a floor that has a map (and no rooms) is rejected with 409 and a clear message in `backend/src/test/java/at/mci/igp/raumlotse/service/FloorServiceMapGuardTest.java`
- [X] T015 [P] [US3] Add failing API tests for `GET /api/maps`, `PUT /api/floors/{floorId}/map` (201/200/404/413/415), `GET /api/maps/{mapId}`, `GET /api/maps/{mapId}/image` (200, `ETag`, 304 on `If-None-Match`, `X-Content-Type-Options: nosniff`), `DELETE /api/maps/{mapId}` (204/404) in `backend/src/test/java/at/mci/igp/raumlotse/controller/FloorMapControllerTest.java`
- [X] T016 [P] [US3] Add failing frontend tests for `API/maps.ts` (list, multipart upload using form-data with CSRF header, delete) in `frontend/src/API/maps.test.ts`
- [X] T017 [P] [US3] Add failing component tests for `MapSelector` (lists maps, switching, empty state "no map" for all users) and `MapUploadControl` (admin only, shows server rejection message, shows aspect-ratio warning) in `frontend/src/components/FloorMap/MapSelector.test.tsx` and `frontend/src/components/FloorMap/MapUploadControl.test.tsx`

### Implementation for User Story 3

- [X] T018 [US3] Implement `MapImageValidator` in `…/raumlotse/service/MapImageValidator.java` using JDK `ImageIO` (PNG/JPEG only, ≤ 10 MB, structured log line on rejection without file content/user data)
- [X] T019 [US3] Implement `FloorMapService` in `…/raumlotse/service/FloorMapService.java` (list, get, create-or-replace by floor, delete; compute `aspectRatioChanged`, `placedRoomCount`, map name = building name + floor name)
- [X] T019a [US3] Make `FloorService.delete` reject floors that have a map via `FloorMapRepository.existsByFloorId` (ConflictException) in `…/raumlotse/service/FloorService.java`
- [X] T020 [US3] Implement `FloorMapController` in `…/raumlotse/controller/FloorMapController.java` per contract (multipart `image` part, 201/200 distinction, image streaming with ETag/304 and `nosniff`, 415 `MAP_IMAGE_UNSUPPORTED`)
- [X] T021 [P] [US3] Implement `frontend/src/API/maps.ts` (`listMaps`, `getMap`, `uploadMapImage`, `deleteMap`, `mapImageUrl`) using the existing `apiRequest` client
- [X] T022 [P] [US3] Implement `MapSelector` and `MapUploadControl` components in `frontend/src/components/FloorMap/` (German UI text consistent with existing pages; upload/delete visible only when `useCurrentRoles().admin`)
- [X] T022a [P] [US3] Add a building/floor picker to `MapUploadControl` in `frontend/src/components/FloorMap/MapUploadControl.tsx` using `listBuildings`/`listFloors` (`frontend/src/API/buildings.ts`, `frontend/src/API/floors.ts`), offering only floors without a map, with a failing test first in `MapUploadControl.test.tsx`
- [X] T023 [US3] Create `frontend/src/pages/MapPage.tsx` and routes `/maps` and `/maps/:mapId` inside the `RequireAuth` block of `frontend/src/App.tsx`; add a navigation link in `frontend/src/components/Navigation/`; add `MapPage.test.tsx` for empty state and map switching

**Checkpoint**: Maps can be created, viewed, switched, replaced, and deleted; independently demonstrable.

---

## Phase 4: User Story 1 - Place a Room on the Map (Priority: P1) 🎯 MVP

**Goal**: Admins place, move, and remove a room's position; non-admins and displays can only read.

**Independent Test**: With a map for floor A, place a room of floor A by clicking, reload and verify same position, move it, remove it; placing a room of floor B on map A is rejected; non-admin sees markers but no edit controls.

### Tests for User Story 1 (write first, must fail)

- [X] T024 [P] [US1] Add failing service tests for `RoomPlacementService` (place new → created, place again → moved, remove, room must belong to map's floor → 422 `ROOM_FLOOR_MISMATCH`, unknown room/map → NotFound, at most one placement per room, coordinates outside `[0,1]` rejected) in `backend/src/test/java/at/mci/igp/raumlotse/service/RoomPlacementServiceTest.java`
- [X] T025 [P] [US1] Add failing API tests for `PUT/DELETE /api/maps/{mapId}/placements/{roomId}` (201/200/204/400/404/422) and that `GET /api/maps/{mapId}` includes the placements, in `backend/src/test/java/at/mci/igp/raumlotse/controller/RoomPlacementControllerTest.java`
- [X] T026 [P] [US1] Add failing integration test: place → restart-equivalent reload (new transaction) returns identical `x`,`y`; concurrent placement of same room leaves exactly one row, in `backend/src/test/java/at/mci/igp/raumlotse/RoomPlacementIntegrationTest.java`
- [X] T027 [P] [US1] Add failing component tests for `MapCanvas` (marker positioned at `left = x*100%`, `top = y*100%`; admin click on image with a selected room emits normalized coordinates; click outside image bounds shows hint and emits nothing; drag marker emits new position; non-admin has no handlers) in `frontend/src/components/FloorMap/MapCanvas.test.tsx`

### Implementation for User Story 1

- [X] T028 [US1] Implement `RoomPlacementService` in `…/raumlotse/service/RoomPlacementService.java` (validation rules from T024; perform placement as an upsert so concurrent first placements of the same room leave exactly one row and no error (last write wins); log placement failures with ids only)
- [X] T029 [US1] Implement `RoomPlacementController` in `…/raumlotse/controller/RoomPlacementController.java` and extend `FloorMapService`/`MapDetailResponse` so `GET /api/maps/{mapId}` returns `placements` with room name/status
- [X] T030 [P] [US1] Add `placeRoom`, `removePlacement` to `frontend/src/API/maps.ts`
- [X] T031 [US1] Implement `MapCanvas` in `frontend/src/components/FloorMap/MapCanvas.tsx` + `MapCanvas.css` (img + percent-positioned markers, pan/zoom via CSS transform and pointer events, marker select shows room name/id popover, touch-friendly hit size)
- [X] T032 [US1] Wire placement editing into `frontend/src/pages/MapPage.tsx` (admin: select room → click to place, drag to move, remove button; others read-only); extend `MapPage.test.tsx` for admin vs. non-admin behavior

**Checkpoint**: MVP — maps with rooms placed at persistent, resolution-independent positions.

---

## Phase 5: User Story 2 - See Which Rooms Are Placed (Priority: P1)

**Goal**: Placed rooms appear as labeled markers; unplaced rooms are listed and selectable for placement.

**Independent Test**: Floor with 5 rooms, 2 placed: map shows 2 labeled markers and the list shows exactly the other 3; selecting one from the list activates placement mode.

### Tests for User Story 2 (write first, must fail)

- [X] T033 [P] [US2] Add failing API/service tests for `GET /api/maps/{mapId}/unplaced-rooms` (only active rooms of the map's floor without placement) in `backend/src/test/java/at/mci/igp/raumlotse/controller/UnplacedRoomsControllerTest.java`
- [X] T034 [P] [US2] Add failing component tests for `UnplacedRoomList` (renders all unplaced rooms, empty message when all placed, selection callback, list updates after a placement) in `frontend/src/components/FloorMap/UnplacedRoomList.test.tsx`

### Implementation for User Story 2

- [X] T035 [US2] Implement the unplaced-rooms query in `RoomPlacementService`/`RoomRepository` and endpoint in `RoomPlacementController` (`…/raumlotse/service/RoomPlacementService.java`, `…/raumlotse/repository/RoomRepository.java`, `…/raumlotse/controller/RoomPlacementController.java`)
- [X] T036 [P] [US2] Add `listUnplacedRooms` to `frontend/src/API/maps.ts` and implement `UnplacedRoomList` in `frontend/src/components/FloorMap/UnplacedRoomList.tsx`
- [X] T037 [US2] Integrate `UnplacedRoomList` and marker labels into `frontend/src/pages/MapPage.tsx` (selection activates placement mode; list and markers update after place/move/remove; display deactivated rooms visually distinguished)

**Checkpoint**: Completeness of the map is verifiable at a glance.

---

## Phase 6: User Story 4 - Keep Placement Consistent with Room Changes (Priority: P2)

**Goal**: Renames, deletions, and new rooms are reflected on the map without orphaned data.

**Independent Test**: Rename a placed room (label changes), delete it (marker gone, no DB row), create a room (appears unplaced).

### Tests for User Story 4 (write first, must fail)

- [X] T038 [P] [US4] Add failing integration tests: renaming a placed room changes the name in `GET /api/maps/{id}` placements; deleting a room removes its `room_placement` row (FK cascade); a newly created room on the floor appears in `unplaced-rooms`; deleting a map makes its rooms unplaced in `backend/src/test/java/at/mci/igp/raumlotse/RoomPlacementLifecycleIntegrationTest.java`

### Implementation for User Story 4

- [X] T039 [US4] Verify in `…/raumlotse/service/RoomService.java` (`delete`) and `RoomDependentHistoryChecker` that deleting a room removes its placement via FK cascade and is not blocked by it; adjust only if T038 fails
- [X] T039a [US4] Add failing tests first in `UnplacedRoomsControllerTest.java`, then exclude `DEACTIVATED` rooms from the unplaced-rooms query and mark deactivated rooms that have a placement in `PlacementResponse` (`…/raumlotse/repository/RoomRepository.java`, `…/raumlotse/dto/PlacementResponse.java`)
- [X] T039b [US4] Add a frontend refetch of map data on route entry and window focus in `frontend/src/pages/MapPage.tsx` with a test in `MapPage.test.tsx`

**Checkpoint**: Map data stays consistent with room lifecycle.

---

## Phase 7: User Story 5 - Connect Maps via Stairs and Elevators (Priority: P2)

**Goal**: Admins define named stairs/elevator connections with at most one point per map; maps show which other maps a connection reaches.

**Independent Test**: Create elevator "A", add points on map 0 and map 1, verify both maps show the point and the other reachable map; add a third map; remove a point; with one point left the connection is `incomplete`; delete the connection removes all points.

### Tests for User Story 5 (write first, must fail)

- [X] T040 [P] [US5] Add failing service tests for `ConnectionService` (create with `name` ≤ 100 chars and case-insensitive unique → 409; type must be `STAIRS`/`ELEVATOR`; add point = create, same map again = move; at most one point per connection per map; `incomplete` when < 2 points; deleting connection removes its points; deleting a map removes its connection points and flags affected connections `incomplete`; coordinates `[0,1]`) in `backend/src/test/java/at/mci/igp/raumlotse/service/ConnectionServiceTest.java`
- [X] T041 [P] [US5] Add failing API tests for `GET/POST /api/connections`, `PUT/DELETE /api/connections/{id}`, `PUT/DELETE /api/connections/{id}/points/{mapId}` (201/200/204/400/404/409) and that `GET /api/maps/{mapId}` includes `connections` with all their points, in `backend/src/test/java/at/mci/igp/raumlotse/controller/ConnectionControllerTest.java`
- [X] T042 [P] [US5] Add failing component tests for `ConnectionLayer` (stairs/elevator markers visually distinct, popover lists other reachable maps, incomplete badge) and `ConnectionCatalog` (admin create/rename/delete; admin place point on current map; non-admin read-only) in `frontend/src/components/FloorMap/ConnectionLayer.test.tsx` and `frontend/src/components/ConnectionCatalog/ConnectionCatalog.test.tsx`

### Implementation for User Story 5

- [X] T043 [US5] Implement `ConnectionService` in `…/raumlotse/service/ConnectionService.java` per T040 and `ConnectionController` in `…/raumlotse/controller/ConnectionController.java` per contract; extend `FloorMapService` so `MapDetailResponse.connections` returns connections with a point on the map including all their points
- [X] T044 [P] [US5] Add `listConnections`, `createConnection`, `updateConnection`, `deleteConnection`, `putConnectionPoint`, `deleteConnectionPoint` to `frontend/src/API/maps.ts`
- [X] T045 [P] [US5] Implement `ConnectionLayer` (`frontend/src/components/FloorMap/ConnectionLayer.tsx`) and `ConnectionCatalog` (`frontend/src/components/ConnectionCatalog/ConnectionCatalog.tsx`, `.css`)
- [X] T046 [US5] Integrate connection layer and catalog into `frontend/src/pages/MapPage.tsx` (admin: select connection → click to place its point on the current map; switch to another reachable map from the popover)

**Checkpoint**: All stories independently functional; data ready for the later route feature.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T047 [P] Add accessibility to the map UI: keyboard-operable marker selection and nudge-to-move for admins, text labels (not color alone), `aria-label`s, in `frontend/src/components/FloorMap/MapCanvas.tsx` with tests in `MapCanvas.test.tsx`
- [X] T048 [P] Add structured, greppable log statements for rejected uploads, placement/connection validation failures, and denied writes (ids only, no image data or personal data), and a test asserting no image bytes or user data in logs in `backend/src/test/java/at/mci/igp/raumlotse/config/MapLoggingPrivacyTest.java`
- [X] T049 [P] Add a performance integration check that `GET /api/maps/{id}` with 200 placements responds within 1 s and that the image endpoint returns 304 on revalidation in `backend/src/test/java/at/mci/igp/raumlotse/MapPerformanceIntegrationTest.java`
- [X] T050 Run `./mvnw test`, `npm run lint`, `npm test`, `npm run build`; execute the manual scenarios in `specs/012-room-map-placement/quickstart.md`; record results and any fixture extensions there
- [X] T051 [P] Update project docs (`README.md` API/feature overview) with the new map pages and endpoints, and note the open dependency on a future display one-time-code login

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2 → stories**. Phase 2 blocks everything.
- **US3 (Phase 3)** first: provides maps. **US1 (Phase 4)** requires US3. **US2 (Phase 5)** requires US1 (shares `MapPage`, placement service). **US4 (Phase 6)** requires US1 + US3. **US5 (Phase 7)** requires US3 only (can run in parallel with US1/US2 after Phase 3 if `MapPage.tsx` edits are coordinated).
- Within a story: tests (fail) → services → controllers → frontend API → components → page integration.
- Polish after all desired stories.

### Parallel opportunities

- Phase 1: T001–T003 together. Phase 2: T004, T005, T007, T008, T010, T011, T012 in parallel (T006 before T007/T008 verification; T009 after T005).
- US3: T013–T017 tests together; T021 and T022 in parallel after T020.
- US1: T024–T027 tests together; T030 parallel to backend work.
- US5 backend (T040/T041/T043) can proceed in parallel with US1 frontend work.

### Parallel example: User Story 1

```text
T024 RoomPlacementServiceTest   T025 RoomPlacementControllerTest
T026 RoomPlacementIntegrationTest   T027 MapCanvas.test.tsx
→ then T028 → T029, with T030 in parallel → T031 → T032
```

## Implementation Strategy

- **MVP**: Phases 1–4 (US3 + US1): maps can be uploaded and rooms placed with persistent, resolution-independent positions. Stop and validate with quickstart scenarios 1–4 and 7.
- **Increment 2**: US2 (completeness list) and US4 (lifecycle consistency).
- **Increment 3**: US5 (cross-floor connections), then Polish.
- Out of scope (later features): placing displays, route calculation, display one-time-code login.
