---

description: "Task list for feature 023 Path Network (Wegenetz)"
---

# Tasks: Path Network (Wegenetz)

**Input**: Design documents from `/specs/023-path-network/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/path-network-api.yaml, quickstart.md

**Tests**: Required. The constitution (Principle I, Test-First) makes failing tests before implementation non-negotiable: within every phase, write the listed tests first, run them and watch them fail, then implement.

**Organization**: Tasks are grouped by user story. Writes are gated by the existing permission `MAP_MANAGE` (no new permission); reads by `READ`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1–US6 from spec.md
- Paths: backend `backend/src/main/java/at/mci/igp/raumlotse/...`, tests `backend/src/test/java/at/mci/igp/raumlotse/...`, frontend `frontend/src/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Database schema and shared typing for the feature

- [X] T001 Write failing migration test `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkMigrationIntegrationTest.java` (pattern of `MapMigrationIntegrationTest.java`): V19 applies on top of V18; coordinates CHECK 0..1; `a_id < b_id` CHECK rejects self-loops; UNIQUE (a_id, b_id) rejects duplicate pairs; composite FKs reject a segment or attachment whose waypoint is on another map; deleting a waypoint cascades its segments and attachments; deleting a placement, connection point, or map cascades attachments; `length_m` CHECK > 0 and ≤ 100000; `role_permission` is unchanged.
- [X] T002 Create migration `backend/src/main/resources/db/migration/V19__create_path_network.sql` per data-model.md: add `UNIQUE (room_id, map_id)` to `room_placement` and `UNIQUE (id, map_id)` to `connection_point`; create `path_waypoint`, `path_segment`, `map_reference_segment`, `room_path_attachment`, `connection_point_path_attachment` with the exact constraints, composite FKs (ON DELETE CASCADE) and indexes on `map_id`, `a_id`, `b_id`; coordinates `NUMERIC(6,5)` with `CHECK 0 ≤ v ≤ 1`.
- [X] T003 [P] Add TypeScript types `frontend/src/types/pathNetwork.ts` mirroring the schemas in contracts/path-network-api.yaml (Position, Waypoint, Segment, Scale, ReferenceSegment, RoomAttachment, ConnectionPointAttachment, Issues, MapNetwork, ConnectionGroup, NetworkWide).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Entities, repositories, DTOs, access rules, and geometry helper that every story needs

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Write failing tests `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkAuthorizationIntegrationTest.java` (integration test through the real permission filter, pattern of `MapAuthorizationIntegrationTest.java`): every write endpoint from the contract returns 401 unauthenticated and 403 for a signed-in session without `MAP_MANAGE`; both GET endpoints return 401 unauthenticated and are allowed for `READ` sessions; a role granted `MAP_MANAGE` can write.
- [X] T005 [P] Write failing unit tests `backend/src/test/java/at/mci/igp/raumlotse/service/PathGeometryTest.java`: pixel distance `hypot(dx*widthPx, dy*heightPx)`, point-to-segment projection with parameter clamping, coincidence threshold (distance < 0.002 of the relative diagonal), pair normalization so `a_id < b_id`.
- [X] T006 Create JPA entities in `backend/src/main/java/at/mci/igp/raumlotse/domain/`: `PathWaypoint`, `PathSegment`, `MapReferenceSegment`, `RoomPathAttachment`, `ConnectionPointPathAttachment` matching data-model.md (UUID ids, `NUMERIC(6,5)` x/y, no version columns, last write wins).
- [X] T007 [P] Create repositories in `backend/src/main/java/at/mci/igp/raumlotse/repository/`: `PathWaypointRepository`, `PathSegmentRepository`, `MapReferenceSegmentRepository`, `RoomPathAttachmentRepository`, `ConnectionPointPathAttachmentRepository` with by-map queries (one query per table per map; parameterized JPA only).
- [X] T008 [P] Create `backend/src/main/java/at/mci/igp/raumlotse/service/PathGeometry.java` (pure helper: pixel distance, projection onto segment, coincidence threshold constant, pair normalization) so `PathGeometryTest` passes.
- [X] T009 [P] Create DTOs in `backend/src/main/java/at/mci/igp/raumlotse/dto/` per contract: `PositionRequest` (x,y with `@DecimalMin("0") @DecimalMax("1")`), `WaypointResponse`, `SegmentRequest`, `SegmentResponse` (`lengthMeters` nullable), `SplitResultResponse`, `ReferenceSegmentRequest`/`ReferenceSegmentResponse`, `AttachmentRequest`, `RoomAttachmentResponse`, `ConnectionPointAttachmentResponse`, `ScaleResponse`, `PathIssuesResponse`, `MapNetworkResponse`, `NetworkWideResponse`, `ConnectionGroupResponse`.
- [X] T010 Add access rules in `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java`: `GET /api/path-network` and `GET /api/maps/[^/]+/path-network` → `PermissionCode.READ`; `POST /api/maps/[^/]+/waypoints`, `PUT|DELETE /api/maps/[^/]+/waypoints/[^/]+`, `POST /api/maps/[^/]+/segments`, `DELETE /api/maps/[^/]+/segments/[^/]+`, `POST /api/maps/[^/]+/segments/[^/]+/split`, `PUT|DELETE /api/maps/[^/]+/reference-segment`, `PUT|DELETE /api/maps/[^/]+/room-attachments/[^/]+`, `PUT|DELETE /api/maps/[^/]+/connection-point-attachments/[^/]+` → `PermissionCode.MAP_MANAGE`. Widen the `MAP_MANAGE` description in `backend/src/main/java/at/mci/igp/raumlotse/domain/PermissionCode.java` to mention the path network.
- [X] T011 Create `backend/src/main/java/at/mci/igp/raumlotse/controller/PathNetworkController.java` with all routes from the contract delegating to a `backend/src/main/java/at/mci/igp/raumlotse/service/PathNetworkService.java` skeleton (constructor injection); reuse the existing Problem/exception handling (`MapRequestException`) for 404/409/422.
- [X] T012 [P] Create typed API client `frontend/src/API/pathNetwork.ts` (read + all write calls through the shared `apiRequest` in `frontend/src/API/client.ts`, which handles CSRF and errors, like `API/maps.ts`) and `frontend/src/API/pathNetwork.test.ts`.

