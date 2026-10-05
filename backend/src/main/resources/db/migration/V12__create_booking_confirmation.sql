CREATE TABLE booking_confirmation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL UNIQUE REFERENCES reservation (id),
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processing_started_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_booking_confirmation_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    CONSTRAINT ck_booking_confirmation_attempt_count
        CHECK (attempt_count BETWEEN 0 AND 1),
    CONSTRAINT ck_booking_confirmation_failure_code
        CHECK (failure_code IS NULL OR failure_code IN (
            'RECIPIENT_UNAVAILABLE', 'MESSAGE_FORMAT_FAILED', 'SMTP_REJECTED', 'WORKER_INTERRUPTED')),
    CONSTRAINT ck_booking_confirmation_state_fields CHECK (
        (status = 'PENDING' AND attempt_count = 0 AND processing_started_at IS NULL
            AND sent_at IS NULL AND failed_at IS NULL AND failure_code IS NULL)
        OR (status = 'PROCESSING' AND attempt_count = 1 AND processing_started_at IS NOT NULL
            AND sent_at IS NULL AND failed_at IS NULL AND failure_code IS NULL)
        OR (status = 'SENT' AND attempt_count = 1 AND processing_started_at IS NOT NULL
            AND sent_at IS NOT NULL AND failed_at IS NULL AND failure_code IS NULL)
        OR (status = 'FAILED' AND attempt_count = 1 AND processing_started_at IS NOT NULL
            AND failed_at IS NOT NULL AND sent_at IS NULL AND failure_code IS NOT NULL)
    )
);

CREATE INDEX ix_booking_confirmation_pending
    ON booking_confirmation (created_at, id)
    WHERE status = 'PENDING';

CREATE INDEX ix_booking_confirmation_processing
    ON booking_confirmation (processing_started_at, id)
    WHERE status = 'PROCESSING';
