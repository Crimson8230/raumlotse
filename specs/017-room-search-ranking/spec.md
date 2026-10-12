# Feature Specification: Ranked Room Search Results

**Feature Branch**: `017-room-search-ranking`

**Created**: 2026-10-12

**Status**: Draft

**Input**: User description: "Als Vortragende:r möchte ich die Sucheregebnisse nach Anforderungen (z.B. kleine Räume für kleine Gruppen) geranked bekommen, um eine ressourcenschonende Nutzung zu gewährleisten. Akzeptanzkriterien: Passende Räume im Suchergebnis sollen danach sortiert werden, wie “optimal” sie zu den Suchkriterien passen — Raumgröße passt zur Anzahl der Personen (kein 100 Personen-Raum für eine 4-Personen Reservierung); Raumausstattung (kein Computerraum für Besprechung mit Whiteboard); individuelle Favoriten-markierung, die in der Sortierung oben liegen, sofern sie den grundlegenden Anforderungen entsprechen (pinned); Raumnutzung (Sortierung nach Beliebtheit (Buchungsfrequenz))."

## Clarifications

### Session 2026-10-12

- Q: Should rooms with a similar number of empty seats count as an equally good size match, so that equipment can decide between them? → A: Yes — size bands relative to the group size (≤ 25 % / ≤ 50 % / ≤ 100 % larger than needed); rooms in the same band count as equal. Rooms more than 100 % larger than needed are ordered by exact seat surplus. Rationale: most rooms support a similar number of students, so a further criterion must be able to break ties.
- Q: When new equipment types are added to the catalog, which ones should push a room down the ranking if the user didn't ask for them? → A: Administrators classify each equipment type as *specialised* or *general*; only unrequested specialised equipment counts against a room; new types default to *general*. New types (e.g., power sockets, air conditioning, PC workstations) need only a catalog entry and classification.
- Q: Should popularity be based on bookings by all users, or only on the searching user's own past bookings? → A: All users' bookings (last 90 days); personal preference is covered by favorites.
- Q: Should users have one place where they can see and manage all their favorite rooms? → A: Yes — a "Nur Favoriten" filter in the room search; when it is active, favorites that are currently not available (deactivated room, floor or building) are listed below the results as "Nicht verfügbar" with a removable favorite mark. No separate page.
- Q: Should there be a maximum number of favorites per user? → A: Yes — at most 10 favorites per user; marking an 11th is refused with a hint to remove one first.

## User Scenarios & Testing *(mandatory)*

This feature builds on the room search (feature 008). The search still decides **which** rooms are shown (all filters remain hard filters); this feature decides **in which order** they are shown. Every room in the result already satisfies the user's basic requirements.

### User Story 1 - Smallest fitting room first (Priority: P1)

As a lecturer searching for a room for a given number of persons, I want rooms whose capacity is close to my group size to appear first, so that I pick a small room for a small group and large rooms stay available for large groups.

**Why this priority**: Capacity fit is the core of "resource-friendly use" and the main example in the request (no 100-person room for 4 persons). On its own it already turns the result list into a recommendation.

**Independent Test**: With rooms seating at most 6, 30 and 100 persons, search with a minimum of 4 persons and verify the order is 6 → 30 → 100.

**Acceptance Scenarios**:

1. **Given** room A seats at most 6, room B 30 and room C 100 persons, **When** the user searches with a minimum of 4 persons, **Then** the results are ordered A, B, C.
2. **Given** room D offers "Theater" with 80 seats and "Workshop" with 10 seats, and room E offers only "Theater" with 20 seats, **When** the user searches with a minimum of 8 persons and no seating arrangement filter, **Then** room D is ranked before room E, because D's smallest suitable arrangement (10) fits better than E's (20).
3. **Given** the same rooms D and E, **When** the user searches with a minimum of 8 persons and the seating arrangement "Theater", **Then** room E (20) is ranked before room D (80), because only arrangements matching the filter are considered.
4. **Given** room F seats 21 and room G seats 24, **When** the user searches with a minimum of 20 persons, **Then** F and G count as an equally good size match (both at most 25 % larger than needed), and the following criteria decide their order.
5. **Given** room H seats 45 and room J seats 90, **When** the user searches with a minimum of 20 persons, **Then** H is ranked before J (both more than 100 % larger than needed, so the exact seat surplus decides).
6. **Given** the user enters no minimum person count, **When** the user searches, **Then** capacity does not influence the order.

---

### User Story 2 - Pinned favorite rooms (Priority: P2)

As a lecturer who regularly teaches in the same rooms, I want to mark rooms as personal favorites, so that whenever they satisfy my search they appear at the top of the results.