**Checkpoint**: Migration, entities, access rules, and skeletons exist; authorization tests pass; user stories can begin.

---

## Phase 3: User Story 1 - Draw Walkways on a Floor Map (Priority: P1) 🎯 MVP

**Goal**: Administrators draw, move, split, and delete waypoints and segments; every signed-in user can view them.

**Independent Test**: Open a floor map, add waypoints, connect them into a corridor with a junction, reload, and see the same network; a non-admin sees it but cannot edit.

### Tests for User Story 1 (write first, must fail)

- [X] T013 [P] [US1] `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkIntegrationTest.java` (waypoint/segment part; service rules are tested end-to-end against the database instead of with mocks): add waypoint rejects coordinates outside [0,1] and a position coinciding with an existing waypoint (409 carrying the existing id); move waypoint; delete waypoint removes its segments; create segment rejects the same waypoint twice (400), a duplicate pair in either order (409), a waypoint of another map (422); delete segment keeps waypoints; split projects the position, replaces the segment by two segments in one transaction, rejects a projection coinciding with an end waypoint (409).
- [X] T014 [P] [US1] `backend/src/test/java/at/mci/igp/raumlotse/controller/PathNetworkControllerTest.java` (waypoints, segments, split, `GET /api/maps/{mapId}/path-network`): status codes and bodies per contract incl. 400/404/409/422.
- [X] T015 [P] [US1] `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkIntegrationTest.java` (extends `MapIntegrationSupport`): draw a corridor with a junction, reload via `GET /api/maps/{mapId}/path-network`, positions identical; non-admin write returns 403; unauthenticated returns 401; deleting the map removes all its waypoints and segments.
- [X] T016 [P] [US1] `frontend/src/components/PathNetwork/PathOverlay.test.tsx`: renders waypoints and segments in a 0..1 SVG viewBox aligned with `MapCanvas`; toggle hides it; read-only mode shows no edit tools; add-waypoint, connect (two clicks), drag-move, split, and delete tools call the API; a click on an existing waypoint reuses it; crossing segments without a shared waypoint show a hint.

