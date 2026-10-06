# Feature Specification: Room Map Placement

**Feature Branch**: `012-room-map-placement`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Räume sollen auf einer Karte platziert werden können, um in einem späteren Feature Wege/Strecken auf Displays (ebenfalls auf der Karte platziert) anzeigen zu können."

## Clarifications

### Session 2026-10-06

- Q: Soll die Karte aus genau einem Grundrissbild bestehen oder sollen mehrere Karten unterstützt werden? → A: Mehrere Karten (z. B. je Stockwerk/Gebäude), und zusätzlich Verbindungen zwischen Karten (Treppen/Aufzüge) bereits in diesem Feature.
- Q: Wer darf Karten und Raumpositionen sehen? → A: Angemeldete Nutzer sowie Displays, die sich per Einmalcode anmelden; anonyme Besucher nicht. Schreibzugriff nur für Admins.
- Q: Wie werden Aufzüge/Treppenhäuser erfasst, die mehr als zwei Karten verbinden? → A: Als Gruppe: Ein benannter Aufzug/Treppenhaus enthält je einen Punkt pro Karte; alle Punkte der Gruppe sind untereinander verbunden.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Place a Room on the Map (Priority: P1)

As an administrator, I want to choose a map (e.g. a floor) and place a room at its actual position on that map so that the location of every room is recorded in a spatially accurate way.

**Why this priority**: The recorded room position is the core data this feature delivers and the prerequisite for any later route display.

**Independent Test**: Open the map, select an existing room, mark its position on the map, save, reload, and verify that the room appears at the same position.

**Acceptance Scenarios**:

1. **Given** a room that has no map position, **When** the administrator selects the room and clicks a point on the map, **Then** the room is shown as a marker at that point and the position is saved.
2. **Given** a room that is already placed, **When** the administrator drags its marker to a new point and confirms, **Then** the new position replaces the old one and persists after a reload.
3. **Given** a placed room, **When** the administrator removes its placement, **Then** the marker disappears and the room is again listed as not placed.
4. **Given** a signed-in user without administrator rights, **When** they open the map, **Then** they can view placed rooms but cannot add, move, or remove placements.
5. **Given** an unauthenticated visitor, **When** they request map data, **Then** access is denied.

---

### User Story 2 - See Which Rooms Are Placed (Priority: P1)

As an administrator, I want to see at a glance which rooms are already placed and which still lack a position so that I can complete the map without missing rooms.

**Why this priority**: Routes can only be calculated to rooms that have a position, so completeness must be easy to verify and drive.

**Independent Test**: Create several rooms, place some of them, and verify that the map shows markers with room names for the placed rooms and a list of unplaced rooms.

**Acceptance Scenarios**:

1. **Given** a mix of placed and unplaced rooms, **When** the map view is opened, **Then** each placed room appears as a labeled marker and every unplaced room appears in a separate list.
2. **Given** an unplaced room in the list, **When** the administrator selects it, **Then** the room becomes the active room to be placed on the map.
3. **Given** a room is placed, **When** the administrator selects its marker, **Then** the room's basic details (name and identifier) are shown.

---

### User Story 3 - Manage Maps (Priority: P1)

As an administrator, I want to create, replace, and delete maps (one per existing floor) so that the maps reflect the real buildings and can be updated when floor plans change.

**Why this priority**: Placement needs at least one map to exist, and multiple floors are the normal case.

**Independent Test**: Create two maps with floor plan images, verify both are selectable in the map view, replace one image, and verify that existing placements remain at their relative positions.

**Acceptance Scenarios**:

1. **Given** no map exists, **When** the administrator creates a map for a floor with a floor plan image, **Then** it appears in the map selection and is shown in the map view.
2. **Given** several maps exist, **When** a user switches between them, **Then** each map shows only the rooms placed on it.
3. **Given** a map with placed rooms, **When** the administrator replaces the image with one of a different resolution but the same aspect ratio, **Then** markers keep their relative positions.
4. **Given** an unsupported or oversized file, **When** the administrator tries to use it as the map, **Then** the system rejects it with a clear message and keeps the current map.
5. **Given** no map has been provided, **When** any user opens the map view, **Then** an explanatory empty state is shown instead of an error.
6. **Given** a map with placed rooms, **When** the administrator deletes the map and confirms, **Then** the map, its placements, and its connections are removed and the affected rooms are again listed as not placed.

---

### User Story 4 - Keep Placement Consistent with Room Changes (Priority: P2)

As an administrator, I want placements to follow the lifecycle of rooms so that the map never shows stale or orphaned rooms.

**Why this priority**: Stale map data would later lead to wrong routes.

**Independent Test**: Rename and delete a placed room and verify that the map reflects the change.

**Acceptance Scenarios**:

1. **Given** a placed room, **When** the room is renamed, **Then** the marker label shows the new name.
2. **Given** a placed room, **When** the room is deleted, **Then** its marker is removed from the map.
3. **Given** a newly created room, **When** the map is opened, **Then** the room appears in the list of unplaced rooms.

---

### User Story 5 - Connect Maps via Stairs and Elevators (Priority: P2)

As an administrator, I want to define stairs and elevators as named connections and mark where they appear on each map so that later route calculations can lead across floors and buildings.

**Why this priority**: Routes spanning several maps are impossible without recorded connections, and the decision was made to capture them in this feature.

**Independent Test**: Create a named elevator, add a point for it on the ground-floor map and on the first-floor map, and verify that each map shows the point and names the other maps the elevator reaches.

**Acceptance Scenarios**:

