# Data Model: Path Network

Migration `V19__create_path_network.sql`. IDs are UUID (`gen_random_uuid()`), coordinates `NUMERIC(6,5)` fractions of the map's width/height with `CHECK 0 ≤ v ≤ 1`, consistent with Feature 012 (`V13`). No version columns (last write wins).

## Changes to existing tables
- `room_placement`: unchanged (an extra unique index would break its concurrent upsert from feature 012).
- `connection_point`: add `UNIQUE (id, map_id)` (target of a composite FK).
- No permission changes: writes use the existing `MAP_MANAGE`.

## path_waypoint
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| map_id | UUID NOT NULL → floor_map(id) ON DELETE CASCADE | |
| x, y | NUMERIC(6,5) NOT NULL | CHECK 0..1 |
| created_at, updated_at | TIMESTAMPTZ | |
| | UNIQUE (id, map_id) | composite FK target |

Index on `map_id`.

## path_segment
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| map_id | UUID NOT NULL | |
| a_id, b_id | UUID NOT NULL | CHECK `a_id < b_id` (normalized pair, no self-loop) |
| | FK (a_id, map_id) → path_waypoint(id, map_id) ON DELETE CASCADE | same-map |
| | FK (b_id, map_id) → path_waypoint(id, map_id) ON DELETE CASCADE | same-map |
| | UNIQUE (a_id, b_id) | no duplicate pair |

Index on `map_id`, `a_id`, `b_id`. Length is derived (see below), not stored.

## map_reference_segment
| Column | Type | Notes |
|---|---|---|
| map_id | UUID PK → floor_map(id) ON DELETE CASCADE | at most one per map |
| x1, y1, x2, y2 | NUMERIC(6,5) NOT NULL | CHECK 0..1; service rejects identical endpoints |
| length_m | NUMERIC(10,3) NOT NULL | CHECK > 0 and ≤ 100000 |

## room_path_attachment
| Column | Type | Notes |
|---|---|---|
| room_id | UUID PK | at most one per room |
| map_id | UUID NOT NULL | |
| waypoint_id | UUID NOT NULL | |
| | FK (room_id) → room_placement(room_id) ON DELETE CASCADE | only placed rooms; removed with placement/room; service checks that the placement is on the same map |
| | FK (waypoint_id, map_id) → path_waypoint(id, map_id) ON DELETE CASCADE | same-map; removed with waypoint |

## connection_point_path_attachment
| Column | Type | Notes |
|---|---|---|
| connection_point_id | UUID PK | at most one per point |
| map_id | UUID NOT NULL | |
| waypoint_id | UUID NOT NULL | |
| | FK (connection_point_id, map_id) → connection_point(id, map_id) ON DELETE CASCADE | |
| | FK (waypoint_id, map_id) → path_waypoint(id, map_id) ON DELETE CASCADE | |

## Derived (computed on read)
- `pixelsPerMeter` = `hypot((x2-x1)*width_px, (y2-y1)*height_px) / length_m`, or `null` without a reference segment.
- `segment.lengthMeters` = `hypot((bx-ax)*width_px, (by-ay)*height_px) / pixelsPerMeter`, or `null` without scale.
- `components`: connected components of the waypoint/segment graph (union-find).
- `issues`: missing scale; placed rooms without attachment; connection points without attachment; attachments whose waypoint lies outside the largest component; connections not fully attached.
- `connectionGroups`: per connection, its points with map, point id, and attached waypoint id (or `null`).

## Lifecycle
- Waypoint delete → its segments and attachments cascade (rooms/points become unattached).
- Segment delete → only that segment.
- Segment split → one transaction: delete segment, insert waypoint, insert two segments.
- Placement removal, room deletion, connection point removal, connection deletion, or map deletion → attachments (and for map: waypoints, segments, reference segment) cascade.
- Image replacement → no change to stored rows; derived pixel values follow the new image size; aspect-ratio change keeps the existing warning from Feature 012.
