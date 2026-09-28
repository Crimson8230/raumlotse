# Data Model: Room Search with Filters

**Feature**: `008-room-search-filter` | **Date**: 2026-09-26 | **Research**: [research.md](research.md)

This feature adds three persisted boolean attributes to existing entities and introduces one transient value object for search criteria. No new tables.

## Schema change — migration `V9__add_barrier_free_attributes.sql`

| Table | New column | Type | Default | Meaning |
|---|---|---|---|---|
| `building` | `has_elevator` | `BOOLEAN NOT NULL` | `false` | The building has an elevator reaching all its floors (FR-009a). |
| `floor` | `ground_floor` | `BOOLEAN NOT NULL` | `false` | The floor is a step-free entrance level; several floors of one building may be marked (FR-009b). |
| `room` | `not_barrier_free` | `BOOLEAN NOT NULL` | `false` | Administrator exclusion: the room is not barrier-free even if its floor/building qualify (FR-009c). |

Existing rows receive `false` for all three columns — "no elevator", "not ground floor", "no exclusion" — so no room is reported as barrier-free reachable until an administrator records elevator or ground-floor information (spec Assumptions). No index is required at the planned scale (≤ 500 rooms).

## Entities (existing, extended)

### Building

| Field | Type | Rules |
|---|---|---|
| `id`, `name`, `status` | existing | unchanged (feature 001) |
| `hasElevator` | boolean | new; default `false`; settable on create and edit |

### Floor

| Field | Type | Rules |
|---|---|---|
| `id`, `buildingId`, `name`, `status` | existing | unchanged (feature 001) |
| `groundFloor` | boolean | new; default `false`; settable on create and edit |

### Room

| Field | Type | Rules |
|---|---|---|
| `id`, `name`, `floor`, `status`, `version`, `seatingArrangements`, `equipmentTypes` | existing | unchanged (feature 001) |
| `notBarrierFree` | boolean | new; default `false`; settable on create and edit; editing it goes through the existing optimistic-locking update (`version`) |
| `barrierFreeReachable` | boolean, **derived, not stored** | `(floor.groundFloor OR floor.building.hasElevator) AND NOT notBarrierFree` |

The derived value is computed on every read, so changing a building's elevator flag or a floor's ground-floor mark immediately affects all its rooms (spec edge case). It is exposed in `RoomResponse` and used by the search.

### Seating Arrangement, Equipment Type, Reservation

Unchanged. Used read-only by the search:

- **Seating Arrangement** `name`, `maxCapacity` — layout and person filters.
- **Equipment Type** `id` — equipment filter (via the room's assigned types).
- **Reservation** `room`, `startTime`, `endTime`, `status` — availability filter; only `RESERVED` and `ACTIVE` block a room.

## Value object — `RoomSearchCriteria` (transient, not stored)

| Field | Type | Validation |
|---|---|---|
| `minPersons` | Integer, optional | ≥ 1 |
| `maxPersons` | Integer, optional | ≥ 1; if both given, `minPersons ≤ maxPersons` |
| `buildingId` | UUID, optional | — (unknown ID yields an empty result) |
| `seatingArrangement` | String, optional | trimmed; blank treated as absent; ≤ 100 characters |
| `equipmentTypeIds` | Set of UUID, optional | — (unknown ID yields an empty result) |
| `barrierFree` | boolean | `true` restricts; `false`/absent does not |
| `from`, `to` | Instant, optional | both or neither; `to` strictly after `from` |

### Matching rule `matches(room, occupiedRoomIds)`

A room matches if **all** of the following hold (FR-010):

1. Room, its floor, and its building are `ACTIVE` (FR-002; enforced by the candidate query).
2. `buildingId` absent, or equals the room's building (FR-005; enforced by the candidate query).
3. **Arrangement rule** (FR-004, FR-006, FR-007): if any of `minPersons`, `maxPersons`, `seatingArrangement` is given, at least one seating arrangement of the room satisfies *all given ones at once*:
   `(minPersons absent OR maxCapacity ≥ minPersons) AND (maxPersons absent OR maxCapacity ≤ maxPersons) AND (seatingArrangement absent OR name equalsIgnoreCase seatingArrangement)`.
4. Every ID in `equipmentTypeIds` is among the room's assigned equipment types (FR-008).
5. `barrierFree` is false, or `room.barrierFreeReachable` is true (FR-009).
6. `from`/`to` absent, or the room ID is not in `occupiedRoomIds` (FR-014).

### Occupied rooms

`occupiedRoomIds(from, to)` = distinct `reservation.room_id` where `status IN ('RESERVED','ACTIVE') AND start_time < :to AND end_time > :from` — half-open `[start, end)`, identical to the reservation feature's conflict rule, so back-to-back windows do not block.

## Ordering

Results are sorted by building name, then room name, both case-insensitive.
