ALTER TABLE reservation ADD COLUMN reserved_for VARCHAR(255);
UPDATE reservation SET reserved_for = created_by WHERE reserved_for IS NULL;
ALTER TABLE reservation ALTER COLUMN reserved_for SET NOT NULL;
ALTER TABLE reservation ADD CONSTRAINT ck_reservation_reserved_for_nonempty
    CHECK (length(btrim(reserved_for)) BETWEEN 1 AND 255);

CREATE INDEX ix_reservation_user_upcoming
    ON reservation (created_by, status, end_time, start_time);
