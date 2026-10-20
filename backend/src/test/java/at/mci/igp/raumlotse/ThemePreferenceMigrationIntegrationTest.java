package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThemePreferenceMigrationIntegrationTest extends MapIntegrationSupport {
    @Test
    void newAccountsStartWithPendingChoiceAndNoTheme() {
        assertThat(db.queryForObject("select theme_code from user_account where id=?", String.class, user)).isNull();
        assertThat(db.queryForObject("select theme_onboarding_completed from user_account where id=?", Boolean.class, user)).isFalse();
    }

    @Test
    void migrationDefinesCompletedDefaultForExistingAccounts() throws Exception {
        String script = new String(getClass().getResourceAsStream("/db/migration/V18__user_theme_preference.sql")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(script).contains("DEFAULT TRUE", "DEFAULT FALSE");
        UUID existing = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash,theme_onboarding_completed) values (?,?,?,?,true)",
                existing, "existing@example.test", "Existing", "{pbkdf2-sha256-600000-v1}test-fixture-not-a-real-password");
        assertThat(db.queryForObject("select theme_onboarding_completed from user_account where id=?", Boolean.class, existing)).isTrue();
    }
}
