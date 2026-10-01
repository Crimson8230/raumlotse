ALTER TABLE reservation ADD COLUMN created_by_user_id UUID REFERENCES user_account (id);

ALTER TABLE equipment_type ADD COLUMN code TEXT;
UPDATE equipment_type SET code = 'PROJECTOR' WHERE lower(name) = 'projector';
UPDATE equipment_type SET code = upper(regexp_replace(name, '[^A-Za-z0-9]+', '_', 'g'))
WHERE code IS NULL;
ALTER TABLE equipment_type ALTER COLUMN code SET NOT NULL;
CREATE UNIQUE INDEX uk_equipment_type_code ON equipment_type (code);

CREATE TABLE room_device_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('LIGHTING', 'VENTILATION', 'PROJECTOR')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    state BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_room_device_state_room_kind UNIQUE (room_id, kind)
);
