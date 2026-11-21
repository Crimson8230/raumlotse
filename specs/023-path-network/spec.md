# Feature Specification: Path Network (Wegenetz)

**Feature Branch**: `023-path-network`

**Created**: 2026-11-21

**Status**: Draft

**Input**: User description: "Feature 023 – Wegenetz: Auf der Karte jeder Etage (Feature 012) kann ein Admin Wege als Linien zeichnen (Wegpunkte und Kanten). Räume (room_placement) und Etagenverbindungen (connection_point, Treppen/Aufzüge) werden an das Wegenetz angebunden. Pro Karte wird ein Maßstab (Pixel pro Meter) erfasst, damit Strecken in Metern berechenbar sind. Wegberechnung, Nachbarräume für das Türschild und die Etagenanzeige sind spätere Features (024–026) und nicht Teil von 023. Die Daten müssen so exponiert werden, dass die Routing-API (024) sie nutzen kann."

## Clarifications

### Session 2026-11-21

- Q: Wie wird der Maßstab einer Karte erfasst? → A: Der Admin zeichnet eine Referenzstrecke (zwei Endpunkte) auf die Karte und trägt dafür die reale Länge in Metern ein; der Maßstab (Pixel pro Meter) wird daraus abgeleitet. Die Referenzstrecke bleibt auf der Karte gespeichert und sichtbar, genau eine pro Karte.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Draw Walkways on a Floor Map (Priority: P1)

As an administrator, I want to draw the walkways of a floor on its map as lines made of waypoints and connecting segments so that the real walkable structure of the floor (corridors, junctions, doorways) is recorded.

**Why this priority**: The drawn network is the core data of this feature and the prerequisite for any later route calculation.

**Independent Test**: Open a floor map, add several waypoints, connect them to form a corridor with a junction, save, reload, and verify that the same network appears.

**Acceptance Scenarios**:

1. **Given** a map without a path network, **When** the administrator adds waypoints by clicking on the map and connects them, **Then** the waypoints and connecting segments are shown as lines on the map and saved.
2. **Given** an existing network, **When** the administrator moves a waypoint, **Then** all segments attached to it follow and the new position persists after reload.
3. **Given** an existing network, **When** the administrator deletes a segment, **Then** only that segment is removed; **When** they delete a waypoint, **Then** the waypoint and all its segments are removed.
4. **Given** an existing segment, **When** the administrator adds a waypoint on a segment, **Then** the segment is split into two segments joined by the new waypoint.
5. **Given** a signed-in user without administrator rights, **When** they open the map, **Then** they can see the network but cannot change it.
6. **Given** an unauthenticated visitor, **When** they request path network data, **Then** access is denied.

---

### User Story 2 - Set the Map Scale (Priority: P1)

As an administrator, I want to record the scale of each map (pixels per meter) so that the length of any segment can be expressed in meters.

**Why this priority**: Without a scale, distances are meaningless for later routing and walking-time estimates.

**Independent Test**: Draw a reference segment on a map, enter its real length in meters, then verify that the lengths of drawn segments are shown in meters and match expectations.

**Acceptance Scenarios**:

1. **Given** a map without a scale, **When** the administrator draws a reference segment between two points on the map and enters its real-world length in meters, **Then** the reference segment is stored with its length value and the scale (pixels per meter) of the map is derived from it.
2. **Given** a map with a scale, **When** the administrator selects a segment, **Then** its length in meters is shown.
3. **Given** a map with a reference segment, **When** the administrator moves its endpoints, changes its length value, or deletes it, **Then** the scale is recalculated (or removed when deleted) and all segment lengths of that map are recalculated consistently.
   - **Given** a map with a reference segment, **When** the map is viewed, **Then** the reference segment is shown visibly distinct from walkway segments, labeled with its length in meters, and is not part of the walkway network.
4. **Given** a map without a scale, **When** the administrator views the network, **Then** the map is flagged as "scale missing" and segment lengths are shown as unavailable.
5. **Given** an entered length that is zero, negative, or not a number, or a reference segment with identical endpoints, **When** the administrator saves, **Then** the input is rejected with a clear message.

---

### User Story 3 - Attach Rooms to the Network (Priority: P1)

As an administrator, I want to attach each placed room (Feature 012) to the path network at its entrance so that a later route feature knows where to start or end a walk to that room.

**Why this priority**: Routes lead to rooms; an unattached room cannot be a route endpoint.

**Independent Test**: Place a room, attach it to a waypoint of the network, reload, and verify the attachment; then check that the room is flagged as unattached before.

**Acceptance Scenarios**:

