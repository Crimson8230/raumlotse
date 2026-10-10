-- Feature 014 (FR-022): admin-editable check-in times. Exactly one row; the defaults are the former code constants.
CREATE TABLE check_in_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    early_check_in_minutes INTEGER NOT NULL DEFAULT 10 CHECK (early_check_in_minutes BETWEEN 0 AND 60),
    grace_period_minutes INTEGER NOT NULL DEFAULT 5 CHECK (grace_period_minutes BETWEEN 1 AND 30),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by_user_id UUID NULL
);

INSERT INTO check_in_settings (id) VALUES (1);