1. **Given** an existing connection and a map, **When** the administrator places the connection's point on the map, **Then** it appears as a distinct marker (stairs or elevator) labeled with the connection's name.
2. **Given** a connection that already has points on two maps, **When** the administrator adds a point for it on a third map, **Then** all three points count as mutually connected and each map shows the other maps the connection reaches.
3. **Given** a connection with several points, **When** the administrator moves or removes one point or deletes its map, **Then** the remaining points stay connected; a connection left with fewer than two points no longer counts as a passage and is flagged as incomplete.
4. **Given** a user without administrator rights, **When** they view a map, **Then** they see connection points but cannot change them.

---

### Edge Cases

- A room is selected for placement and the administrator clicks outside the map area: no position is stored and a hint is shown.
- Two rooms are placed at (nearly) the same point: both are stored, markers remain individually selectable.
- Two administrators edit the same room's placement concurrently: the last saved position wins and the other administrator sees the updated position after refresh.
- The map is viewed on a small screen: markers remain selectable and the map can be panned and zoomed.
- A second point of the same connection is added to a map that already has one: the system rejects it (at most one point per connection per map).
- A connection is deleted: all its points are removed from all maps.
- The map image is replaced with a different aspect ratio: the system warns that existing placements may no longer match reality.
- A placed room becomes inactive: its placement stays visible and is marked as inactive; inactive rooms are not offered in the unplaced list.
- A room is deleted while an administrator is placing it: the save fails with a clear message and no orphaned placement is stored.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a map view showing the selected map and let users switch between all existing maps.
- **FR-002**: Administrators MUST be able to place an existing room at a chosen point on the map.
- **FR-003**: Administrators MUST be able to move a placed room to a different point and to remove its placement.
- **FR-004**: System MUST persist each room's map position so that it is restored after reload and restart.
- **FR-005**: System MUST allow at most one map position per room.
- **FR-006**: System MUST display every placed room as a marker labeled with its name.
- **FR-007**: System MUST list all active rooms of the map's floor without a map position and let administrators pick one from the list to place it.
- **FR-008**: System MUST restrict creating, changing, and removing maps, placements, and connections to administrators; every other authenticated session (signed-in users, and displays once they have an authenticated session) MUST have read-only access; unauthenticated requests MUST be denied.
- **FR-009**: Administrators MUST be able to create (for an existing floor), replace the image of, and delete maps; the system MUST reject unsupported file types and files above a defined size limit with a clear message.
- **FR-010**: System MUST store positions independent of the displayed image size (relative to the map), so markers stay correctly positioned at any zoom level, screen size, or image resolution.
- **FR-011**: System MUST remove a room's placement when the room is deleted and reflect room renames on the marker label.
- **FR-012**: System MUST validate that a submitted position lies within the map bounds and refer to an existing room.
- **FR-013**: System MUST show an informative empty state when no map has been provided.
- **FR-014**: Each room MUST have at most one placement, and only on the map of its own floor.
- **FR-015**: Administrators MUST be able to add, move, and remove the point of an existing connection on a map.
- **FR-016**: Administrators MUST be able to create a named connection (type stairs or elevator) and add to it at most one point per map; all points of a connection are mutually connected, and each map MUST show which other maps a connection reaches.
- **FR-017**: System MUST keep connections consistent when a point, a map, or a connection is deleted: no point remains without its connection, and a connection with fewer than two points is flagged as incomplete.
- **FR-018**: System MUST expose maps, room positions, and connections with their points in a form a later route feature can consume.

### Key Entities *(include if feature involves data)*

- **Map**: The image of one floor or building that serves as a placement surface; belongs to exactly one floor (its name is derived from building and floor), has an image and its proportions, and can be replaced or deleted. Several maps may exist.
- **Room Placement**: The position of one room on a map, expressed relative to the map's dimensions; belongs to exactly one room and one map; at most one per room.
- **Connection**: A named stairs or elevator passage that spans two or more maps; has a type and a set of connection points.
- **Connection Point**: The position of a connection on one map, relative to the map's dimensions; at most one per connection per map. All points of the same connection are mutually connected.
- **Room**: Existing entity; gains an optional placement.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An administrator can place a room on the map in under 30 seconds, from opening the map to a saved position.
- **SC-002**: 100% of placed rooms appear at the same relative position after a page reload, a restart, and on screens of different sizes.
- **SC-003**: An administrator can identify all unplaced rooms within 5 seconds of opening the map view.
- **SC-004**: 95% of administrators complete placing their first room on the first attempt without assistance.
- **SC-005**: After a room is deleted, zero markers of that room remain on the map.
- **SC-006**: An administrator can create a floor map and add an existing stairs/elevator connection to it, so that it spans two floors, in under 3 minutes.
- **SC-007**: After deleting a map or connection point, zero orphaned connection points remain on any map.

## Assumptions

- Each map is one floor plan image bound to exactly one existing floor (a floor has at most one map; its displayed name is derived from building and floor name), so a room can only be placed on the map of its own floor; an arbitrary number of maps is supported.
- Connections only record where passages are and which maps they connect; path lengths, accessibility attributes, and route calculation are out of scope.
- The map is an uploaded floor plan image, not a geographic/GPS map.
- Only administrators manage maps, placements, and connections; all other authenticated sessions may view them. Anonymous access is not supported.
- The one-time-code sign-in for displays is a separate dependency and not delivered here; this feature only guarantees that any authenticated non-admin session can read.
- Placing **displays** on the map and calculating or showing **routes** are separate, later features and out of scope here; this feature only stores room positions so they can be reused.
- Supported map image formats are PNG and JPEG (SVG is excluded because it can carry active content) with a reasonable size limit (e.g. 10 MB).
- The feature builds on the existing room management and role/permission capabilities.