1. **Given** a placed room and a network on the same map, **When** the administrator attaches the room to a waypoint (or to a point on a segment, which creates a waypoint there), **Then** the attachment is shown as a short connecting line and saved.
2. **Given** an attached room, **When** the administrator attaches it elsewhere or removes the attachment, **Then** the previous attachment is replaced or removed.
3. **Given** a map with placed rooms, **When** the administrator opens the network view, **Then** placed rooms without attachment are clearly marked and listed.
4. **Given** a placed room is moved on the map, **When** it is attached, **Then** the attachment remains and the connecting line follows the new marker position.
5. **Given** a room's placement is removed or the room is deleted, **Then** its attachment is removed as well.

---

### User Story 4 - Attach Stairs and Elevators to the Network (Priority: P2)

As an administrator, I want to attach the connection points of stairs and elevators (Feature 012) to the path network of their map so that later routes can switch between floors.

**Why this priority**: Multi-floor routes require that passages are reachable from the walkways of both floors; single-floor routing works without it.

**Independent Test**: For an elevator with points on two maps, attach each point to the network of its map and verify that the connection is reported as usable on both floors.

**Acceptance Scenarios**:

1. **Given** a connection point on a map with a network, **When** the administrator attaches it to a waypoint, **Then** the attachment is shown and saved.
2. **Given** a connection whose points are all attached, **Then** the connection is flagged as usable for later routing; **Given** at least one unattached point, **Then** it is flagged as not fully attached.
3. **Given** an attached connection point, **When** the point is moved, removed, or its map or connection is deleted, **Then** the attachment is updated or removed with it, leaving no dangling references.

---

### User Story 5 - Check Network Completeness (Priority: P2)

As an administrator, I want an overview of problems in the network so that I can fix gaps before the routing feature relies on it.

**Why this priority**: Faulty networks would later cause missing or wrong routes; early visibility saves debugging.

**Independent Test**: Create a network with an isolated sub-network, an unattached room, and a map without scale; verify that all three problems are reported.

**Acceptance Scenarios**:

1. **Given** a map whose network consists of several separate parts, **When** the administrator opens the check, **Then** the separate parts are reported and highlighted.
2. **Given** a map, **Then** the check lists: missing scale, unattached placed rooms, unattached connection points, and rooms or connection points sitting in a different network part than the rest.
3. **Given** all problems are fixed, **Then** the map is shown as complete.

---

### User Story 6 - Consume the Network from Other Features (Priority: P2)

As the later routing feature, I need to read the complete network (waypoints, segments with lengths in meters, room and connection attachments, connection groups across maps) in a single consistent form so that I can calculate routes without knowing how the data was drawn.

**Why this priority**: Explicitly required so that Feature 024 can build on this one; it carries no end-user UI of its own.

**Independent Test**: Request the network for one floor and for all floors; verify that the result contains every waypoint, segment, attachment and scale, and that it is rejected for unauthenticated callers.

**Acceptance Scenarios**:

1. **Given** a map with a network and scale, **When** an authenticated consumer requests it, **Then** it receives waypoints, segments with lengths in meters, attached rooms and attached connection points.
2. **Given** a map without a scale, **When** it is requested, **Then** the result states that the scale is missing and does not return invented meter values.
3. **Given** an authenticated non-admin session (e.g. a signed-in user or a display), **When** it requests network data, **Then** it gets read-only access.
4. **Given** several maps joined by connections, **When** the whole network is requested, **Then** the result lets a consumer see which waypoints of different maps are linked via a connection.

---

### Edge Cases

