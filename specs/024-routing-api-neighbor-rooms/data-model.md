# Data Model: Routing and Neighbor Rooms

## Schema change (migration `V20__floor_position.sql`)

`floor.position INT NOT NULL`, not unique (ties are resolved by name, then id), backfilled per building: `ground_floor = true` first, then by name → 0..n-1. The column `ground_floor` is kept and synchronized by the application (`position == 0`), so existing readers keep working.

New floors get `max(position) + 1` of their building. Deleting a floor closes the gap in the same transaction. Reordering sends the complete ordered list of the building's floors.

## Derived (computed per request, not stored)

### Graph
- Node = waypoint (all maps with a scale). Edge = segment (cost seconds = meters / 1.2, meters kept). Connection edge = pair of attached points of one connection on different maps; cost 15 s × floor steps (stairs) or 30 s (elevator); excluded when `accessibleOnly` and type is stairs, when any point of the pair is unattached, or when the two maps belong to floors of different buildings (not supported).
- Room entry node = the waypoint of its room attachment.

### Route
| Field | Notes |
|---|---|
| fromRoomId, toRoomId, accessibleOnly | echo of request |
| distanceMeters | sum of segment meters |
| walkingSeconds | total cost incl. floor changes |
| steps[] | ordered: `{mapId, floorName, waypointId, x, y}` plus `floorChange` marker `{connectionId, connectionName, type, fromFloorName, toFloorName}` |

### Neighbor entry
| Field | Notes |
|---|---|
| direction | `LEFT`, `RIGHT`, `STRAIGHT`, `UP`, `DOWN` |
| kind | `ROOM`, `STAIRS`, `ELEVATOR` |
| roomId, name | room entries |
| connectionId, name, targetFloorName | stairs/elevator entries |
| distanceMeters, walkingSeconds | to the room's entrance / the connection point |
| status | `AVAILABLE`/`RESERVED`/`OCCUPIED`, room entries only, optional |

### Neighbors result
`{roomId, available, reason?, entries[]}`; reason ∈ `ROOM_NOT_ATTACHED`, `SCALE_MISSING`, `NO_ORIENTATION`, `NO_NEIGHBORS`. Entries ordered: left, straight, right by distance, then up, down.

## Rules
- Only active rooms are candidates/targets; rooms in other network components are unreachable by construction.
- Each side keeps ≤ 3 room entries; per vertical direction ≤ 1 stairs and ≤ 1 elevator entry.
- Route to itself: distance 0, one step.