**Why this priority**: Favorites encode the user's personal knowledge (location, habits) that the system cannot infer. They are explicitly requested as "pinned" but only add value once there is an order to pin against.

**Independent Test**: Mark one large room as favorite, search with filters it satisfies and filters it does not satisfy, and verify it is listed first in the first case and absent in the second.

**Acceptance Scenarios**:

1. **Given** a signed-in user views a room in the search results or in its detail view, **When** they mark it as favorite, **Then** the room is shown as a favorite for that user from then on, and they can remove the mark the same way.
2. **Given** room C (100 seats) is a favorite of the user and rooms A (6 seats) and B (30 seats) are not, **When** the user searches with a minimum of 4 persons, **Then** C is listed first, followed by A and B.
3. **Given** room C is a favorite but does not satisfy the search filters (e.g., lacks a required projector), **When** the user searches, **Then** room C is not listed at all.
4. **Given** the user has several favorites that satisfy the search, **When** the results are shown, **Then** all of them are listed before every non-favorite room, and among themselves they are ordered by the same fit rules as all other rooms.
5. **Given** user X marked room C as favorite, **When** user Y searches, **Then** room C is not pinned for user Y and no favorite mark is shown to Y.
6. **Given** the user has favorites C and D, **When** they enable "Nur Favoriten" without other filters, **Then** only C and D are listed, ranked by the usual rules.
7. **Given** "Nur Favoriten" is enabled together with other filters, **When** the user searches, **Then** only favorites that satisfy all other filters are listed.
8. **Given** favorite E has been deactivated, **When** the user enables "Nur Favoriten", **Then** E is listed below the results under "Nicht verfügbar" (not bookable), and the user can remove its favorite mark there.
9. **Given** the user has no favorites, **When** they enable "Nur Favoriten", **Then** a message explains that no favorites exist yet and how to mark rooms with the star.
10. **Given** the user already has 10 favorites, **When** they try to mark another room, **Then** the room is not marked and a message explains that at most 10 favorites are possible and an existing one must be removed first.

---

### User Story 3 - Fewest unneeded equipment first (Priority: P2)

As a lecturer who only needs, for example, a whiteboard, I want rooms that offer what I need without extra specialised equipment (equipment types an administrator has classified as specialised, such as PC workstations) to appear first, so that well-equipped rooms such as computer labs remain available for those who need them.

**Why this priority**: Explicitly requested and directly serves resource-friendly use, but secondary to capacity, which is the larger lever.

**Independent Test**: With "PC-Arbeitsplätze" classified as specialised and "Klimaanlage" as general, create two rooms of equal capacity, one with a whiteboard and air conditioning and one with a whiteboard and PC workstations, search requiring a whiteboard and verify the room with air conditioning is ranked first.

**Acceptance Scenarios**:

1. **Given** "PC-Arbeitsplätze" is classified as specialised, rooms A and B both seat 20, A has only a whiteboard and B has a whiteboard and PC workstations, **When** the user requires a whiteboard and a minimum of 15 persons, **Then** A is ranked before B.
2. **Given** two rooms in different size bands, **When** the user searches, **Then** the room in the better size band wins even if it has more unneeded equipment (capacity fit takes precedence over equipment fit).
3. **Given** the user requires no equipment and two rooms are in the same size band, **When** the user searches, **Then** the room with fewer specialised equipment types is ranked first.
4. **Given** a group of 20, room K (21 seats) has PC workstations (specialised) and room L (24 seats) has only a whiteboard, **When** the user requires a whiteboard, **Then** L is ranked before K, because both are in the same size band and L has no unneeded specialised equipment.
5. **Given** "Klimaanlage" and "Steckdosen am Platz" are classified as general, room M has both and room N has neither, and otherwise they are equal, **When** the user searches without requiring them, **Then** M is not ranked below N because of them.
6. **Given** an administrator adds a new equipment type to the catalog, **When** they save it without changing its classification, **Then** it is general and does not affect the ranking until it is classified as specialised.
7. **Given** a room has PC workstations (specialised) and the user requires PC workstations, **When** the user searches, **Then** the PC workstations do not count against that room.

---

### User Story 4 - Popular rooms first among equally fitting rooms (Priority: P3)

As a lecturer, I want rooms that are booked frequently to be listed before rarely booked rooms when they otherwise fit equally well, so that I benefit from rooms that have proven themselves in practice.

**Why this priority**: Requested, but a weaker signal than fit; it mainly resolves ties and orders searches without capacity or equipment criteria.

