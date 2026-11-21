---

description: "Task list for Routing and Neighbor Rooms on the Door Sign"
---

# Tasks: Routing and Neighbor Rooms on the Door Sign

**Input**: Design documents from `specs/024-routing-api-neighbor-rooms/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/routing-api.yaml, quickstart.md

**Tests**: Required — the constitution makes Test-First Development non-negotiable. Within every phase, write the tests first, run them, and see them fail before implementing.

**Out of scope (do not build)**: routes between different buildings, one-time-code sign-in (025), the floor display (026), visual redesign of the door sign, any write access in routing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 = door sign neighbors, US2 = route between two rooms, US3 = reusable results for other consumers

## Phase 1: Setup

- [X] T001 Create `RoutingDefaults` holder with walking speed 1.2 m/s, stairs 15 s per floor step, elevator 30 s per change, straight tolerance 20°, max 3 neighbors per side in `backend/src/main/java/at/mci/igp/raumlotse/service/RoutingDefaults.java`
- [X] T002 [P] Add TypeScript types `NeighborEntry`, `Neighbors`, `Route`, `RouteStep` mirroring `contracts/routing-api.yaml` in `frontend/src/types/routing.ts`

---

## Phase 2: Foundational (blocks all user stories)

**Purpose**: floor order (needed for up/down and floor-step costs) and the pure routing core (graph + Dijkstra).

### Tests first (must fail before implementation)
- [X] T003 [P] Migration test: `floor.position` backfilled per building (ground-floor-flagged floor first, then by name, positions 0..n-1), unique per building, in `backend/src/test/java/at/mci/igp/raumlotse/FloorPositionMigrationIntegrationTest.java`
- [X] T004 [P] Service tests for floor order: new floor appended on top (`max(position)+1`), delete closes the gap, reorder requires exactly the building's floors (else 400), `groundFloor` is true only for position 0 and ignored on create/update requests, in `backend/src/test/java/at/mci/igp/raumlotse/service/FloorOrderServiceTest.java`
- [X] T005 [P] Controller/permission tests for `PUT /api/buildings/{buildingId}/floors/order` (200, 400, 401, 403 without floor-management permission, 404) in `backend/src/test/java/at/mci/igp/raumlotse/controller/FloorControllerTest.java`
- [X] T006 [P] Unit tests for `RoutingGraph`: waypoints/segments become nodes/edges in seconds, maps without scale excluded, connection edges between attached points of one connection (stairs 15 s × floor steps, elevator 30 s), unattached points skipped, connections whose maps lie in different buildings ignored, `accessibleOnly` drops stairs, in `backend/src/test/java/at/mci/igp/raumlotse/service/RoutingGraphTest.java`
- [X] T007 [P] Unit tests for `RouteCalculator` (Dijkstra) on hand-built graphs: shortest by time, same floor, cross floor, stairs vs. elevator choice, accessible only, unreachable, route to itself (distance 0, one step), distance equals the sum of segment meters, in `backend/src/test/java/at/mci/igp/raumlotse/service/RouteCalculatorTest.java`

### Implementation
- [X] T008 Migration `V20__floor_position.sql`: add `floor.position INT NOT NULL`, backfill per building, no unique constraint; keep `ground_floor` synchronized with `position == 0`, in `backend/src/main/resources/db/migration/V20__floor_position.sql`
- [X] T009 Add `position` to `Floor` (new floors get `max(position)+1`; `isGroundFloor()` returns `position == 0`) and order queries to `FloorRepository` in `backend/src/main/java/at/mci/igp/raumlotse/domain/Floor.java` and `backend/src/main/java/at/mci/igp/raumlotse/repository/FloorRepository.java`
- [X] T010 Update `FloorService`: append on create, close gap on delete, new `reorder(buildingId, floorIds)`; ignore `groundFloor` input in create/update in `backend/src/main/java/at/mci/igp/raumlotse/service/FloorService.java`
- [X] T011 Add `FloorOrderRequest` and `position` in `FloorResponse` (keep `groundFloor` as derived field) in `backend/src/main/java/at/mci/igp/raumlotse/dto/FloorOrderRequest.java` and `backend/src/main/java/at/mci/igp/raumlotse/dto/FloorResponse.java`
- [X] T012 Add `PUT /api/buildings/{buildingId}/floors/order` and register it with the existing floor-management permission in `backend/src/main/java/at/mci/igp/raumlotse/controller/FloorController.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java`
- [X] T013 Implement `RoutingGraph` (builds nodes/edges from `NetworkWideResponse` plus floor positions and building ids; rules from `data-model.md`) in `backend/src/main/java/at/mci/igp/raumlotse/service/RoutingGraph.java`
- [X] T014 Implement `RouteCalculator` (Dijkstra on `PriorityQueue`, cost seconds, returns distance meters, seconds and ordered steps with floor changes; single-source mode for neighbors) in `backend/src/main/java/at/mci/igp/raumlotse/service/RouteCalculator.java`
- [X] T015 Frontend floor API: `reorderFloors(buildingId, floorIds)` and `position` on the floor type, with client test, in `frontend/src/API/floors.ts`
- [X] T016 [P] Component test for the sortable floor list in the building catalog: up/down buttons change order, bottom entry shows as ground floor, ground-floor checkbox is gone, buttons are keyboard operable, in `frontend/src/components/BuildingCatalog/BuildingCatalog.test.tsx`
- [X] T017 Replace the ground-floor checkbox with a floor list ordered highest on top, with up/down buttons calling `reorderFloors`, in `frontend/src/components/BuildingCatalog/BuildingCatalog.tsx`
- [X] T018 Remove the now-unused `groundFloor` write path from the floor create/update client and forms and keep read use (room overview) working; run the existing floor/room tests in `frontend/src/API/floors.ts` and `frontend/src/components/RoomOverview/RoomOverview.tsx`

**Checkpoint**: floors can be ordered; graph and Dijkstra pass their unit tests.

---

## Phase 3: User Story 1 - Neighboring Rooms on the Door Sign (Priority: P1) 🎯 MVP

**Goal**: The door sign shows up to 3 rooms to the left and 3 to the right, plus stairs/elevator up and down, with distance, walking time and status; the area is omitted when nothing can be shown.

**Independent Test**: Open the door sign of an attached room on a scaled floor: neighbors appear with direction, distance, time and status. Open one of an unattached room or of a map without scale: no neighbor area, the rest unchanged.

### Tests first (must fail before implementation)
- [X] T019 [P] [US1] Unit tests for `NeighborCalculator`: facing vector, left/right/straight (≤ 20° ahead = straight, ties to the right), ≤ 3 per side ordered by walking distance, rooms beyond junctions included, same-waypoint candidates classified by marker vectors, only active rooms on the same map, other network parts excluded, zero orientation vector gives `NO_ORIENTATION`, in `backend/src/test/java/at/mci/igp/raumlotse/service/NeighborCalculatorTest.java`
- [X] T020 [P] [US1] Unit tests for up/down entries: nearest stairs and nearest elevator separately per direction, labeled by type, only fully attached connections within one building, `UP`/`DOWN` from floor positions, in `backend/src/test/java/at/mci/igp/raumlotse/service/NeighborCalculatorVerticalTest.java`
- [X] T021 [P] [US1] Integration tests for `GET /api/rooms/{roomId}/neighbors`: 200 with entries and status, 200 `available=false` with reasons `ROOM_NOT_ATTACHED`, `SCALE_MISSING`, `NO_ORIENTATION`, `NO_NEIGHBORS`, 404 unknown room, 401, 403, no write methods exist, in `backend/src/test/java/at/mci/igp/raumlotse/RoutingNeighborsIntegrationTest.java`
- [X] T022 [P] [US1] Frontend tests: `NeighborSection` renders one `Wayfinding` per entry with German direction, target, "ca. X m · Y s" and status; omitted when empty/unavailable; long names shortened, in `frontend/src/components/RoomDisplay/NeighborSection.test.tsx`
- [X] T023 [P] [US1] Page test: `RoomDisplayPage` shows neighbors, and still renders everything else when the neighbors request fails, in `frontend/src/pages/RoomDisplayPage.test.tsx`

### Implementation
- [X] T024 [US1] Implement `NeighborCalculator` (single-source search on the room's map, side classification, per-side limit, vertical entries) in `backend/src/main/java/at/mci/igp/raumlotse/service/NeighborCalculator.java`
- [X] T025 [US1] Add bulk status lookup for up to 6 rooms reusing the OCCUPIED/RESERVED/AVAILABLE rule in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomStatusService.java`
- [X] T026 [US1] Implement `RoutingService.neighbors(roomId)` (load 023 read model and floor data, build graph, map problems to reasons, attach status, log reason code without personal data) in `backend/src/main/java/at/mci/igp/raumlotse/service/RoutingService.java`
- [X] T027 [US1] Add DTOs `NeighborsResponse` and `NeighborEntryResponse` in `backend/src/main/java/at/mci/igp/raumlotse/dto/NeighborsResponse.java` and `backend/src/main/java/at/mci/igp/raumlotse/dto/NeighborEntryResponse.java`
- [X] T028 [US1] Add `RoutingController` with `GET /api/rooms/{roomId}/neighbors` and register the path with permission READ in `backend/src/main/java/at/mci/igp/raumlotse/controller/RoutingController.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java`
- [X] T029 [US1] Add the neighbors API client `getRoomNeighbors(roomId)` with zod validation and test in `frontend/src/API/routing.ts` and `frontend/src/API/routing.test.ts`
- [X] T030 [US1] Implement `NeighborSection` mapping entries to the existing `Wayfinding` block (LEFT→links, RIGHT→rechts, STRAIGHT→geradeaus, UP→hoch, DOWN→runter; target text, distance and time string) plus status label in `frontend/src/components/RoomDisplay/NeighborSection.tsx` and its CSS next to it
- [X] T031 [US1] Extend `RoomDisplayProps` with optional `neighbors` and render `NeighborSection` in `frontend/src/components/RoomDisplay/roomDisplayTypes.ts` and `frontend/src/components/RoomDisplay/RoomDisplay.tsx`
- [X] T032 [US1] Load neighbors in `RoomDisplayPage` next to status with `.catch(() => null)` and the existing 30 s refresh in `frontend/src/pages/RoomDisplayPage.tsx`

