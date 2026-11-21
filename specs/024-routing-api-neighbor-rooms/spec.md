# Feature Specification: Routing and Neighbor Rooms on the Door Sign

**Feature Branch**: `024-routing-api-neighbor-rooms`

**Created**: 2026-11-21

**Status**: Draft

**Input**: User description: "Feature 024 – Routing-API und Nachbarräume auf dem Türschild: Based on the path network of Feature 023, the backend calculates shortest routes between rooms, including across floors via stairs and elevators. A read-only endpoint returns, for a room, the nearest neighboring rooms to the left and right on the same corridor with direction (left, right, straight, up, down) and distance in meters; additionally the distance and estimated walking time between any two rooms. The existing door sign (/rooms/:roomId/display, Feature 005, event title from 022) shows these neighbors with the existing Wayfinding UI building block, including the occupancy status of the neighbors where available. Read access for signed-in sessions and displays; no write access. Results must be reusable by the floor display (026). Out of scope: one-time-code sign-in for displays (025), the floor display itself (026), and visual redesign of the door sign. Prerequisite: Feature 023 is merged."

## Clarifications

### Session 2026-11-21

- Q: Weighting of stairs and elevators? → A: Fixed time-equivalent costs per floor change; stairs and elevators are not distance-based because vertical distance is not captured (023). Defaults: stairs 15 s per floor, elevator 30 s per floor change (waiting plus ride). Walking speed 1.2 m/s. Values are defaults, not configurable in this feature.
- Q: "Left/right" from whose perspective? → A: From a person standing in the corridor in front of the room's door and facing it, i.e. the person reading the door sign. Left/right on the sign is what that person sees.
- Q: Number of neighbors per side? → A: Up to 3 per side, plus, per vertical direction (up, down), at most one stairs entry and one elevator entry.
- Q: Which rooms count as neighbors? → A: The nearest rooms on the same floor by walking distance, including rooms beyond junctions; left or right comes from the first step of the route. No junction cutoff and no distance cap.
- Q: Show walking time for neighbors on the door sign? → A: Yes, each neighbor entry shows distance in meters and estimated walking time (also in the neighbor result for other consumers).
- Q: How are stairs and elevators shown on the door sign? → A: Always both: the nearest stairs and the nearest elevator are listed separately as "up" and "down" entries, labeled by type. "Accessible only" applies to route requests only, not to the sign.
- Q: How are "up" and "down" determined, since floors have no order? → A: Administrators arrange the floors of each building as a sortable list on the locations page (highest floor on top); the lowest entry is automatically the ground floor. This replaces the manual ground-floor checkbox. Connections between different buildings are not supported: they are neither used for routes nor listed as up/down entries.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See Neighboring Rooms on the Door Sign (Priority: P1)

As a visitor standing in front of a room, I want the door sign to show which rooms lie to my left and to my right along the corridor, with their distance, so that I can find the next room without a floor plan.

**Why this priority**: This is the visible value of the feature and the reason the routing exists.

**Independent Test**: Open the door sign of a room whose floor has a network, scale and attached rooms; verify that left and right neighbors appear with names, directions and distances in meters, and that the sign of a room without a usable network looks as before.

**Acceptance Scenarios**:

1. **Given** a room attached to a network with a scale and other attached rooms on the same corridor, **When** the door sign is opened, **Then** it shows up to 3 nearest rooms on the left and up to 3 on the right, each with direction, room name, distance in meters and estimated walking time, ordered by increasing distance.
2. **Given** a neighbor room currently has an occupancy status (free, occupied, reserved), **When** the door sign is shown, **Then** the neighbor entry shows that status; **Given** no status is available, **Then** the entry shows none.
3. **Given** the room has an elevator or stairs reachable on the same floor, **When** the door sign is shown, **Then** it lists the nearest stairs and the nearest elevator separately, each labeled by type, as "up" and/or "down" entries (where a floor exists in that direction) with distance and walking time.
4. **Given** the room is not attached, its map has no scale, or no neighbors can be found, **When** the door sign is opened, **Then** the neighbor area is omitted (no estimates) and the rest of the sign (status, event title, etc.) is unchanged.
5. **Given** the neighbor data changes (e.g. status), **When** the door sign refreshes as it already does, **Then** the neighbor area is updated consistently with the rest of the sign.

---

### User Story 2 - Route and Walking Time Between Two Rooms (Priority: P1)

