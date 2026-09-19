CREATE TABLE reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES room (id),
    seating_arrangement_id UUID NOT NULL REFERENCES seating_arrangement (id),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status TEXT NOT NULL DEFAULT 'RESERVED',
    expected_attendees INT NOT NULL CHECK (expected_attendees > 0),
    note TEXT,
    created_by TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_reservation_time CHECK (end_time > start_time),
    CONSTRAINT chk_reservation_status CHECK (status IN ('RESERVED', 'ACTIVE', 'COMPLETED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX ix_reservation_room_id ON reservation (room_id);
CREATE INDEX ix_reservation_seating_arrangement_id ON reservation (seating_arrangement_id);
CREATE INDEX ix_reservation_room_time_status ON reservation (room_id, status, start_time, end_time);

CREATE TABLE reservation_equipment (
    reservation_id UUID NOT NULL REFERENCES reservation (id) ON DELETE CASCADE,
    equipment_type_id UUID NOT NULL REFERENCES equipment_type (id),
    PRIMARY KEY (reservation_id, equipment_type_id)
);

CREATE INDEX ix_reservation_equipment_equipment_type_id ON reservation_equipment (equipment_type_id);