**Checkpoint**: US1 works and is demonstrable alone (MVP).

---

## Phase 4: User Story 2 - Route and Walking Time Between Two Rooms (Priority: P1)

**Goal**: Distance, walking time and steps between any two rooms, across floors, with an accessible-only option and clear errors.

**Independent Test**: Request routes for same-floor and cross-floor pairs; verify distance/time/steps, `accessibleOnly`, and the 404/409 cases.

### Tests first (must fail before implementation)
- [X] T033 [P] [US2] Integration tests for `GET /api/routes`: same floor, cross floor with `floorChange`, stairs vs. elevator choice, `accessibleOnly=true` never uses stairs, 409 `NO_ACCESSIBLE_ROUTE`, 409 `ROOM_NOT_ATTACHED`, 409 `SCALE_MISSING`, 409 `NOT_CONNECTED` (also for rooms in different buildings), 404 unknown room, 400 missing/invalid parameters, 401, 403, in `backend/src/test/java/at/mci/igp/raumlotse/RoutingRoutesIntegrationTest.java`
- [X] T034 [P] [US2] Service tests for problem precedence (unknown room → not attached → scale missing → not connected → no accessible route only if a non-restricted route exists) in `backend/src/test/java/at/mci/igp/raumlotse/service/RouteFinderTest.java` (precedence lives in the pure `RouteFinder`)

