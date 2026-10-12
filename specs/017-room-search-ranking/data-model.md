# Data Model: Ranked Room Search Results

Migration: `backend/src/main/resources/db/migration/V17__room_search_ranking.sql` (next free version after V16).

## Changed: EquipmentType

| Field | Type | Rules |
|-------|------|-------|
| `specialised` | boolean, NOT NULL, default `false` | `true` = counts toward equipment surplus when assigned but not required (FR-004). Existing rows become `false` (general). Set by users with `EQUIPMENT_TYPE_MANAGE`. |

Unchanged: id, name, code, status. Deactivated types keep their classification and still count when assigned (spec edge case).

## New: RoomFavorite

| Field | Type | Rules |
|-------|------|-------|
| `user_id` | UUID, FK → `user_account(id)` ON DELETE CASCADE | Always the authenticated user. |
| `room_id` | UUID, FK → `room(id)` ON DELETE CASCADE | Room must be ACTIVE (room, floor, building) when the mark is created. |
| `created_at` | timestamptz, NOT NULL, default now | Informational. |

- Primary key `(user_id, room_id)` → at most one mark per user and room; marking twice is a no-op.
- At most **10** rows per `user_id` (FR-006a), including rows for unavailable rooms; enforced in the service under a pessimistic lock on the user's `user_account` row (research R5a). Re-marking an existing favorite at the limit succeeds (no-op).
- Marks on rooms deactivated later are kept and take effect again on reactivation.
- Never exposed for other users (FR-007).

## Changed (not stored): Room Search Criteria

- New optional `favoritesOnly` (boolean, default false): restricts candidates to the user's favorite room ids; combined with all other filters by AND (FR-013).

## Derived (not stored): ranking keys per room in a search

Computed in `RoomRanking` for each room that passed `RoomSearchCriteria.matches`:

| Key | Definition | Order |
|-----|-----------|-------|
| `favorite` | room id ∈ user's favorite room ids | favorites first |
| `sizeBand` | `group = minPersons`; `capacity` = smallest `maxCapacity` among arrangements satisfying min/max persons and layout; `s = capacity − group`; band 1 if `4s ≤ group`, 2 if `2s ≤ group`, 3 if `s ≤ group`, else 4. No `minPersons` → 0 for all. | ascending |
| `bandSurplus` | `s` if band 4, else 0 | ascending |
| `specialisedSurplus` | count of room's equipment types with `specialised = true` and id ∉ required equipment ids | ascending |
| `popularity` | count of room's reservations with status ∈ {RESERVED, ACTIVE, COMPLETED} and `now − 90 days ≤ startTime ≤ now`, all users | descending |
| tie-break | building name, then room name (case-insensitive), then room id (room names are not unique, SC-001) | ascending |

Comparison is lexicographic over the keys in the order listed (FR-002).

## Unchanged entities used

- **Room / SeatingArrangement**: capacities and equipment assignment (yes/no).
- **Reservation**: `room_id`, `status`, `start_time` for popularity; existing index `ix_reservation_room_time_status` serves the aggregate query.
