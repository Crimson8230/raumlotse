# Feature Specification: Room Search with Filters

**Feature Branch**: `008-room-search-filter`

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "Als Vortragende:r (alle Nutzer:innen?) möchte ich nach bestimmten Kriterien filtern (Datum, Uhrzeit, Anzahl an mögl. Personen, Gebäude, Bestuhlung, Beamer, Whiteboard) können, um meine Suche einzugrenzen. Akzeptanzkriterien: Es kann nach Räumen gesucht werden. In der Suche können Filterkriterien angegeben werden: min./max. Personenanzahl, Gebäude, Bestuhlung, Beamer, Whiteboard, Barrierefrei. Das Suchergebnis zeigt nur passende Räume an. Es geht um Filterung der Räume."

## Clarifications

### Session 2026-09-26

- Q: Is date/time availability filtering in scope for this feature? → A: Yes — included as User Story 4 (P3): the search can exclude rooms booked in a given date/time window.
- Q: Should the equipment filter be limited to projector and whiteboard, or offer every equipment type from the catalog? → A: Every active equipment type from the catalog is filterable; projector and whiteboard are covered as seeded catalog entries.
- Q: How is "barrier-free" recorded? → A: Custom — every room is considered barrier-free in itself. What matters is step-free reachability: whether the building has an elevator (yes/no, a property of the building). A room counts as barrier-free reachable if it is on the ground floor or its building has an elevator.
- Q: How should the spec handle barrier-free access, given that Austrian law (BGStG reasonableness clause, OIB-RL 4 exemptions for existing buildings) does not guarantee that every public room is barrier-free? → A: Keep the building/floor-derived rule (elevator per building, ground-floor mark per floor), plus a per-room override: an administrator can explicitly mark a room as "not barrier-free", which excludes it even if its floor/building would qualify. The override only works in that direction. This supersedes the earlier statement that every room is barrier-free in itself.
- Q: Should a search time window be pre-filled in the booking form when the user moves from a search result to booking? → A: Yes — opening a room from a search with a time window opens its detail view with the booking form open and start/end pre-filled (still editable); conflict validation at save time is unchanged.

### Session 2026-09-28 (change request after quickstart validation)

- Q: How does a user get from a search result to booking? → A: Each active result offers a dedicated "Buchen" action that opens the room's detail view with the booking form already open (pre-filled when the search had a time window). Clicking the room name still opens the detail view as before.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find rooms that fit my group size and location (Priority: P1)

As a lecturer (or any signed-in user) planning a session, I want to search the rooms and narrow the results by minimum/maximum number of persons and by building, so that I only see rooms that can actually host my group where I need it.

**Why this priority**: Capacity and location are the two criteria that rule out the most rooms and are required for virtually every search. This alone turns the flat room list into a useful search and is a viable MVP.

**Independent Test**: With several active rooms in different buildings and with different seating capacities, set a minimum and/or maximum person count and a building, run the search, and verify that exactly the matching rooms are listed.

**Acceptance Scenarios**:

1. **Given** active rooms exist, **When** a signed-in user opens the room search without setting any filter, **Then** all active rooms are listed.
2. **Given** room A with a largest seating capacity of 40 and room B with a largest seating capacity of 15, **When** the user searches with a minimum of 20 persons, **Then** room A is listed and room B is not.
3. **Given** room A seats at most 40 and room C seats at most 120 in any arrangement, **When** the user searches with a maximum of 50 persons, **Then** room A is listed and room C is not.
4. **Given** rooms in building "Haus 1" and "Haus 2", **When** the user filters by building "Haus 1", **Then** only rooms located in "Haus 1" are listed.
5. **Given** a set of filters that no room satisfies, **When** the user searches, **Then** an explicit "no matching rooms" message is shown together with a way to reset the filters.

---

### User Story 2 - Filter by seating layout and equipment (Priority: P2)

As a lecturer, I want to additionally filter by seating arrangement (Bestuhlung) and by required equipment from the equipment catalog (e.g., projector, whiteboard), so that the room I pick supports my teaching format without extra setup.

**Why this priority**: Layout and equipment are the next most frequent reasons a room is unsuitable, but the search is already useful without them.

