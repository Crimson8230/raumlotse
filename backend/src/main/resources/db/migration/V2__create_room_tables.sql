CREATE TABLE room (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    floor_id UUID NOT NULL REFERENCES floor (id),
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX ix_room_floor_id ON room (floor_id);
CREATE INDEX ix_room_status ON room (status);

CREATE TABLE seating_arrangement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    max_capacity INT NOT NULL CHECK (max_capacity > 0)
);

CREATE UNIQUE INDEX uk_seating_arrangement_room_name ON seating_arrangement (room_id, lower(name));
CREATE INDEX ix_seating_arrangement_room_id ON seating_arrangement (room_id);
