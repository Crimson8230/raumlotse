# Data Model: Room Map Placement

Migration `V13__create_room_map_tables.sql`. IDs are UUID (`gen_random_uuid()`), optimistic `version BIGINT` where edited concurrently, consistent with existing tables.

## floor_map
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| floor_id | UUID NOT NULL UNIQUE → floor(id) | one map per floor |
| content_type | TEXT NOT NULL | `image/png` \| `image/jpeg` (CHECK) |
| width_px, height_px | INT NOT NULL | > 0 |
| image_version | BIGINT NOT NULL DEFAULT 1 | incremented on replace; ETag |
| version | BIGINT | optimistic lock |
| created_at, updated_at | TIMESTAMPTZ | |

## floor_map_image
| Column | Type | Notes |
|---|---|---|
| map_id | UUID PK → floor_map(id) ON DELETE CASCADE | separate table so lists/details never load image bytes |
| image | BYTEA NOT NULL | ≤ 10 MB |

## room_placement
| Column | Type | Notes |
|---|---|---|
| room_id | UUID PK → room(id) ON DELETE CASCADE | at most one placement per room (FR-005/015) |
| map_id | UUID NOT NULL → floor_map(id) ON DELETE CASCADE | |
| x, y | NUMERIC(6,5) NOT NULL | CHECK 0 ≤ v ≤ 1 |
| | | last write wins (upsert); no version column |

Rule (service): `room.floor_id == floor_map.floor_id`, else 422. Index on `map_id`.

## connection
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| name | TEXT NOT NULL | unique case-insensitive |
| type | TEXT NOT NULL | CHECK IN (`STAIRS`,`ELEVATOR`) |
| version | BIGINT | |

## connection_point
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| connection_id | UUID NOT NULL → connection(id) ON DELETE CASCADE | |
| map_id | UUID NOT NULL → floor_map(id) ON DELETE CASCADE | |
| x, y | NUMERIC(6,5) NOT NULL | CHECK 0..1 |
| | UNIQUE (connection_id, map_id) | at most one point per connection per map |

## Derived
- `incomplete` = connection has < 2 points. Reachable maps of a point = other maps of the same connection.
- Unplaced rooms of a map = rooms of the map's floor without `room_placement`.

## State/lifecycle
Map delete → placements and points cascade; rooms become unplaced. Room delete → placement cascades. Connection delete → all points cascade. Image replace → `image_version++`, placements unchanged.