- A waypoint is placed outside the map bounds: it is rejected and a hint is shown.
- Two waypoints at (nearly) the same point: the system prevents creating a segment of zero length and offers to reuse the existing waypoint.
- A segment is created twice between the same two waypoints, or from a waypoint to itself: rejected.
- Segments cross each other without a shared waypoint: they are NOT treated as connected; a hint suggests adding a waypoint at the crossing.
- The map image is replaced with the same aspect ratio: waypoints keep their relative positions; the reference segment keeps its relative position, and the system warns that its length value may need to be re-checked.
- The map image is replaced with a different aspect ratio: the system warns that the network may no longer match reality (as in Feature 012).
- A map is deleted: its network, scale, and all attachments are removed; connections lose their points as already defined in Feature 012.
- A room moves to another floor or loses its placement: its attachment is removed.
- Two administrators edit the network concurrently: the last saved change wins; the other sees updated data after refresh.
- A large network (hundreds of waypoints on one map): drawing and viewing remain usable.
- The map is viewed on a small screen: the network is visible and can be panned and zoomed; editing is intended for larger screens.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Administrators MUST be able to add, move, and delete waypoints on a map.
- **FR-002**: Administrators MUST be able to connect two waypoints of the same map with a segment and delete segments; segments are undirected, unique per waypoint pair, and cannot connect a waypoint to itself.
- **FR-003**: System MUST allow inserting a waypoint onto an existing segment, splitting it into two segments.
- **FR-004**: System MUST persist waypoints and segments with positions relative to the map, so they stay correctly placed at any zoom level, screen size, or image resolution.
- **FR-005**: System MUST validate that waypoint positions lie within the map bounds and that segments only join waypoints of the same map.
- **FR-006**: Administrators MUST be able to set the scale of a map (pixels per meter), by drawing exactly one reference segment on the map and entering its real length in meters; the scale is derived from the segment's on-map length and the entered value; the system MUST reject non-positive or invalid input and identical endpoints; the reference segment MUST stay stored and visible, and MUST NOT take part in the walkway network.
- **FR-007**: System MUST show the length of each segment in meters when the map has a scale, and MUST clearly state that lengths are unavailable when it does not.
- **FR-008**: Administrators MUST be able to attach a placed room to the network (at a waypoint, or at a point on a segment that becomes a waypoint); a room has at most one attachment.
- **FR-009**: System MUST only allow attaching a room that has a placement on the same map and MUST remove the attachment when the placement or the room is removed.
- **FR-010**: Administrators MUST be able to attach each connection point (stairs/elevator) to the network of its map; a connection point has at most one attachment.
- **FR-011**: System MUST keep the network consistent when waypoints, segments, rooms, placements, connection points, connections, or maps are deleted: no segment without both end waypoints, and no attachment without its target.
- **FR-012**: System MUST show, per map, which placed rooms and connection points are not yet attached and which parts of the network are not connected to each other.
- **FR-013**: System MUST flag a connection as fully attached only when all its connection points are attached.
- **FR-014**: System MUST restrict creating, changing, and removing waypoints, segments, scales, and attachments to administrators; every other authenticated session (signed-in users, and displays once authenticated) MUST have read-only access; unauthenticated requests MUST be denied.
- **FR-015**: System MUST expose the path network of one map and of all maps — waypoints, segments with meter lengths where a scale exists, scales, room attachments, connection-point attachments, and the groups of linked connection points across maps — in one consistent, read-only form that the later routing feature can consume.
- **FR-016**: System MUST show the network as an optional overlay on the existing map view, toggleable, without disturbing existing room and connection markers.
- **FR-017**: System MUST NOT calculate routes, list neighboring rooms for door signs, or show floor displays in this feature.

### Key Entities *(include if feature involves data)*

- **Map**: Existing entity (Feature 012); gains an optional reference segment, from which its scale (pixels per meter) is derived.
- **Reference Segment**: Two endpoints on one map (relative to the map's dimensions) plus the entered real length in meters; at most one per map; not a walkway.
- **Waypoint**: A point of the walkway network on exactly one map, positioned relative to the map's dimensions.
- **Segment**: An undirected line between two waypoints of the same map; its length in meters is derived from the positions and the map's scale.
- **Room Attachment**: The link between one room placement and the network (a waypoint) marking where the room is entered; at most one per room.
- **Connection Point Attachment**: The link between one connection point (stairs/elevator on a map) and a waypoint of the same map; at most one per connection point.
- **Room Placement / Connection / Connection Point**: Existing entities (Feature 012), unchanged except for the optional attachment.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An administrator can draw the network of a typical floor (about 30 rooms) and attach all its rooms in under 30 minutes.
- **SC-002**: 100% of waypoints and segments appear at the same relative position after a reload, a restart, and on screens of different sizes.
- **SC-003**: An administrator can set the scale of a map by drawing a reference segment in under 1 minute, and displayed lengths of walkway segments laid over the same stretch deviate by no more than 5% from the entered reference length.
- **SC-004**: An administrator can identify all attachment and connectivity problems of a map within 10 seconds of opening the check.
- **SC-005**: After deleting a waypoint, room, connection point, or map, zero dangling segments or attachments remain.
- **SC-006**: A consumer can retrieve the full network of a floor with meter lengths in a single request that completes in under 2 seconds for 500 waypoints.
- **SC-007**: 90% of administrators draw and attach their first room correctly on the first attempt without assistance.

## Assumptions

- This feature builds on Feature 012 (maps, room placements, connections, connection points) and its role model; the new data belongs to the existing maps.
- Segments are straight lines between waypoints; curved paths are drawn with several waypoints. Segments are walkable in both directions; one-way paths, accessibility attributes, and walking-time estimates are out of scope.
- A room is attached at a single entry waypoint; doors with several entrances are not modelled.
- Vertical distance of stairs/elevators (length or time between floors) is not captured here; only that points of one connection are linked across maps (as in Feature 012).
- The scale is one value per map, derived from its single reference segment (uniform in both axes); a map without a scale remains usable for drawing, but meter-based consumers receive an explicit "scale missing" state.
- Read access follows Feature 012: all authenticated sessions may read; only administrators write; anonymous access is denied.
- Route calculation (024), neighboring rooms for door signs (025), and the floor display (026) are separate later features and out of scope.
- Editing the network is designed for desktop-size screens; viewing works on all screen sizes.
