CREATE TABLE user_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_account_email UNIQUE (email),
    CONSTRAINT ck_user_account_email_nonempty CHECK (length(email) > 0),
    CONSTRAINT ck_user_account_display_name CHECK (length(btrim(display_name)) BETWEEN 1 AND 120),
    CONSTRAINT ck_user_account_password_hash CHECK (password_hash LIKE '{pbkdf2-sha256-600000-v1}%' AND length(password_hash) > 32)
);

CREATE UNIQUE INDEX uk_user_account_canonical_email ON user_account (lower(btrim(email)));