As a signed-in user or a consuming feature, I want to ask for the route between any two rooms and receive the distance in meters and an estimated walking time, so that people can judge how far a room is.

**Why this priority**: The shortest-route calculation is the foundation for neighbors and for the later floor display.

**Independent Test**: Request the route between two rooms on the same floor and between rooms on different floors; verify distance, time and the sequence of steps; verify error responses for unroutable cases.

**Acceptance Scenarios**:

1. **Given** two attached rooms on the same floor in the same network part, **When** a route is requested, **Then** the result contains total distance in meters, estimated walking time, and the ordered steps (waypoints on the way, per floor).
2. **Given** two rooms on different floors joined by stairs or an elevator, **When** a route is requested, **Then** the shortest route (distance plus floor-change cost) is returned, including which connection is used and each floor change.
3. **Given** both stairs and an elevator are available, **When** the option "accessible only" is set, **Then** only elevators are used; **Given** no elevator route exists, **Then** a clear "no accessible route" result is returned instead of a stair route.
4. **Given** a room that is unattached, on a map without scale, or in a separate network part, **When** a route is requested, **Then** a specific error says which condition applies; no estimate is produced.
5. **Given** an unknown room, **Then** a "not found" error is returned.
6. **Given** a request from an unauthenticated caller, **Then** access is denied; **Given** a signed-in session or a display, **Then** access is allowed; there is no way to change data via this feature.

---

### User Story 3 - Neighbors via a Read-Only Service for Other Consumers (Priority: P2)

As the later floor display (026) and other consumers, I need the neighbor and route results in a stable, reusable form so that they don't recompute routes themselves.

**Why this priority**: Explicitly required for reuse; it carries no extra end-user UI.

**Independent Test**: Request neighbors for several rooms and routes between pairs; verify the same result structure (direction, target room, distance, status) is returned and that results reflect current network edits.

**Acceptance Scenarios**:

1. **Given** a room, **When** neighbors are requested, **Then** the result lists entries with direction (left, right, straight, up, down), target room or connection, and distance in meters, plus per-room status where available.
2. **Given** the administrator changes the network (023), **When** results are requested afterwards, **Then** they reflect the change without a manual refresh step.
3. **Given** a map without a scale, **When** neighbors are requested, **Then** the result states "scale missing" instead of values.

---

### Edge Cases

- Room attached to a dead-end: only one side has neighbors; the other is empty and omitted.
- Corridor with a junction: neighbors are classified by the direction of the first step of their shortest route; rooms beyond the junction may appear as left, right or straight, and the nearest by walking distance win.
- A room whose door faces a wall-less open area or whose attachment is on a waypoint with several outgoing segments: direction is decided from the side-of-travel of the first segment relative to the facing direction; straight is used when it continues within a small tolerance of the facing direction.
- Network part isolated from other parts: rooms in other parts are never returned as neighbors or route targets.
- A connection (stairs/elevator) whose points are not all attached, or whose points lie in different buildings: it is not used for routes and not listed as up/down. Routes between rooms of different buildings are answered as not connected.
- Route from a room to itself: distance 0 with an empty step list.
- Rooms on a floor whose scale is missing but on a route that crosses it: the route is rejected as "scale missing" for that floor.
- Occupancy status of a neighbor is unavailable or fails to load: the neighbor is shown without status; the sign still renders.
- Door sign shown on a small display or in kiosk mode: at most 3 neighbors per side keeps the layout readable; long names are shortened.
- Many requests in a short time (several displays refreshing): results remain fast and consistent.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST calculate the shortest route between two rooms over the path network of Feature 023, using segment lengths in meters, attachments of rooms and connection points, and linked connection points across floors.
- **FR-002**: System MUST support routes across floors via stairs and elevators, adding a fixed cost per floor change (default: stairs 15 s, elevator 30 s per floor change) so that the shortest route is the one with the lowest total walking time.
- **FR-003**: System MUST offer an "accessible only" option that excludes stairs from the route and neighbor calculation.
- **FR-004**: System MUST report distance in meters and estimated walking time (default walking speed 1.2 m/s plus floor-change costs) for every route.
- **FR-005**: System MUST return the route's ordered steps, including floor changes and the connection used.
- **FR-006**: System MUST, for a given room, determine the nearest neighboring rooms on the same floor that lie to the left and to the right from the perspective of a person standing in front of the room's door and facing it, returning up to 3 per side ordered by walking distance; rooms beyond junctions are included, with the side decided by the first step of their shortest route, and there is no junction cutoff or maximum distance.
- **FR-007**: System MUST return for each neighbor its direction (left, right, straight, up, down), the target (room or connection), the distance in meters and the estimated walking time; up/down entries lead to the nearest usable stairs and the nearest usable elevator in that direction, listed separately and labeled by type.
- **FR-008**: System MUST include the current occupancy status of neighbor rooms where one exists.
- **FR-009**: System MUST NOT produce estimates for rooms that are unattached, on maps without scale, or in separate network parts; it MUST return a specific, human-readable reason instead.
- **FR-010**: System MUST return clear errors for unknown rooms and for unreachable targets (including "no accessible route").
- **FR-011**: The existing door sign MUST show the neighbors with the existing Wayfinding building block (direction, target, distance, walking time) and the neighbor's status, and MUST omit the neighbor area entirely when no neighbors can be shown, leaving the rest of the sign unchanged.
- **FR-012**: System MUST restrict this functionality to read access: signed-in sessions and authenticated displays may read; unauthenticated requests MUST be denied; no data can be created, changed or deleted through it.
- **FR-013**: Results MUST be offered in one consistent form usable by other features (notably the floor display, 026), and MUST always reflect the current network, scale and attachment data.
- **FR-014**: System MUST NOT include one-time-code sign-in for displays (025), the floor display (026), a visual redesign of the door sign, or routes between different buildings.
- **FR-015**: Administrators MUST be able to arrange the floors of a building in a sortable order on the locations page; the lowest floor is automatically the ground floor (replacing the manual ground-floor setting), and up/down directions are derived from this order. Connections whose points lie in different buildings are not supported and are ignored by routing and neighbors.