### Implementation for User Story 1

- [X] T017 [US1] Implement waypoint, segment, and split operations in `backend/src/main/java/at/mci/igp/raumlotse/service/PathNetworkService.java` (validation per research D9, pair normalization via `PathGeometry`, split in one `@Transactional` method, structured log lines without personal data for rejected writes).
- [X] T018 [US1] Implement `GET /api/maps/{mapId}/path-network` (waypoints and segments; `scale.available=false`, `lengthMeters=null`, empty attachments and issues for now) and the write routes for waypoints, segments, and split in `backend/src/main/java/at/mci/igp/raumlotse/controller/PathNetworkController.java`.
- [X] T019 [P] [US1] Create `frontend/src/components/PathNetwork/PathOverlay.tsx` + `PathOverlay.css` (inline SVG, `viewBox="0 0 1 1"`, `preserveAspectRatio="none"`, `vector-effect: non-scaling-stroke`, per-viewer toggle) and `frontend/src/components/PathNetwork/PathToolbar.tsx` (tools: add, connect, move, split, delete).
- [X] T020 [US1] Integrate overlay and toolbar into `frontend/src/components/FloorMap/MapCanvas.tsx` and `frontend/src/pages/MapPage.tsx`; show edit tools only for sessions with `MAP_MANAGE` (via `useCurrentRoles`); keep Feature 012 markers undisturbed (z-order, pointer events).

**Checkpoint**: US1 is fully functional and independently testable (MVP).

---

## Phase 4: User Story 2 - Set the Map Scale (Priority: P1)

**Goal**: One reference segment per map with a real length in meters yields the scale and the meter lengths of all segments.

**Independent Test**: Draw a reference segment, enter its length, see segment lengths in meters; edit or delete it and see them update or become unavailable.

### Tests for User Story 2 (write first, must fail)

- [X] T021 [P] [US2] `backend/src/test/java/at/mci/igp/raumlotse/service/MapScaleServiceTest.java`: `pixelsPerMeter = hypot((x2-x1)*widthPx, (y2-y1)*heightPx) / lengthM`; `lengthM` must be > 0 and ≤ 100000; identical endpoints rejected (distance < 0.002 of the relative diagonal); coordinates within [0,1]; at most one reference per map (second PUT replaces); delete removes the scale; segment `lengthMeters` is `null` without scale and pixel distance / scale with it; scale stays consistent after the map image is replaced with the same aspect ratio.
- [X] T022 [P] [US2] Extend `backend/src/test/java/at/mci/igp/raumlotse/controller/PathNetworkControllerTest.java` and `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkIntegrationTest.java`: `PUT`/`DELETE /api/maps/{mapId}/reference-segment` (200/201/204/400/404); the network response carries `scale` and `lengthMeters`; SC-003 check: a segment laid over the reference stretch is within 5 percent of the entered length.
- [X] T023 [P] [US2] `frontend/src/components/PathNetwork/ReferenceSegmentTool.test.tsx`: draw two endpoints, enter length, reject empty/zero/negative/non-numeric input with a clear message, show the reference visibly distinct and labeled with its length, show the "scale missing" state, show segment length on selection, edit and delete the reference.

### Implementation for User Story 2