### Implementation
- [X] T035 [US2] Add `RouteResponse`, `RouteStepResponse` (with `floorChange`) and `RoutingProblem` mapping to the existing `Problem` format in `backend/src/main/java/at/mci/igp/raumlotse/dto/RouteResponse.java`, `backend/src/main/java/at/mci/igp/raumlotse/dto/RouteStepResponse.java` and `backend/src/main/java/at/mci/igp/raumlotse/exception/GlobalExceptionHandler.java`
- [X] T036 [US2] Implement `RoutingService.route(from, to, accessibleOnly)` with the problem precedence above in `backend/src/main/java/at/mci/igp/raumlotse/service/RoutingService.java`
- [X] T037 [US2] Add `GET /api/routes` (UUID/boolean parameter validation) and register it with permission READ in `backend/src/main/java/at/mci/igp/raumlotse/controller/RoutingController.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java`
- [X] T038 [US2] Add `getRoute(fromRoomId, toRoomId, accessibleOnly)` client with test in `frontend/src/API/routing.ts` and `frontend/src/API/routing.test.ts`

**Checkpoint**: US1 and US2 both work independently.

---

## Phase 5: User Story 3 - Reusable Results for Other Consumers (Priority: P2)

**Goal**: Neighbor and route results are in one stable form, always reflect current network data, and are usable by the floor display (026) without recomputation.

