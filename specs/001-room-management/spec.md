# Feature Specification: Room Management

**Feature Branch**: `001-room-management`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "Wir erstellen ein Buchungssystem für Meetingräume als Webapp. Wir beginnen als 1. Feature mit der Anlage von Räumen im System. Ein Raum besteht aus einem Namen, ist in einem Stockwerk und einem Gebäude. Er hat eine ID und verfügbare Ausstattung wie Projektor, Whiteboard, 1 bis n Sitzordnungen und davon abhängig eine max. Personenanzahl. Räume können angelegt, konfiguriert, ggfs. geändert und wieder gelöscht bzw. deaktiviert werden."

## Clarifications

### Session 2026-09-15

- Q: Does an authentication/authorization system already exist (or will one be built elsewhere) that this feature can rely on for the "administrator" role, or should room management for now assume a single implicit admin with no login/role-checking? → A: No auth system yet — treat this feature as accessible to a single implicit administrator for now; role enforcement is deferred to a future auth feature.
- Q: Should the equipment options (projector, whiteboard, etc.) come from a fixed, hard-coded list for now, or should administrators be able to add new equipment types themselves through the system? → A: Admin-manageable catalog — administrators can add/rename/remove equipment types through the system.
- Q: When two administrators edit the same room at the same time and both save, should the system detect the conflict and reject the second save, or should the second save simply overwrite the first (last write wins)? → A: Detect conflicts — reject the second save with an error if the room changed since it was loaded (optimistic locking).

### Session 2026-09-15 (change request)

- Q: Should Buildings and Floors become centrally managed entities (selected from an existing list or newly added, and separately updatable/deactivatable/deletable) instead of free-text strings on a room? → A: Yes — both Building and Floor become managed catalogs, following the same create/rename/deactivate/reactivate/delete pattern already used for the equipment catalog.
- Q: Should each Floor belong to exactly one Building, or should Floors be a single building-independent list shared by all buildings? → A: Each Floor belongs to exactly one Building — a room's floor choices are limited to the floors that exist under its selected building.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create a new room (Priority: P1)

