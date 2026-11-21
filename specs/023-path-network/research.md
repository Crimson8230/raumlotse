# Research: Path Network

## D1 — Scale from a reference segment
- **Decision**: `map_reference_segment` (one row per map) stores two relative endpoints and `length_m`. Scale is derived on read: `pixelsPerMeter = pixelDistance / length_m`, where `pixelDistance = hypot((x2-x1)*width_px, (y2-y1)*height_px)` using the map's current image size. Not stored.
- **Rationale**: Clarified requirement (reference segment is drawn, stays visible, one per map). Storing only the reference keeps scale and all segment lengths consistent after moving/editing/deleting it, and positions stay valid after an image replacement with the same aspect ratio (relative coordinates + current pixel size).
- **Alternatives**: Stored `pixels_per_meter` column (can drift from the visible reference, needs recalculation); entering px/m directly (rejected by the clarification).

## D2 — Relative geometry and lengths
- **Decision**: Waypoints use `NUMERIC(6,5)` x/y in [0,1] like Feature 012. Segment length in meters = `hypot(dx*width_px, dy*height_px) / pixelsPerMeter`, computed in the read model; `null` when no scale exists.
- **Rationale**: Resolution/zoom independence (FR-004); avoids invented values (FR-007, US6-2).
- **Alternatives**: Stored per-segment length (stale on scale change).

## D3 — Graph model and integrity
- **Decision**: `path_waypoint(id, map_id, x, y)` with `UNIQUE(id, map_id)`. `path_segment(map_id, a_id, b_id)` with composite FKs `(a_id,map_id)` and `(b_id,map_id)` → waypoint, `CHECK (a_id < b_id)` (normalized, rules out self-loops), `UNIQUE (a_id, b_id)` (no duplicate pair, direction-less). Deleting a waypoint cascades its segments.
- **Rationale**: Same-map integrity and uniqueness are guaranteed by the database (Principle IV); service only normalizes pair order and rejects coincident points.
- **Alternatives**: Service-only validation (race-prone).

## D4 — Attachments
- **Decision**: `room_path_attachment(room_id PK, map_id, waypoint_id)` with FK `(room_id)` → `room_placement(room_id)` ON DELETE CASCADE (the service checks that the placement lies on the same map; no new index on `room_placement`, whose concurrent upsert from feature 012 an extra unique index would break) and FK `(waypoint_id, map_id)` → `path_waypoint(id, map_id)` ON DELETE CASCADE. `connection_point_path_attachment(connection_point_id PK, map_id, waypoint_id)` analogous (needs `UNIQUE(id, map_id)` on `connection_point`).
- **Rationale**: Removing a placement, room, connection point, map, or waypoint removes the attachment automatically; same-map rule holds by construction (FR-009/011, SC-005). Attaching "at a point on a segment" is done as a separate split step (D5), keeping attachment requests simple.
- **Alternatives**: Attach directly to an edge position (requires t-parameter and complicates routing); nullable `waypoint_id` columns on existing tables (loose coupling to 012 tables, no map consistency).

## D5 — Splitting a segment
- **Decision**: `POST /api/maps/{mapId}/segments/{segmentId}/split` with a position (projected onto the segment server-side) deletes the segment and creates a waypoint plus two segments in one transaction; returns the new waypoint.
- **Rationale**: FR-003 atomically; attaching rooms/connection points mid-corridor is then "split, then attach".

## D6 — Connectivity and issues are computed on read
- **Decision**: `PathNetworkIssueChecker` runs union-find over a map's waypoints/segments and returns: `components` (count + waypoint ids per component), `missingScale`, `unattachedRooms` (placed rooms without attachment), `unattachedConnectionPoints`, and `attachmentsOutsideMainComponent` (attachments whose waypoint is not in the largest component). A connection is `fullyAttached` when all its points are attached.
- **Rationale**: Up to a few hundred waypoints per map; cheap, never stale, nothing to migrate. Serves US5 and gives 024 a quick sanity check.
- **Alternatives**: Persisted component ids (stale after edits, trigger logic).

## D7 — Read model for routing (024)
- **Decision**: `GET /api/maps/{mapId}/path-network` returns one map's waypoints, segments (with `lengthMeters`), reference segment, `scale`, room attachments, connection-point attachments, and `issues`. `GET /api/path-network` returns all maps' networks plus `connectionGroups` (per connection: points with `mapId`, `connectionPointId`, `waypointId|null`) so a router can link waypoints across maps. Both read-only, `READ` permission, one query per table.
- **Rationale**: FR-015, US6; avoids N+1 and avoids 024 re-implementing joins. Vertical length between floors is out of scope; consumers treat group members as zero/constant-cost links per their own rules.
- **Alternatives**: Re-use `GET /api/maps/{id}` and extend it (grows an existing contract; breaking risk).

## D8 — Authorization
- **Decision** (user decision): Path network writes are coupled to the existing `PermissionCode.MAP_MANAGE`; no new permission and no change to `role_permission`. `RoleAccessFilter` gets `READ` for the two GET endpoints and `MAP_MANAGE` for all write endpoints (waypoints, segments, split, reference segment, room and connection-point attachments). Reads remain `authenticated()` in `SecurityConfig`. The permission label/description in `PermissionCode` ("Karten verwalten") is widened to mention the path network.
- **Rationale**: Whoever manages maps also maintains their walkways; avoids a new permission and migration. ADMIN already holds `MAP_MANAGE` (spec: only admins write).
- **Alternatives**: Dedicated `PATH_NETWORK_MANAGE` (finer role control, rejected by the user).

## D9 — Validation rules
- **Decision**: Coordinates in [0,1] (bean validation + DB CHECK); coincident waypoints (distance < 0.002 of the map's relative diagonal) rejected with a hint to reuse the existing one; reference length > 0, ≤ 100 000 m, endpoints distinct (distance > 0.002); segment creation rejects self, duplicate, cross-map. Crossings without a shared waypoint are not connected; frontend shows a hint only.
- **Rationale**: Spec edge cases; thresholds are constants in one place.

## D10 — Concurrency
- **Decision**: Last write wins, no version columns; other editors see changes after refresh.
- **Rationale**: Matches the 012 placement rule and the spec edge case.

## D11 — Frontend
- **Decision**: Inline SVG overlay inside the MapCanvas container with `viewBox="0 0 1 1" preserveAspectRatio="none"` and `vector-effect: non-scaling-stroke`; tools: add waypoint, connect (click two), move (drag), split, delete, reference segment, attach room / connection point. Overlay toggle is per-viewer and read-only for users without the permission.
- **Rationale**: No dependency; shares the 0..1 coordinate frame with 012 markers; zoom/pan reuse the existing canvas transform.
- **Alternatives**: Canvas/Leaflet/graph libraries (new dependency without need).