**Independent Test**: With rooms that differ in seating arrangements and assigned equipment, select a seating arrangement and one or both equipment filters, and verify only rooms offering all selected characteristics are listed.

**Acceptance Scenarios**:

1. **Given** room A offers the seating arrangement "U-Shape" and room B does not, **When** the user filters by "U-Shape", **Then** room A is listed and room B is not.
2. **Given** room A has a projector and room B does not, **When** the user requires a projector, **Then** only room A is listed.
3. **Given** room A has a projector and a whiteboard and room B has only a projector, **When** the user requires both projector and whiteboard, **Then** only room A is listed.
4. **Given** room A offers "Theater" with capacity 60 and "U-Shape" with capacity 20, **When** the user filters by "U-Shape" and a minimum of 30 persons, **Then** room A is NOT listed, because no single arrangement satisfies both the layout and the capacity.
5. **Given** an administrator has added "Microphone" to the equipment catalog and assigned it to room A, **When** the user opens the search, **Then** "Microphone" is offered as an equipment filter and requiring it lists only room A.

---

### User Story 3 - Filter for barrier-free reachable rooms (Priority: P2)

As a lecturer whose audience includes people with reduced mobility, I want to restrict the search to rooms that can be reached without stairs, so that every participant can get to the room. A room is considered barrier-free reachable if it is on the ground floor or its building has an elevator, unless an administrator has explicitly marked that room as not barrier-free (e.g., steps inside the room or a narrow door).

**Why this priority**: Accessibility is explicitly requested and can be a hard requirement, but it depends on elevator and ground-floor information being maintained first.

**Independent Test**: Record for buildings whether they have an elevator and mark ground floors, enable the barrier-free filter, mark one qualifying room as not barrier-free, and verify that only rooms on a ground floor or in a building with an elevator are listed, except the explicitly excluded room.

**Acceptance Scenarios**:

1. **Given** building "Haus 1" has an elevator, **When** the user enables the barrier-free filter, **Then** rooms on every floor of "Haus 1" are listed.
2. **Given** building "Haus 2" has no elevator, room A is on its ground floor and room B on its first upper floor, **When** the user enables the barrier-free filter, **Then** room A is listed and room B is not.
3. **Given** the barrier-free filter is not enabled, **When** the user searches, **Then** rooms are listed regardless of elevator or floor.
4. **Given** an administrator maintains the building catalog, **When** they create or edit a building, **Then** they can record whether it has an elevator.
5. **Given** an administrator maintains a building's floors, **When** they create or edit a floor, **Then** they can mark it as the ground floor (step-free entrance level).
6. **Given** building "Haus 1" has an elevator and room C in "Haus 1" is explicitly marked as not barrier-free, **When** the user enables the barrier-free filter, **Then** room C is not listed while the other rooms of "Haus 1" are.
7. **Given** an administrator creates or edits a room, **When** they save it, **Then** they can mark the room as not barrier-free; by default a room is not marked and inherits reachability from its floor and building.

---

### User Story 4 - Find rooms free at a given date and time (Priority: P3)

As a lecturer, I want to specify a date and a time window so that the search only shows rooms that are not already booked during that period.

**Why this priority**: Confirmed in scope (see Clarifications), but it adds the most value once the attribute filters exist.

**Independent Test**: Create a reservation for room A from 10:00 to 12:00 on a given day, search for 11:00–12:00 on that day, and verify room A is excluded while an unbooked room B is listed.

**Acceptance Scenarios**:

1. **Given** room A has a reserved or active booking from 10:00 to 12:00, **When** the user searches for 11:00–13:00 on the same day, **Then** room A is not listed.
2. **Given** room A has a booking from 10:00 to 11:00, **When** the user searches for 11:00–12:00, **Then** room A is listed (back-to-back is allowed, consistent with the reservation rules).
3. **Given** room A's only booking in the window is cancelled, expired, or completed, **When** the user searches that window, **Then** room A is listed.
4. **Given** no date/time is entered, **When** the user searches, **Then** rooms are listed regardless of their bookings.
5. **Given** a search for 11:00–12:00 on a given day lists room B, **When** the user opens room B from the result, **Then** room B's detail view shows the booking form already open with start 11:00 and end 12:00 on that day pre-filled, and the user can change them before confirming.
6. **Given** the user opens a room from a search without a time window, **When** the detail view loads, **Then** the booking form is not pre-filled and behaves as before.

