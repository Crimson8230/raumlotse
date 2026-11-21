# Research: Routing and Neighbor Rooms

## Decision 1: Data source for the graph
- **Decision**: Build the graph from the existing 023 read model (`PathNetworkService` network-wide result): waypoints, segments with `lengthMeters`, room attachments, connection groups.
- **Rationale**: 023 was built for exactly this; no second interpretation of the drawing data. Maps without scale have null lengths and are excluded from routing.
- **Alternatives**: Query the tables directly (duplicates derivation of lengths and scale); persist a precomputed graph (cache invalidation on every edit, rejected by Simplicity).

## Decision 2: Algorithm and cost
- **Decision**: Dijkstra with `PriorityQueue`; cost = walking seconds. Segment cost = meters / 1.2 m/s. Connection edges between attached points of one connection on different maps: stairs 15 s × floor steps, elevator 30 s per change (any number of floors).
- **Rationale**: One consistent cost makes "shortest" mean "fastest" and lets stairs compete with elevators fairly. Distances are reported separately as the sum of segment meters.
- **Alternatives**: Meters-only cost (cannot weigh floor changes); A* (graph is small, no gain).
- **Constants**: Defined once in a `RoutingDefaults` holder; not configurable (spec assumption).

## Decision 3: Floor order (up/down)
- **Finding**: `floor` has only name, status and `ground_floor`; no order. Up/down needs one.
- **Decision (user)**: Add `floor.position` (int, 0 = ground floor, increasing upward), edited as a sortable list on `/admin/locations` (highest floor on top, lowest entry at the bottom is the ground floor). Ground floor becomes derived (`position == 0`); the checkbox from Feature 008 is removed; `FloorResponse.groundFloor` stays and stays correct for existing consumers (room overview, filters).
- **Rationale**: Order, not numbers, matches how admins think ("above/below") and avoids inconsistent level numbers.
- **Backfill**: Per building, floors flagged `ground_floor` first, then the rest by name; positions 0..n-1. New floors are appended on top.
- **UI**: Up/down buttons (keyboard accessible); no drag-and-drop library.
- **Cross-building connections (not supported, per user)**: Connections whose points lie in different buildings are ignored by the graph and by neighbors; a route between rooms of different buildings is answered `NOT_CONNECTED`. Floor positions are therefore only ever compared within one building.
- **Alternatives**: Level numbers (rejected by user); keep checkbox (cannot order); derive from name (unreliable).

## Decision 4: Left/right/straight
- **Decision**: Facing direction `f` = room marker − attachment waypoint (the way a person in the corridor looks at the door). `left = (f.y, −f.x)` in map coordinates (y down). The first step of the shortest path leaves the attachment waypoint along vector `s`; side = sign of `s · left`; `straight` when the angle between `s` and `f` is ≤ 20° (this resolves the open tolerance question); ties go to the right. Candidates attached to the same waypoint (distance 0) are classified by the vector between the two room markers.
- **Rationale**: Uses only data that exists (placements, attachments), no new setting.
- **Limitation**: A room attached right next to a long wall is fine; a marker placed on top of its waypoint (zero vector) yields no orientation, so the room gets no neighbor area (reason `NO_ORIENTATION`).

## Decision 5: Neighbor search
- **Decision**: Single-source Dijkstra from the room's waypoint restricted to the room's map; every other active room attached on that map is a candidate; keep the 3 nearest per side. Up/down: connection points attached on this map and reachable; per direction the nearest stairs and nearest elevator.
- **Rationale**: Matches the clarified rule (same floor, by walking distance, no junction cutoff).

## Decision 6: Problems and status codes
- **Decision**: Neighbors endpoint returns 200 with `available=false` and a reason code when no estimate is possible (the door sign just omits the area); unknown room → 404. Route endpoint returns 409 with the `Problem` format and codes `ROOM_NOT_ATTACHED`, `SCALE_MISSING`, `NOT_CONNECTED`, `NO_ACCESSIBLE_ROUTE`; unknown room → 404; missing/invalid parameters → 400.
- **Rationale**: The door sign must not treat a normal "not configured" case as a failure; a route request that cannot be answered is a conflict with the data state.

## Decision 7: Occupancy status of neighbors
- **Decision**: Reuse the status rule of `RoomStatusService` (OCCUPIED/RESERVED/AVAILABLE) for the ≤ 6 neighbor rooms, through a small bulk method; no status for up/down entries. A failure to read status leaves the status out.

## Decision 8: Access
- **Decision**: New `GET /api/rooms/{id}/neighbors` and `GET /api/routes` with permission READ in `RoleAccessFilter`, as the 023 reads. The door sign uses the same session as today.

## Decision 9: Frontend
- **Decision**: `RoomDisplayPage` loads neighbors beside status with `.catch(() => null)`; `RoomDisplay` gets an optional `neighbors` prop and renders a section with one `Wayfinding` per entry (German direction labels `links/rechts/geradeaus/hoch/runter`, target text, distance "ca. 40 m · 35 s" style) plus a status label. The Wayfinding block is not changed.
