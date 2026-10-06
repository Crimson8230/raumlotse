-- Feature 012: floor maps, room placements, stairs/elevator connections.
-- Positions are fractions (0..1) of the image width/height so they are independent of display size.
CREATE TABLE floor_map (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    floor_id UUID NOT NULL UNIQUE REFERENCES floor (id),
    content_type TEXT NOT NULL,
    width_px INT NOT NULL,
    height_px INT NOT NULL,
    image_version BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_floor_map_content_type CHECK (content_type IN ('image/png', 'image/jpeg')),
    CONSTRAINT ck_floor_map_dimensions CHECK (width_px > 0 AND height_px > 0)
);

-- Image bytes live in their own table so map lists and details never load them.
CREATE TABLE floor_map_image (
    map_id UUID PRIMARY KEY REFERENCES floor_map (id) ON DELETE CASCADE,
    image BYTEA NOT NULL
);

CREATE TABLE room_placement (
    room_id UUID PRIMARY KEY REFERENCES room (id) ON DELETE CASCADE,
    map_id UUID NOT NULL REFERENCES floor_map (id) ON DELETE CASCADE,
    x NUMERIC(6, 5) NOT NULL,
    y NUMERIC(6, 5) NOT NULL,
    CONSTRAINT ck_room_placement_x CHECK (x >= 0 AND x <= 1),
    CONSTRAINT ck_room_placement_y CHECK (y >= 0 AND y <= 1)
);

CREATE INDEX ix_room_placement_map_id ON room_placement (map_id);

CREATE TABLE connection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_connection_type CHECK (type IN ('STAIRS', 'ELEVATOR'))
);

CREATE UNIQUE INDEX uk_connection_name ON connection (lower(name));

CREATE TABLE connection_point (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id UUID NOT NULL REFERENCES connection (id) ON DELETE CASCADE,
    map_id UUID NOT NULL REFERENCES floor_map (id) ON DELETE CASCADE,
    x NUMERIC(6, 5) NOT NULL,
    y NUMERIC(6, 5) NOT NULL,
    CONSTRAINT uk_connection_point_map UNIQUE (connection_id, map_id),
    CONSTRAINT ck_connection_point_x CHECK (x >= 0 AND x <= 1),
    CONSTRAINT ck_connection_point_y CHECK (y >= 0 AND y <= 1)
);

CREATE INDEX ix_connection_point_map_id ON connection_point (map_id);
