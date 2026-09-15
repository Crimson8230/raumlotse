CREATE TABLE equipment_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE UNIQUE INDEX uk_equipment_type_name ON equipment_type (lower(name));

CREATE TABLE room_equipment (
    room_id UUID NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    equipment_type_id UUID NOT NULL REFERENCES equipment_type (id),
    PRIMARY KEY (room_id, equipment_type_id)
);

INSERT INTO equipment_type (name) VALUES ('Projector'), ('Whiteboard');