---

### Edge Cases

- A minimum person count greater than the maximum person count is rejected with an understandable message; no search is executed.
- Person counts that are zero, negative, non-integer, or non-numeric are rejected with a correction hint.
- Filters with no value set (e.g., no building selected) do not restrict the result.
- Deactivated rooms never appear in search results; rooms whose floor or building is deactivated also do not appear.
- Deactivated buildings are not offered as a building filter option.
- A seating arrangement filter compares arrangement names case-insensitively, so "u-shape" and "U-Shape" match the same rooms.
- If a room offers several arrangements, the room matches if at least one arrangement satisfies all arrangement-related filters (layout and person range) at the same time.
- Deactivated equipment types are not offered as filter options; rooms that still have such equipment assigned remain visible when no equipment filter is set.
- When the barrier-free filter is active, buildings without recorded elevator information are treated as having no elevator, and floors not marked as ground floor are treated as upper floors, so a room is never wrongly promised as step-free.
- The room-level mark only excludes: a room on an upper floor of a building without elevator cannot be marked as barrier-free, since the rule has no positive per-room override.
- Changing a building's elevator property or a floor's ground-floor mark changes the barrier-free reachability of all affected rooms immediately, except rooms explicitly marked as not barrier-free.
- A building may have more than one floor marked as ground floor (e.g., split-level entrances); rooms on any of them count as step-free reachable.
- Only a date and time window with the end after the start are accepted; a window partly or fully in the past is accepted (it simply reflects past bookings) — no booking is made by the search.
- Pre-filled times are only a starting point: if the room was booked by someone else in the meantime, or the window lies in the past, the booking is rejected by the existing reservation rules with their usual messages.
- A partial date/time input (e.g., date without times) is rejected with a correction hint instead of being ignored.
- When no rooms exist at all, the search shows the standard empty state rather than an error.
- Changing filters after a search updates the results; results never mix outcomes from an earlier filter set.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST let every signed-in user search the rooms; unauthenticated users MUST NOT be able to search.
- **FR-002**: System MUST only return active rooms whose floor and building are also active.
- **FR-003**: Users MUST be able to specify an optional minimum and an optional maximum number of persons as whole numbers ≥ 1; the system MUST reject a minimum greater than the maximum.
- **FR-004**: A room MUST match the person filters only if at least one of its seating arrangements has a maximum capacity that is ≥ the given minimum and ≤ the given maximum.
- **FR-005**: Users MUST be able to filter by one building, selected from the currently active buildings; only rooms in that building MUST match.
- **FR-006**: Users MUST be able to filter by one seating arrangement name, selected from the names used by active rooms; only rooms offering an arrangement with that name (case-insensitive) MUST match.
- **FR-007**: When both a seating arrangement and a person range are given, a room MUST match only if a single arrangement satisfies both.
- **FR-008**: Users MUST be able to require zero or more equipment types, selected from all active equipment types in the catalog (including projector and whiteboard); a room MUST match only if every required equipment type is assigned to it.
- **FR-009**: Users MUST be able to restrict results to barrier-free reachable rooms; a room MUST match only if (its floor is marked as ground floor OR its building has an elevator) AND the room is not explicitly marked as not barrier-free.
- **FR-009a**: Administrators MUST be able to record for each building whether it has an elevator (yes/no, default no) when creating or editing it.
- **FR-009b**: Administrators MUST be able to mark each floor as ground floor (yes/no, default no) when creating or editing it.
- **FR-009c**: Administrators MUST be able to explicitly mark a room as not barrier-free (yes/no, default no) when creating or editing it; the room's detail view MUST show whether it is barrier-free reachable.
- **FR-010**: All given filters MUST be combined with AND; a room appears only if it satisfies every given filter.
- **FR-011**: The result list MUST show for each room at least its name, building, floor, seating arrangements with capacities, assigned equipment, and whether it is barrier-free reachable, and MUST let the user open the room's detail view (from which it can be booked).
- **FR-011a**: When the user opens a room from a search that included a date/time window, the room's detail view MUST open with the booking form shown and its start and end pre-filled from that window; the values MUST remain editable, and all existing reservation validations MUST still apply on confirmation. Without a search time window, the booking form MUST behave as before.
- **FR-011b**: Each active room in the result list MUST offer a "Buchen" action that opens the room's detail view with the booking form shown; if the search included a date/time window, start and end MUST be pre-filled as in FR-011a, otherwise the form starts empty. Deactivated rooms (visible only in the administrators' non-search list) MUST NOT offer the action.
- **FR-012**: When no room matches, the system MUST show an explicit no-results message and a way to reset all filters.
- **FR-013**: Users MUST be able to reset all filters in a single action, returning to the unfiltered list of active rooms.
- **FR-014**: Users MUST be able to optionally give a date with start and end time; when given, the system MUST exclude rooms having a `RESERVED` or `ACTIVE` reservation overlapping that window, using the same half-open interval rule `[start, end)` as the reservation feature; the end MUST be after the start.