### Key Entities *(include if feature involves data)*

- **Route**: Result of a route request between two rooms: total distance (m), estimated time, option used (accessible only or not), ordered steps with floor and waypoint information, and floor changes with the connection used. Derived, not stored.
- **Neighbor Entry**: One item of a room's neighbor list: direction (left, right, straight, up, down), target room or connection, distance (m), estimated walking time, optional occupancy status. Derived, not stored.
- **Routing Problem**: A specific reason why a result cannot be produced: room unknown, room unattached, scale missing, no connection between network parts, no accessible route.
- **Path Network, Room Attachment, Connection Point Attachment, Scale**: Existing data from Feature 023; used unchanged.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a floor with a complete network, 100% of rooms with at least one other attached room on the same floor show at least one neighbor on their door sign.
- **SC-002**: For a set of reference routes with known lengths, the reported distance deviates by no more than 1% from the sum of the network's segment lengths, and the returned route is never longer (by walking time) than any alternative path found by exhaustive comparison.
- **SC-003**: A visitor can name the direction and approximate distance of the nearest neighboring room within 10 seconds of reading the door sign (observed in a test with at least 5 people).
- **SC-004**: A neighbor list or route result is available in under 1 second for a network of 500 waypoints per floor across 5 floors.
- **SC-005**: 100% of requests for rooms without attachment, without scale, or in separate network parts produce a clear reason rather than a distance, and the corresponding door signs show no neighbor area.
- **SC-006**: With "accessible only" set, 0% of returned routes use stairs.
- **SC-007**: A change to the network by an administrator is reflected in the next result requested after saving.
- **SC-008**: All neighbor and route data for a floor display (026) can be obtained from the same results without further calculation by the consumer.

## Assumptions

- Builds on Feature 023 (network, scale, attachments, connection groups), Feature 012 (maps, placements, connections), Feature 005 (door sign, display route) and Feature 022 (event title); the existing role model and display authentication apply unchanged.
- Floors are linked only by the connection groups of Feature 012/023; vertical distance is not modeled, hence fixed floor-change costs. Cost and speed values are defaults and not user-configurable in this feature.
- "Left/right" is determined geometrically from the room's marker position relative to its attachment on the map; no additional orientation setting per room is introduced.
- Neighbor candidates are limited to the same floor; stairs and elevators are listed as separate, type-labeled up/down entries; the door sign does not apply "accessible only".
- Occupancy status comes from the existing status source of the door sign (Feature 005/009); where none exists, nothing is shown.
- Results are computed on request from the current data; no persistent storage of routes.
- Door signs refresh through their existing mechanism; no new live-update channel is added.
- One-time-code sign-in for displays (025) is not part of this feature; displays are assumed to be authenticated as in Feature 005.
- Routing works within one building; there are no building-to-building routes (no bridges, tunnels or outdoor paths).
