CREATE TABLE login_attempt_state (
    identity_key BYTEA PRIMARY KEY,
    failure_times TIMESTAMPTZ[] NOT NULL DEFAULT '{}'::timestamptz[],
    blocked_until TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_login_attempt_identity_key CHECK (octet_length(identity_key) = 32),
    CONSTRAINT ck_login_attempt_failure_count CHECK (cardinality(failure_times) <= 5),
    CONSTRAINT ck_login_attempt_failures_nonnull CHECK (array_position(failure_times, NULL) IS NULL)
);

CREATE INDEX ix_login_attempt_expires_at ON login_attempt_state (expires_at);
