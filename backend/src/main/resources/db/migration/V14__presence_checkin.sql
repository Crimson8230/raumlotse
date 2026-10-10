-- Feature 014: on-site presence check-in.
-- Check-in record and latest detected presence on the reservation (all nullable; existing rows stay NULL).
ALTER TABLE reservation
    ADD COLUMN check_in_method TEXT NULL CHECK (check_in_method IN ('QR', 'NFC', 'MANUAL')),
    ADD COLUMN checked_in_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN checked_in_by_user_id UUID NULL,
    ADD COLUMN last_presence_at TIMESTAMP WITH TIME ZONE NULL;

-- The door becomes a simulated room device (state TRUE = unlocked). V11 declared the kind check inline,
-- so PostgreSQL named it room_device_state_kind_check.
ALTER TABLE room_device_state DROP CONSTRAINT room_device_state_kind_check;
ALTER TABLE room_device_state ADD CONSTRAINT room_device_state_kind_check
    CHECK (kind IN ('LIGHTING', 'VENTILATION', 'PROJECTOR', 'DOOR'));