**Independent Test**: With two otherwise identical rooms where one has more bookings in the recent past, search and verify the more frequently booked room is listed first.

**Acceptance Scenarios**:

1. **Given** rooms A and B fit the search equally well, A had 12 bookings in the last 90 days and B had 3, **When** the user searches, **Then** A is ranked before B.
2. **Given** a room's bookings in the last 90 days were all cancelled or expired (not used), **When** popularity is determined, **Then** those bookings do not count.
3. **Given** the user searches without any criteria, **When** the results are shown, **Then** the order is: the user's favorites, then rooms by popularity (most booked first), then by building and room name.

---

### Edge Cases

- Band limits are inclusive: for a group of 20, a room with 25 seats (exactly 25 % larger) is in band 1, a room with 26 seats in band 2.
- When several rooms are equal on every ranking criterion, they are ordered by building name, then room name; rooms with the same building and room name (names are not unique) are ordered by a fixed internal room identifier, so the order is stable and reproducible.
- When only a maximum person count is given, there is no group size to fit against; capacity does not influence the order.
- Capacity fit is judged by the room's smallest seating arrangement that satisfies all arrangement-related filters (person range and, if set, layout), consistent with the matching rule of feature 008.
- Deactivated specialised equipment types assigned to a room still count as unneeded equipment, since they remain physically present; if this is undesired, administrators remove the assignment or reclassify the type.
- Equipment types existing before this feature start as *general*, so the equipment criterion has no effect until an administrator classifies at least one type as specialised.
- A favorite room that is deactivated (or whose floor/building is deactivated) never appears in the ranked results; the favorite mark is kept, the room is shown under "Nicht verfügbar" when "Nur Favoriten" is active, and the mark becomes effective again if the room is reactivated.
- Two marks submitted at the same time (e.g., from two devices) when the user has 9 favorites MUST NOT result in more than 10 favorites; one of them is refused.
- Marking a room that is already a favorite does not count twice and succeeds even at the limit.
- The "Nicht verfügbar" list is not affected by the other filters and is not ranked; it is ordered by building, then room name.
- Rooms that are new or have never been booked have a popularity of zero and are ranked after booked rooms of equal fit; they are never hidden.
- Popularity counts each booking once regardless of its duration or who made it; popularity is the same for every user, and the result list does not reveal who booked a room.
- Marking or unmarking a favorite while results are shown updates the order without the user having to search again.
- The date/time filter (feature 008) is unaffected; ranking is applied only to the rooms that remain after all filters.
- An empty result list behaves exactly as in feature 008 (no-results message and reset action).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST continue to determine the set of result rooms exactly as defined in feature 008; ranking MUST NOT add or remove rooms from the result.
- **FR-002**: System MUST order the search results by the following criteria, each one only deciding when all previous ones are equal:
  1. favorite of the searching user (favorites first);
  2. capacity fit (better size band first, see FR-003);
  3. equipment fit (fewer unrequested specialised equipment types first, see FR-004);
  4. popularity (more counted bookings by all users in the last 90 days first);
  5. building name, then room name (alphabetical), then a fixed internal room identifier (so equal names never swap places).
- **FR-003**: The requested group size MUST be the minimum person count of the search. A room's relevant capacity MUST be the maximum capacity of its smallest arrangement that satisfies all arrangement-related filters; its seat surplus is that capacity minus the group size. Each room MUST be assigned a size band by its surplus relative to the group size: band 1 ≤ 25 %, band 2 ≤ 50 %, band 3 ≤ 100 %, band 4 > 100 %. Rooms in a lower band MUST rank higher; rooms in band 1–3 MUST count as equal within their band; rooms in band 4 MUST be ordered among themselves by exact seat surplus (smaller first). Without a minimum person count, all rooms MUST count as equal on capacity fit.
- **FR-004**: Equipment surplus MUST be the number of equipment types assigned to the room that are classified as specialised and are not among the required equipment types of the search. Equipment types classified as general MUST NOT affect the ranking.
- **FR-004a**: Administrators MUST be able to classify each equipment type in the catalog as *specialised* or *general* when creating or editing it; the default MUST be *general*. Changing a classification MUST take effect on the next search.
- **FR-005**: Popularity MUST be the number of the room's reservations by all users whose start lies within the 90 days before the search and whose status is reserved, active or completed; cancelled and expired reservations MUST NOT count. Future reservations MUST NOT count.
- **FR-006**: Every signed-in user MUST be able to mark and unmark any active room as a personal favorite from the search result list and from the room's detail view.
- **FR-006a**: A user MUST NOT have more than 10 favorites; favorites of rooms that are not available count toward the limit. Marking a room beyond the limit MUST be refused with a message that names the limit; unmarking is always possible.
- **FR-007**: Favorites MUST be personal: they MUST only affect the order and favorite display of the user who set them and MUST NOT be visible to other users.
- **FR-008**: Favorite rooms in the search results MUST be visibly marked as favorites.
- **FR-009**: A favorite MUST only be pinned to the top when the room satisfies all search filters; favorite rooms that do not satisfy the filters MUST NOT be shown in the results (only the "Nicht verfügbar" section of FR-014 lists unavailable favorites).
- **FR-010**: Favorite marks MUST persist across sessions and devices of the same user.
- **FR-011**: Changing a favorite mark while results are displayed MUST update the order of the displayed results immediately.
- **FR-012**: The result list MUST make it apparent that results are ordered by suitability (e.g., a short note such as "Sortiert nach Eignung").
- **FR-013**: Users MUST be able to restrict the search to their own favorites ("Nur Favoriten"); this filter MUST combine with all other filters by AND and MUST be part of the shareable search state like the other filters (feature 008). Ranking applies unchanged.
- **FR-014**: When "Nur Favoriten" is active, the system MUST additionally list the user's favorites that are not fully active (room, floor or building deactivated) in a separate "Nicht verfügbar" section below the results, without a booking action, with a favorite mark that can be removed.
- **FR-015**: When "Nur Favoriten" is active and the user has no favorites, the system MUST show a message explaining how to mark favorites instead of the generic no-results message.

