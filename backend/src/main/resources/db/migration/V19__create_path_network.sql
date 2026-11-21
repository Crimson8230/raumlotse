-- Feature 023: path network (waypoints, segments), per-map reference segment, and attachments of
-- room placements and connection points. Positions are fractions (0..1) of the map image size.

-- Composite-FK target so a connection point attachment is guaranteed to refer to the same map.
-- room_placement is left untouched: a second unique index there would break its concurrent upsert (feature 012);
-- the room/map match of a room attachment is checked by the service instead.
ALTER TABLE connection_point ADD CONSTRAINT uk_connection_point_id_map UNIQUE (id, map_id);

CREATE TABLE path_waypoint (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    map_id UUID NOT NULL REFERENCES floor_map (id) ON DELETE CASCADE,
    x NUMERIC(6, 5) NOT NULL,
    y NUMERIC(6, 5) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_path_waypoint_x CHECK (x >= 0 AND x <= 1),
    CONSTRAINT ck_path_waypoint_y CHECK (y >= 0 AND y <= 1),
    CONSTRAINT uk_path_waypoint_id_map UNIQUE (id, map_id)
);

CREATE INDEX ix_path_waypoint_map_id ON path_waypoint (map_id);

CREATE TABLE path_segment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    map_id UUID NOT NULL,
    a_id UUID NOT NULL,
    b_id UUID NOT NULL,
    CONSTRAINT ck_path_segment_order CHECK (a_id < b_id),
    CONSTRAINT uk_path_segment_pair UNIQUE (a_id, b_id),
    CONSTRAINT fk_path_segment_a FOREIGN KEY (a_id, map_id) REFERENCES path_waypoint (id, map_id) ON DELETE CASCADE,
    CONSTRAINT fk_path_segment_b FOREIGN KEY (b_id, map_id) REFERENCES path_waypoint (id, map_id) ON DELETE CASCADE
);

CREATE INDEX ix_path_segment_map_id ON path_segment (map_id);
CREATE INDEX ix_path_segment_a_id ON path_segment (a_id);
CREATE INDEX ix_path_segment_b_id ON path_segment (b_id);

CREATE TABLE map_reference_segment (
    map_id UUID PRIMARY KEY REFERENCES floor_map (id) ON DELETE CASCADE,
    x1 NUMERIC(6, 5) NOT NULL,
    y1 NUMERIC(6, 5) NOT NULL,
    x2 NUMERIC(6, 5) NOT NULL,
    y2 NUMERIC(6, 5) NOT NULL,
    length_m NUMERIC(10, 3) NOT NULL,
    CONSTRAINT ck_map_reference_x1 CHECK (x1 >= 0 AND x1 <= 1),
    CONSTRAINT ck_map_reference_y1 CHECK (y1 >= 0 AND y1 <= 1),
    CONSTRAINT ck_map_reference_x2 CHECK (x2 >= 0 AND x2 <= 1),
    CONSTRAINT ck_map_reference_y2 CHECK (y2 >= 0 AND y2 <= 1),
    CONSTRAINT ck_map_reference_length CHECK (length_m > 0 AND length_m <= 100000)
);

CREATE TABLE room_path_attachment (
    room_id UUID PRIMARY KEY,
    map_id UUID NOT NULL,
    waypoint_id UUID NOT NULL,
    CONSTRAINT fk_room_path_attachment_placement FOREIGN KEY (room_id)
        REFERENCES room_placement (room_id) ON DELETE CASCADE,
    CONSTRAINT fk_room_path_attachment_waypoint FOREIGN KEY (waypoint_id, map_id)
        REFERENCES path_waypoint (id, map_id) ON DELETE CASCADE
);

CREATE INDEX ix_room_path_attachment_map_id ON room_path_attachment (map_id);
CREATE INDEX ix_room_path_attachment_waypoint_id ON room_path_attachment (waypoint_id);

CREATE TABLE connection_point_path_attachment (
    connection_point_id UUID PRIMARY KEY,
    map_id UUID NOT NULL,
    waypoint_id UUID NOT NULL,
    CONSTRAINT fk_cp_path_attachment_point FOREIGN KEY (connection_point_id, map_id)
        REFERENCES connection_point (id, map_id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_path_attachment_waypoint FOREIGN KEY (waypoint_id, map_id)
        REFERENCES path_waypoint (id, map_id) ON DELETE CASCADE
);

CREATE INDEX ix_cp_path_attachment_map_id ON connection_point_path_attachment (map_id);
CREATE INDEX ix_cp_path_attachment_waypoint_id ON connection_point_path_attachment (waypoint_id);
