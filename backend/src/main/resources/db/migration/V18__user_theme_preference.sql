ALTER TABLE user_account
    ADD COLUMN theme_code VARCHAR(32),
    ADD COLUMN theme_onboarding_completed BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE user_account ALTER COLUMN theme_onboarding_completed SET DEFAULT FALSE;