A facility administrator adds a new meeting room to the system so that it becomes available for future booking. They provide the room's name, select an existing building and one of that building's existing floors (or first create the building/floor if it doesn't exist yet), pick equipment, and add at least one seating arrangement with its associated maximum capacity.

**Why this priority**: Without the ability to create rooms, no other room-related or downstream booking functionality can exist. This is the foundational capability of the entire system.

**Independent Test**: Can be fully tested by submitting a new room with valid name, building, floor, and at least one seating arrangement, and verifying the room appears in the system's room list with a unique ID and "active" status.

**Acceptance Scenarios**:

1. **Given** an administrator is on the room creation form, **When** they submit a valid name, select an existing active building and one of its active floors, and provide one seating arrangement with a max capacity, **Then** the system creates the room, assigns it a unique ID, marks it active, and confirms success.
2. **Given** an administrator is creating a room, **When** they add multiple seating arrangements (e.g., "Theater" with capacity 40 and "U-Shape" with capacity 20), **Then** the system stores each seating arrangement with its own maximum capacity under the same room.
3. **Given** an administrator is creating a room, **When** they omit the name, do not select a building, do not select a floor, or provide zero seating arrangements, **Then** the system rejects the submission and explains which fields are missing or invalid.
4. **Given** an administrator is creating a room, **When** they select available equipment (e.g., projector, whiteboard), **Then** the equipment is stored and displayed as part of the room's profile.
5. **Given** an administrator is creating a room and no buildings exist yet, **When** they reach the building selection step, **Then** the system lets them create a new building (and then a new floor under it) without leaving the room creation flow.

---

### User Story 2 - View and configure existing rooms (Priority: P2)

A facility administrator reviews the list of existing rooms and opens one to see or adjust its details (name, building, floor, equipment, seating arrangements, capacities).

**Why this priority**: Once rooms exist, administrators need to inspect and correct their configuration as building layouts, furniture, or equipment change. This is required for the room inventory to stay accurate over time.

**Independent Test**: Can be fully tested by opening a previously created room's detail view and confirming all stored attributes are displayed accurately.

**Acceptance Scenarios**:

1. **Given** rooms exist in the system, **When** an administrator opens the room list, **Then** they see each room's name, building, floor, and status (active/deactivated).
2. **Given** an administrator opens a specific room, **When** the detail view loads, **Then** all configured attributes (equipment, seating arrangements, capacities) are shown.

---

### User Story 3 - Update an existing room (Priority: P2)

A facility administrator edits an existing room's details — for example, renaming it, adding a new seating arrangement, or updating its equipment — after a physical change in the room.

**Why this priority**: Room attributes change over time (new furniture, renovations, added equipment); administrators need to keep the record accurate without recreating the room and losing its history/ID.

**Independent Test**: Can be fully tested by changing one or more attributes of an existing room and verifying the updated values are persisted and reflected in the room list and detail view.

**Acceptance Scenarios**:

1. **Given** an existing room, **When** an administrator updates its name, equipment, or seating arrangements and saves, **Then** the system persists the changes and the room retains its original ID.
2. **Given** an existing room, **When** an administrator attempts to save an update that removes all seating arrangements, **Then** the system rejects the change, since a room MUST always have at least one seating arrangement.
3. **Given** two administrators have both loaded the same room, **When** the first saves a change and then the second saves a change based on the now-outdated data, **Then** the system accepts the first save and rejects the second with a conflict error instead of overwriting the first administrator's change.

---

### User Story 4 - Deactivate or delete a room (Priority: P3)

A facility administrator removes a room from active use — either by deactivating it (hiding it from future booking while preserving its history) or, if the room has no dependent history, deleting it outright.

**Why this priority**: Buildings are renovated and rooms are decommissioned; administrators need a way to retire a room's record without breaking references to it from other parts of the system (lowest priority since it depends on rooms already existing and being configured).

**Independent Test**: Can be fully tested by deactivating an existing room and confirming it no longer appears in the active room list but remains retrievable, and by deleting a room with no dependent history and confirming it no longer exists in the system.

**Acceptance Scenarios**:

1. **Given** an existing active room, **When** an administrator deactivates it, **Then** the room is excluded from the active room list but its record and ID remain in the system.
2. **Given** a deactivated room, **When** an administrator reactivates it, **Then** the room reappears in the active room list with its original configuration intact.
3. **Given** an existing room with no dependent history, **When** an administrator deletes it, **Then** the room's record is permanently removed and no longer appears anywhere in the system.

---

### Edge Cases

- What happens when an administrator tries to create a room with a name that already exists in the same building?
- How does the system handle a seating arrangement submitted with a maximum capacity of zero or a negative number?
- What happens when an administrator tries to delete a room that already has dependent history (e.g., referenced elsewhere)? The system MUST prevent hard deletion in this case and guide the administrator to deactivate instead.
- When two administrators load the same room and both attempt to save changes, the system MUST accept the first save and reject the second with a clear conflict error, requiring the second administrator to reload the current data before retrying.
- What happens when an administrator submits a room with duplicate seating arrangement names (e.g., two arrangements both named "Theater")?
- What happens when an administrator tries to remove an equipment type from the catalog that is still assigned to one or more rooms? The system MUST deactivate the type instead of deleting it, preserving existing room assignments.
- What happens when an administrator tries to create a room but no active building or floor exists yet? The system MUST let them create a building and a floor as part of (or immediately before) the room creation flow (see User Story 1, Scenario 5).
- What happens when an administrator deactivates a building that still has active floors? The system MUST automatically deactivate all of that building's floors along with it, so a room can never reference an active floor under an inactive building.
- What happens when an administrator tries to delete a building that still has one or more floors (active or deactivated)? The system MUST reject the deletion; floors must be deleted (or left deactivated) first.
- What happens when an administrator tries to delete a floor that is still referenced by one or more rooms? The system MUST reject the deletion and direct the administrator to deactivate the floor instead, mirroring the equipment-type rule above.
- What happens when an administrator tries to rename a building, floor, or equipment type to a name that collides with an existing one (case-insensitively)? The system MUST reject the rename with a clear explanation, the same as it does for a colliding creation (FR-023).
- What happens when an administrator tries to reactivate a single floor while its parent building is still deactivated? The system MUST reject the reactivation and explain that the building must be reactivated first (FR-021).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow an authorized administrator to create a new room by providing a name, selecting an existing active building, selecting an existing active floor that belongs to that building, providing at least one seating arrangement (name + maximum capacity), and optionally one or more equipment types from the catalog.
- **FR-002**: System MUST assign each newly created room a unique, system-generated identifier that never changes for the lifetime of the room record.
- **FR-003**: System MUST require every room to have at least one seating arrangement at all times; a room cannot be saved with zero seating arrangements, whether on creation or update.
- **FR-004**: System MUST associate each seating arrangement with its own maximum person capacity, independent of the capacities of the room's other seating arrangements.
- **FR-005**: System MUST allow an authorized administrator to view the full configuration of any existing room (name, building, floor, equipment, seating arrangements, capacities, status).
- **FR-006**: System MUST allow an authorized administrator to update an existing room's name, building, floor, equipment, and seating arrangements without changing the room's unique identifier.
- **FR-007**: System MUST validate that a room's name is non-empty, that its building and floor reference an existing active building/floor pair, and that every seating arrangement has a non-empty name and a maximum capacity greater than zero.
- **FR-008**: System MUST allow an authorized administrator to deactivate an active room, immediately removing it from lists of rooms available for future booking while preserving its stored configuration and identifier.
- **FR-009**: System MUST allow an authorized administrator to reactivate a previously deactivated room, restoring it to the active room list with its configuration unchanged.
- **FR-010**: System MUST allow an authorized administrator to permanently delete a room only when the room has no dependent history (e.g., no existing bookings referencing it); if dependent history exists, the system MUST reject the deletion and direct the administrator to deactivate the room instead.
- **FR-011**: System MUST list all rooms with at least their name, building, floor, and active/deactivated status, and MUST allow filtering the list by active/deactivated status.
- **FR-012**: System MUST provide room creation, configuration, update, deactivation, reactivation, and deletion functionality intended for administrator use. No authentication or role-based access control exists yet; enforcing that only an administrator role can perform these actions is deferred to a future authentication/authorization feature, and this feature MUST NOT be blocked on building one.
- **FR-013**: System MUST prevent creating or updating a room to have the same name (case-insensitively) as another room whose floor belongs to the same building.
- **FR-014**: System MUST allow an administrator to manage an equipment catalog — adding new equipment types (non-empty name, defaulting to active status), renaming existing ones, deactivating types that are no longer offered, and reactivating a previously deactivated type — independently of any specific room. Equipment types have no child entities of their own, so deactivating one has no cascading effect beyond the type itself (contrast with Building, FR-021).
- **FR-015**: System MUST let an administrator assign zero or more equipment types from the catalog to a room when creating or updating it, and MUST prevent assigning an equipment type that has been deactivated from the catalog.
- **FR-016**: System MUST NOT permanently delete an equipment type from the catalog while it is still assigned to any room; an administrator retiring such a type from active use MUST deactivate it instead (existing room assignments are preserved and displayed, but the type can no longer be newly assigned).
- **FR-017**: System MUST detect when a room update is based on stale data (the room was modified by someone else since it was loaded) and MUST reject the stale update with a conflict error instead of silently overwriting the intervening change.
- **FR-018**: System MUST allow an administrator to manage a Building catalog: create a building (non-empty name, defaulting to active status), rename it, deactivate it, reactivate it, and delete it, independently of any specific room.
- **FR-019**: System MUST allow an administrator to manage Floors, each belonging to exactly one building for its entire lifetime (a floor cannot be moved to a different building after creation): create a floor (non-empty name, defaulting to active status) under a chosen active building, rename it, deactivate it, reactivate it, and delete it.
- **FR-020**: System MUST let an administrator select, when creating or updating a room, an existing active building and one of that building's existing active floors, and MUST prevent selecting a deactivated building, a deactivated floor, or a floor that does not belong to the selected building.
- **FR-021**: When an administrator deactivates a building, the system MUST automatically deactivate all of that building's floors as well; reactivating a building MUST NOT automatically reactivate its floors — each floor must be reactivated individually if still needed. System MUST reject an attempt to reactivate an individual floor while its parent building is still deactivated, since an active floor under an inactive building would contradict FR-020; the building must be reactivated first.
- **FR-022**: System MUST NOT delete a building while it still has any floors (active or deactivated); because floors are structural sub-parts of a building rather than an optional association, this check is stricter than — and independent of — the reference-based check below, by design: the administrator must delete (or leave deactivated) those floors first. System MUST NOT delete a floor while it is still referenced by any room; an administrator retiring such a floor from active use MUST deactivate it instead (existing room references are preserved and displayed, but the floor can no longer be newly assigned).
- **FR-023**: System MUST prevent creating or renaming a building to a name shared by another building, prevent creating or renaming a floor to a name shared by another floor within the same building, and prevent creating or renaming an equipment type to a name shared by another equipment type in the catalog; all three uniqueness checks are case-insensitive.

### Key Entities

- **Room**: Represents a physical meeting space that can eventually be booked. Attributes: unique identifier, name, a reference to one Floor (which in turn belongs to one Building), list of equipment, one or more seating arrangements, active/deactivated status, a change-tracking marker (e.g., version/last-modified) used to detect concurrent-edit conflicts. A room's identifier is immutable and persists across updates and deactivation.
- **Seating Arrangement**: Represents one way a room's furniture can be configured (e.g., "Theater", "U-Shape", "Classroom"). Attributes: name, maximum person capacity. Belongs to exactly one room; a room must have at least one.
- **Equipment Type**: Represents a kind of equipment that can be offered in a room (e.g., projector, whiteboard), managed independently by an administrator via a catalog. Attributes: unique identifier, name (unique across the catalog, case-insensitive), active/deactivated status. A room references zero or more equipment types from this catalog; a deactivated type remains visible on rooms that already reference it but cannot be newly assigned.
- **Building**: Represents a physical building that contains rooms, managed independently by an administrator via a catalog. Attributes: unique identifier, name (unique across buildings, case-insensitive), active/deactivated status. Has zero or more Floors (zero when first created). A deactivated building (and its now-deactivated floors, per FR-021) remains visible on rooms that already reference it via their floor, but cannot be newly selected for a room.
- **Floor**: Represents one floor level within exactly one building for its entire lifetime, managed independently by an administrator. Attributes: unique identifier, name (unique within its building, case-insensitive), active/deactivated status. A room references exactly one floor; a deactivated floor (or one whose building is deactivated) remains visible on rooms that already reference it but cannot be newly assigned.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An administrator can create a fully configured room (name, building, floor, equipment, seating arrangements) in under 2 minutes.
- **SC-002**: 100% of rooms with at least one seating arrangement and a positive capacity are successfully creatable and retrievable without data loss.
- **SC-003**: Administrators can locate and open any existing room's configuration from the room list in under 3 clicks/steps.
- **SC-004**: Zero rooms with dependent history can be permanently deleted; all such attempts are rejected with a clear explanation, preventing accidental loss of booking-relevant data.
- **SC-005**: 95% of room creation or update attempts with invalid data (missing required fields, zero seating arrangements, non-positive capacity) are rejected with an explanation an administrator can act on without external help.
- **SC-006**: 100% of rooms reference a currently defined building and floor from the managed catalogs (zero rooms with free-text or inconsistent building/floor values), eliminating naming drift such as "1st Floor" vs. "Floor 1" for the same physical location.

## Assumptions

- "Configuring" a room and "changing" a room are treated as the same capability (update), since the feature description does not distinguish separate workflows for initial configuration versus later changes.
- Seating arrangement names (e.g., "Theater", "U-Shape", "Classroom", "Boardroom") are freely defined by the administrator per room rather than drawn from a fixed system-wide catalog, since the description only specifies "1 to n seating arrangements" without naming a fixed set.
- Equipment types are drawn from an administrator-managed catalog (seeded with at least Projector and Whiteboard) rather than free-text tags, to keep equipment consistent and filterable in the future booking feature.
- Room uniqueness is scoped to name + building (the same room name may be reused across different buildings, e.g., "Room 101" in Building A and Building B), now enforced via the room's floor→building reference rather than a free-text field.
- A newly created Building starts with zero floors; per FR-001 and FR-020, a room requires an active floor, so a brand-new building cannot host a room until the administrator adds at least one floor to it (in the same flow or afterward).
- Unlike Building and Floor (mandatory for every room per FR-001), the Equipment Type catalog being empty does not block room creation, since equipment assignment is optional (FR-001, FR-015) — this asymmetry between the three catalogs is intentional.
- Renaming an equipment type, building, or floor does not alter any room's underlying reference (foreign key); rooms always display the catalog entry's current name, with no historical snapshot retained.
- "Dependent history" that blocks hard deletion refers to references from other features (e.g., bookings) that do not yet exist in this feature; until such features exist, no room will have dependent history and hard deletion is always available.
- No authentication/authorization system exists yet in this codebase; this feature is built as if used by a single implicit administrator, with no login or role-checking. Real role enforcement (administrator manage vs. general user read-only) is deferred to a future auth feature and must be layered on without redesigning this feature's data model.