**Independent Test**: Edit the network, then request results: they change accordingly; the same structures serve neighbors for any room.

### Tests first (must fail before implementation)
- [X] T039 [P] [US3] Integration test: after an edit via the 023 API (move a waypoint, delete a segment, change the scale), the next neighbors/route request reflects it with no caching, in `backend/src/test/java/at/mci/igp/raumlotse/RoutingFreshnessIntegrationTest.java`
- [X] T040 [P] [US3] Contract test: responses of both endpoints validate against `specs/024-routing-api-neighbor-rooms/contracts/routing-api.yaml` field names and enums, in `backend/src/test/java/at/mci/igp/raumlotse/RoutingContractTest.java`
- [X] T041 [P] [US3] Test: neighbors of every attached room of a floor can be requested without further calculation by the consumer (shape identical across rooms; scale-missing map yields `available=false`), in `backend/src/test/java/at/mci/igp/raumlotse/RoutingNeighborsIntegrationTest.java`

### Implementation
- [X] T042 [US3] Expose a `RoutingService` method `neighborsForFloor(mapId)` returning the neighbor result of all attached rooms of one map for reuse by feature 026 (no HTTP endpoint added) in `backend/src/main/java/at/mci/igp/raumlotse/service/RoutingService.java`
- [X] T043 [US3] Document the reuse contract (result shapes, reasons, floor-order semantics, no cross-building routes) in `specs/024-routing-api-neighbor-rooms/quickstart.md`

---

## Phase 6: Polish & Cross-Cutting

- [X] T044 [P] Performance test: neighbors and route in under 1 s for 500 waypoints per floor × 5 floors (SC-004) in `backend/src/test/java/at/mci/igp/raumlotse/RoutingPerformanceIntegrationTest.java`
- [X] T045 [P] Authorization matrix entries for the two new GET paths and the floor order PUT in `backend/src/test/java/at/mci/igp/raumlotse/service/AuthorizationMatrixTest.java`
- [X] T046 [P] Verify no write method exists on routing paths and logs contain no personal data (grep structured log lines) in `backend/src/main/java/at/mci/igp/raumlotse/controller/RoutingController.java`
- [X] T047 Run backend `./mvnw test` and frontend `npm test`, `npx tsc -b`, `npm run lint`; fix findings
- [ ] T048 Walk through `specs/024-routing-api-neighbor-rooms/quickstart.md` end to end against the running Docker Compose stack

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → (Phase 3 and Phase 4 may run in parallel after Phase 2; both share `RoutingService` and `RoutingController`, so edits to those files are sequential) → Phase 5 → Phase 6.
- Phase 2 floor-order tasks and graph/Dijkstra tasks are independent of each other and can run in parallel; the frontend floor list depends on the floor API client.
- Within each phase: tests first and failing, then implementation.

## Parallel Examples

- Phase 2 tests: the migration, floor service, controller, graph and Dijkstra test tasks all touch different files.
- US1: `NeighborCalculatorTest`, `NeighborSection.test.tsx` and the integration test can be written in parallel.

## Implementation Strategy

1. MVP: Phases 1–3 (neighbors on the door sign) — deliver and demo.
2. Add US2 (routes), then US3 (reuse guarantees), then polish.