- [X] T024 [US2] Create `backend/src/main/java/at/mci/igp/raumlotse/service/MapScaleService.java` (derive `pixelsPerMeter` from the reference and the map's `width_px`/`height_px`; compute segment `lengthMeters`; set/replace/delete the reference with the validation above) and wire it into `PathNetworkService` and `PathNetworkController` (`PUT|DELETE /api/maps/{mapId}/reference-segment`).
- [X] T025 [P] [US2] Create `frontend/src/components/PathNetwork/ReferenceSegmentTool.tsx`; render the reference segment (distinct style, length label, not part of the walkway network) and segment lengths in meters in `frontend/src/components/PathNetwork/PathOverlay.tsx`; show a "scale missing" banner with a call to action.

**Checkpoint**: US1 and US2 work independently; the map reports meters.

---

## Phase 5: User Story 3 - Attach Rooms to the Network (Priority: P1)

**Goal**: Each placed room can be attached to one waypoint; unattached placed rooms are visible and listed.

**Independent Test**: Attach a placed room to a waypoint, reload, see the attachment; remove the placement and see the attachment vanish.

### Tests for User Story 3 (write first, must fail)

- [X] T026 [P] [US3] `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkIntegrationTest.java` (room attachment part): attach requires a placement of the room on this map (422 otherwise) and a waypoint of this map (422); re-attaching replaces the previous attachment (200 vs 201); delete attachment (404 when none); moving the placement keeps the attachment; removing the placement or deleting the room removes the attachment; deleting the waypoint leaves the room placed but unattached.
- [X] T027 [P] [US3] Extend `backend/src/test/java/at/mci/igp/raumlotse/controller/PathNetworkControllerTest.java`, `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkIntegrationTest.java`, and `backend/src/test/java/at/mci/igp/raumlotse/RoomPlacementLifecycleIntegrationTest.java`: `PUT|DELETE /api/maps/{mapId}/room-attachments/{roomId}` per contract; the network response lists `roomAttachments`; zero dangling attachments after placement, room, waypoint, or map deletion (SC-005).
- [X] T028 [P] [US3] `frontend/src/components/PathNetwork/AttachmentPanel.test.tsx` (room part): attach tool picks a placed room then a waypoint; a connecting line is drawn from the room marker to its waypoint and follows when the marker moves; unattached placed rooms are marked and listed; detach works.

### Implementation for User Story 3

- [X] T029 [US3] Implement room attachment set/replace/delete in `backend/src/main/java/at/mci/igp/raumlotse/service/PathNetworkService.java` and the two routes in `backend/src/main/java/at/mci/igp/raumlotse/controller/PathNetworkController.java`; include `roomAttachments` in `GET /api/maps/{mapId}/path-network`.
- [X] T030 [P] [US3] Create `frontend/src/components/PathNetwork/AttachmentPanel.tsx` (room attachment workflow, list of unattached placed rooms); draw attachment lines and unattached markers in `PathOverlay.tsx`; integrate into `frontend/src/pages/MapPage.tsx` next to `UnplacedRoomList`.

**Checkpoint**: Routes can later start or end at rooms; US1–US3 work independently.

---

## Phase 6: User Story 4 - Attach Stairs and Elevators to the Network (Priority: P2)

**Goal**: Connection points are attached to waypoints; a connection is fully attached only when all its points are.

**Independent Test**: For an elevator with points on two maps, attach both points; the connection is flagged fully attached; delete a waypoint and it is flagged not fully attached.

### Tests for User Story 4 (write first, must fail)

- [X] T031 [P] [US4] `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkIntegrationTest.java` (connection point part): attach requires the connection point to be on this map and the waypoint on this map (422 otherwise); replace/delete; at most one attachment per connection point; `fullyAttached` is true only when every point of the connection has an attachment; moving or removing the point, deleting the connection, or deleting the map removes attachments with no dangling rows.
- [X] T032 [P] [US4] Extend `backend/src/test/java/at/mci/igp/raumlotse/controller/PathNetworkControllerTest.java`, `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkIntegrationTest.java`, and `backend/src/test/java/at/mci/igp/raumlotse/ConnectionIntegrationTest.java`: `PUT|DELETE /api/maps/{mapId}/connection-point-attachments/{connectionPointId}` per contract; the network response lists `connectionPointAttachments`.
- [X] T033 [P] [US4] Extend `frontend/src/components/PathNetwork/AttachmentPanel.test.tsx` and `frontend/src/components/ConnectionCatalog/ConnectionCatalog.test.tsx`: attach tool for connection points, "not fully attached" badge in the connection catalog.

### Implementation for User Story 4

- [X] T034 [US4] Implement connection point attachment set/replace/delete and `fullyAttached` in `backend/src/main/java/at/mci/igp/raumlotse/service/PathNetworkService.java`; add the two routes in `backend/src/main/java/at/mci/igp/raumlotse/controller/PathNetworkController.java`; include `connectionPointAttachments` in the map network response.
- [X] T035 [P] [US4] Extend `frontend/src/components/PathNetwork/AttachmentPanel.tsx` and `PathOverlay.tsx` for connection points; show the "not fully attached" badge in `frontend/src/components/ConnectionCatalog/ConnectionCatalog.tsx`.

**Checkpoint**: Multi-floor connections are usable on the network side.

---

## Phase 7: User Story 5 - Check Network Completeness (Priority: P2)

**Goal**: One overview lists scale, attachment, and connectivity problems of a map.

**Independent Test**: Build an isolated sub-network, leave a room unattached, remove the reference segment; all three appear; fixing them shows the map as complete.

### Tests for User Story 5 (write first, must fail)

- [X] T036 [P] [US5] `backend/src/test/java/at/mci/igp/raumlotse/service/PathNetworkIssueCheckerTest.java`: union-find components (largest first, 0 for an empty map), `missingScale`, `unattachedRoomIds` (placed rooms only), `unattachedConnectionPointIds`, `attachmentsOutsideMainComponent` (kind ROOM or CONNECTION_POINT), a complete map yields no issues; crossing segments without a shared waypoint stay unconnected.
- [X] T037 [P] [US5] Extend `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkIntegrationTest.java` and add `frontend/src/components/PathNetwork/IssueList.test.tsx`: the `issues` block of the map network response is correct for the three-problem scenario; the list highlights separate parts on the map and shows "complete" when clean (SC-004).

### Implementation for User Story 5

- [X] T038 [US5] Create `backend/src/main/java/at/mci/igp/raumlotse/service/PathNetworkIssueChecker.java` (union-find over waypoints/segments, rules per research D6) and include `issues` in the map network response in `PathNetworkService`.
- [X] T039 [P] [US5] Create `frontend/src/components/PathNetwork/IssueList.tsx` (grouped issues, click to focus the element on the map, "complete" state) and highlight separate network parts in `PathOverlay.tsx`; mount in `frontend/src/pages/MapPage.tsx`.

**Checkpoint**: Administrators can verify completeness before routing relies on the data.

---

## Phase 8: User Story 6 - Consume the Network from Other Features (Priority: P2)

**Goal**: A single read-only endpoint exposes all maps' networks and the connection groups for the routing feature (024).

**Independent Test**: Request the network of one map and of all maps; every waypoint, segment, attachment, scale, and connection group is present; unauthenticated callers are rejected; a map without scale reports `scale.available=false`.

### Tests for User Story 6 (write first, must fail)

- [X] T040 [P] [US6] `backend/src/test/java/at/mci/igp/raumlotse/controller/PathNetworkControllerTest.java` and `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkIntegrationTest.java`: `GET /api/path-network` returns all maps with waypoints, segments (`lengthMeters`), `scale`, attachments, `issues`, plus `connectionGroups` (points with `mapId`, `connectionPointId`, `waypointId` or null, `fullyAttached`); a `READ` session can read; unauthenticated gets 401; no invented meter values without a scale.
- [X] T041 [P] [US6] `backend/src/test/java/at/mci/igp/raumlotse/PathNetworkPerformanceIntegrationTest.java` (pattern of `MapPerformanceIntegrationTest.java`): a map with 500 waypoints and about 600 segments returns from both read endpoints in under 2 seconds (SC-006) with one query per table (no N+1).

### Implementation for User Story 6

- [X] T042 [US6] Implement the network-wide read model in `backend/src/main/java/at/mci/igp/raumlotse/service/PathNetworkService.java` (batch-load all tables once, group by map, build `connectionGroups` from `connection` and `connection_point` plus attachments) and add `GET /api/path-network` in `backend/src/main/java/at/mci/igp/raumlotse/controller/PathNetworkController.java`.

**Checkpoint**: All stories complete; Feature 024 can consume the data without extra joins.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T043 [P] Extend `backend/src/test/java/at/mci/igp/raumlotse/MapLoggingPrivacyTest.java` for the new write paths: rejected writes log structured, greppable lines without personal data.
- [X] T044 [P] Show the existing `aspectRatioChanged` warning from `backend/src/main/java/at/mci/igp/raumlotse/service/FloorMapService.java` in the path overlay (`frontend/src/components/PathNetwork/PathOverlay.tsx`) as "network and reference segment may no longer match"; add tests in `backend/src/test/java/at/mci/igp/raumlotse/service/FloorMapServiceTest.java` and `PathOverlay.test.tsx`.
- [X] T045 [P] Small-screen behavior: overlay remains viewable and pannable on narrow screens, edit tools hidden below the desktop breakpoint in `frontend/src/components/PathNetwork/PathOverlay.css`; test in `PathOverlay.test.tsx`.
- [X] T046 Run backend `./mvnw test` and frontend `npm run lint && npx tsc -b && npm test`; fix findings.
- [ ] T047 Walk through every scenario of `specs/023-path-network/quickstart.md` against the running stack and record deviations. (API scenarios 2–7 are covered by the automated integration tests; the browser walkthrough of the UI is still to be done by hand.)
- [X] T048 [P] Add a pointer to feature 023 where `specs/012-room-map-placement/spec.md` excludes paths, and document the new endpoints in `docs/` where the existing map API is described.

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2** must complete first; Phase 2 blocks all user stories.
- **US1** (waypoints, segments, view) has no story dependencies and is the MVP.
- **US2** depends on US1 only for the overlay and the read response; its service and endpoints are independent.
- **US3** and **US4** need waypoints from US1; they are independent of each other and of US2.
- **US5** reports on scale (US2) and attachments (US3, US4); build it after those.
- **US6** finishes the read model for all maps; build it after US1–US5.
- Polish comes last.

Within each story: tests first (must fail) → services → controllers → frontend.

## Parallel Opportunities

- Setup: the frontend types task runs parallel to the migration work.
- Foundational: geometry test/helper, repositories, DTOs, and the API client are independent files.
- Per story: all test tasks marked [P] run in parallel; the frontend component task runs parallel to backend implementation once the contract is fixed.
- US3 and US4 can be built in parallel by two developers after US1.

### Parallel Example: User Story 1

```text
T013 PathNetworkServiceTest | T014 PathNetworkControllerTest | T015 PathNetworkIntegrationTest | T016 PathOverlay.test.tsx
then T017 service -> T018 controller; in parallel with them T019 PathOverlay.tsx + PathToolbar.tsx; finally T020 integration
```

## Implementation Strategy

1. **MVP**: Phases 1–3 (draw and view the network). Demoable by itself.
2. Add US2 (meters), then US3 (rooms): together they make single-floor routing data complete.
3. Add US4 (connection points) for multi-floor routing data, then US5 (checks) and US6 (consumer endpoint).
4. Finish with Polish; stop at any checkpoint to validate independently.

