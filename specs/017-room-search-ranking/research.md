# Research: Ranked Room Search Results

All Technical Context items were known from the existing codebase; no NEEDS CLARIFICATION remained. The decisions below record the design choices made for this feature.

## R1 — Where ranking happens

- **Decision**: Rank in the backend, in `RoomSearchService`, after the existing in-memory filtering, using a new pure domain class `RoomRanking` (a `Comparator<Room>` built from the criteria, the user's favorite room ids and a popularity map).
- **Rationale**: The search already loads all candidate rooms and filters them in memory (`RoomSearchCriteria.matches`), so ranking the same list costs nothing extra and keeps one source of truth for the order. A pure comparator is trivially unit-testable (Constitution I) and needs no new query shape.
- **Alternatives considered**: SQL `ORDER BY` with computed band columns — duplicates the arrangement-matching rule in SQL, harder to test. Ranking in the frontend — would require shipping popularity and equipment classification to the client and would split the contract (Constitution III).

## R2 — Size band computation

- **Decision**: Integer arithmetic on seat surplus `s = capacity − group` (capacity = smallest `maxCapacity` among arrangements that satisfy the arrangement rule): band 1 if `4·s ≤ group`, band 2 if `2·s ≤ group`, band 3 if `s ≤ group`, else band 4. Comparator key: `(band, band == 4 ? s : 0)`. Without `minPersons`, key is `(0, 0)` for all rooms.
- **Rationale**: Avoids floating-point boundary errors; inclusive limits match the spec edge case (group 20: 25 seats → band 1, 26 → band 2).
- **Alternatives considered**: Percentage as `double` — boundary flakiness. Exact surplus everywhere — rejected in clarification.

## R3 — Equipment classification storage

- **Decision**: New column `equipment_type.specialised BOOLEAN NOT NULL DEFAULT false`; exposed as optional `specialised` on `EquipmentTypeRequest` (null → keep on update, false on create) and always on `EquipmentTypeResponse`.
- **Rationale**: A boolean is the smallest model for the agreed two-class scheme; default false satisfies "new and existing types start as general". Additive and backward compatible for existing callers.
- **Alternatives considered**: Enum column `classification` — no third class planned (YAGNI). Numeric weight — rejected in clarification.

## R4 — Popularity query

- **Decision**: One aggregate query per search: `select r.room.id, count(r) from Reservation r where r.status in (RESERVED, ACTIVE, COMPLETED) and r.startTime >= :since and r.startTime <= :now group by r.room.id`, with `since = now − 90 days`, using the `Clock` bean from `ClockConfig` (injected into `RoomSearchService` as in `ReservationService`). Rooms absent from the result have popularity 0.
- **Rationale**: Uses existing index `ix_reservation_room_time_status (room_id, status, start_time, end_time)`; one round trip regardless of room count (≤ 500 rooms target, SC-004).
- **Alternatives considered**: Denormalised counter on `room` — needs maintenance on every status change; premature. Per-room count — N+1 queries.

## R5 — Favorites model and API

- **Decision**: New table `room_favorite (user_id → user_account ON DELETE CASCADE, room_id → room ON DELETE CASCADE, created_at, PK (user_id, room_id))`. Endpoints under the current user: `GET /api/me/favorite-rooms` (list of favorites with room id, name, building, floor and `available` flag, so the frontend can render the "Nicht verfügbar" section without extra calls), `PUT /api/me/favorite-rooms/{roomId}` and `DELETE /api/me/favorite-rooms/{roomId}` (both idempotent, 204). Authorised with permission `READ` in `RoleAccessFilter`. The user id always comes from the authenticated principal, never from the request.
- **Rationale**: Keeps `RoomResponse` (shared by many endpoints) unchanged; the frontend fetches favorite ids once and uses them both in search results and room detail. The composite key enforces "at most one mark per room per user". Using the principal makes favorites strictly personal (FR-007, Constitution IV).
- **Alternatives considered**: `favorite` flag on `RoomResponse` — would require the current user in every room endpoint. `/api/rooms/{id}/favorite` — reads as a room property and collides visually with the `/api/rooms/[^/]+` READ patterns.
- **Marking deactivated rooms**: `PUT` on a non-active room → 404 `ROOM_NOT_FOUND` semantics matching "mark any active room" (FR-006); existing marks on later-deactivated rooms are kept (spec edge case).

## R5a — Favorites-only filter and limit of 10

- **Decision**: New optional search parameter `favoritesOnly=true`; `RoomSearchService` restricts candidates to the user's favorite ids before ranking. The "Nicht verfügbar" section is rendered by the frontend from `GET /api/me/favorite-rooms` entries with `available = false`, sorted by building, room name. The limit is enforced in `RoomFavoriteService.mark` inside one transaction that first locks the user's `user_account` row (`SELECT … FOR UPDATE` via a `@Lock(PESSIMISTIC_WRITE)` repository method), then counts favorites; ≥ 10 and room not yet marked → 409 `FAVORITE_LIMIT_REACHED`.
- **Rationale**: The filter reuses the existing search pipeline and URL state (feature 008). Locking the user row serialises concurrent marks of the same user only, so two parallel requests at 9 favorites cannot both succeed (spec edge case) without a table-wide lock.
- **Alternatives considered**: Database trigger enforcing the count — logic hidden from the service layer and harder to test. Counting without a lock — race allows 11 favorites. Separate endpoint for unavailable favorites — one more call for the same data.

## R6 — Immediate reorder after toggling a favorite

- **Decision**: After a successful `PUT`/`DELETE`, the result list re-runs the current search (same query string) and updates the favorite id set.
- **Rationale**: The server is the single source of the order (R1); re-fetching ≤ 500 rooms is well within the 1 s target (SC-003). Optimistic toggle of the star icon keeps the interaction feeling instant.
- **Alternatives considered**: Client-side re-sorting — would need the full ranking keys on the client.

## R7 — Keeping the public service signature

- **Decision**: Keep `RoomSearchService.search(RoomSearchCriteria)` (ranks with no favorites) and add `search(RoomSearchCriteria, UUID userId)`; the controller resolves the user id from `Authentication` the same way `ReservationController` does.
- **Rationale**: Existing tests and callers keep working (repo guidance: keep public signatures).