### Key Entities *(include if feature involves data)*

- **Room Search Criteria**: The set of optional filters a user supplies: minimum persons, maximum persons, building, seating arrangement name, required equipment types, barrier-free reachable flag, and a date with time window. Not stored.
- **Building** (existing): Gains a yes/no "has elevator" property.
- **Floor** (existing): Gains a yes/no "ground floor" property.
- **Room** (existing): Gains a yes/no "explicitly not barrier-free" property (default no). Its barrier-free reachability is derived: (floor is ground floor OR building has elevator) AND NOT explicitly not barrier-free. Uses its floor → building reference, seating arrangements with capacities, assigned equipment, and status.
- **Seating Arrangement** (existing): Name and maximum capacity; basis for layout and person filters.
- **Equipment Type** (existing): Catalog entries such as projector and whiteboard; basis for equipment filters.
- **Reservation** (existing): Used by the date/time filter to determine occupied rooms.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of acceptance test cases, the search result contains exactly the active rooms satisfying all given filters: no non-matching room is shown and no matching room is missing.
- **SC-002**: A user can find a suitable room for a given group size, building, and equipment need within 1 minute without assistance.
- **SC-003**: With up to 500 rooms in the system, results appear within 2 seconds of the user submitting the filters in at least 95% of searches.
- **SC-004**: 100% of invalid filter inputs (min > max, non-positive or non-numeric person counts, end not after start) are rejected with a message the user can act on.
- **SC-005**: A user can go from a search result to the matching room's detail view in one interaction; when the search included a time window, the user can book that room for that window without re-entering date or time.

## Assumptions

- "Vortragende:r (alle Nutzer:innen?)" is resolved as: every signed-in user may search, regardless of role. The project does not yet enforce business permissions per role (see feature 003), and searching is read-only.
- The search extends the existing room overview ("Räume") instead of introducing a separate area in the navigation; administrators keep their existing management actions.
- "Anzahl an mögl. Personen" refers to the maximum capacity of a seating arrangement, because capacity is defined per arrangement in room management (feature 001).
- Seating arrangement names are free text per room (feature 001); the layout filter offers the distinct names in use, compared case-insensitively.
- Only one building and one seating arrangement can be selected per search; selecting several at once is out of scope.
- Results are sorted by building, then room name; custom sorting, pagination, and saved searches are out of scope.
- Room suggestions or ranking ("intelligent" recommendation) are out of scope; this feature only filters.
- Barrier-free access is not legally guaranteed for every room in Austria (BGStG § 6 reasonableness clause; OIB-RL 4 exemptions for existing and listed buildings), so it is modeled explicitly. Barrier-free in this feature means step-free reachability of the room, derived from building and floor with a room-level exclusion. Floor names are free text (e.g., "EG", "1. OG"), so ground floors are identified by an explicit administrator-maintained mark rather than by interpreting names.
- Buildings and floors existing before this feature start with "no elevator" and "not ground floor", and existing rooms start without the "not barrier-free" mark, until an administrator records otherwise.
- The date/time filter only checks booking conflicts; it does not create or hold a reservation, and rooms found free may be booked by someone else before the user books.