### Key Entities *(include if feature involves data)*

- **Favorite** (new): The fact that a specific user has marked a specific room as favorite. Belongs to exactly one user and one room; a user can have at most one favorite mark per room and at most 10 favorites in total.
- **Room** (existing): Provides seating arrangements with capacities and assigned equipment used for capacity and equipment fit.
- **Equipment Type** (existing): Gains a classification *specialised* or *general* (default *general*); only specialised types count toward equipment surplus. Assignment to a room remains yes/no (no quantities).
- **Reservation** (existing): Provides start time and status used to compute popularity.
- **Room Search Criteria** (existing, from feature 008): Gains a yes/no "favorites only" filter. Minimum persons (group size), seating arrangement, and required equipment types are inputs to ranking. Not stored.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of acceptance test cases, the result order follows the ranking criteria of FR-002 exactly, and repeating the same search with unchanged data yields the same order.
- **SC-002**: For a search with a group size, the first non-favorite result is always a room from the best size band available among all matching rooms; a room more than twice the needed size is never ranked above a matching room that is at most twice the needed size, except as a favorite.
- **SC-003**: A user can mark or unmark a room as favorite in one interaction, and the change is reflected in the result order within 1 second.
- **SC-004**: With up to 500 rooms, ranked results appear within 2 seconds of submitting the search in at least 95% of searches (same target as feature 008).
- **SC-005**: Over time, the average number of unused seats per booking (capacity of the booked arrangement minus requested group size) decreases compared to the period before this feature.

## Assumptions

- "Vortragende:r" is resolved as every signed-in user, consistent with feature 008; favorites are available to every signed-in user.
- This feature supersedes feature 008's assumption that results are sorted by building and room name, and its exclusion of ranking; building and room name remain the final tie-breaker.
- Ranking is lexicographic (strict priority order of criteria) rather than a weighted score, because a weighted score would let popularity compensate for poor size or equipment fit and its weights would be arbitrary and hard to explain; capacity fit is ranked above equipment fit because unused seats are the larger waste of resources. Size bands (rather than exact seat counts) keep equipment fit effective, since many rooms have similar capacities.
- Popularity is placed last among the fit criteria so that it does not undermine resource-friendly ranking by favoring large, frequently booked rooms.
- "Computerraum" is represented by its equipment (e.g., a "PC-Arbeitsplätze" type classified as specialised) in the existing equipment catalog; there is no separate room type. All unrequested specialised equipment types count the same; per-type weights are out of scope.
- Equipment remains yes/no per room; quantities (e.g., number of PCs or sockets) are out of scope. Future equipment types such as power sockets or air conditioning are added via the catalog and classified, without changes to the ranking rules.
- General equipment is never a ranking bonus in this feature; users who need it require it via the equipment filter.
- The popularity window of 90 days is a fixed default; making it configurable is out of scope.
- Users cannot choose a different sort order (e.g., alphabetical) in this feature; the ranked order is the only order of search results.
- The room overview without an active search for administrators (including deactivated rooms) keeps its existing order; ranking applies to the room search.
- The ranking is not explained per room (no visible score); only the favorite mark and the general "sorted by suitability" note are shown.
