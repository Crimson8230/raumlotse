package at.mci.igp.raumlotse.config;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.service.EmailCanonicalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;

@Configuration
@Profile("local-auth-fixture")
public class LocalAuthFixtureConfiguration {
    @Bean
    ApplicationRunner provisionLocalAuthFixture(UserAccountRepository accounts, PasswordEncoder encoder,
            EmailCanonicalizer canonicalizer, Environment environment, JdbcTemplate jdbc,
            @Value("${AUTH_FIXTURE_EMAIL}") String email,
            @Value("${AUTH_FIXTURE_DISPLAY_NAME}") String displayName,
            @Value("${AUTH_FIXTURE_PASSWORD}") String password) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException("Local auth fixtures cannot be enabled with a production profile.");
        }
        if (email.isBlank() || displayName.isBlank() || password.length() < 1 || password.length() > 1024) {
            throw new IllegalStateException("Local auth fixture credentials are invalid.");
        }
        String canonicalEmail = canonicalizer.canonicalize(email);
        String canonicalName = displayName.strip();
        if (canonicalName.isEmpty() || canonicalName.length() > 120) {
            throw new IllegalStateException("Local auth fixture display name is invalid.");
        }
        return args -> {
            UserAccount account = accounts.findByEmail(canonicalEmail).orElseGet(
                    () -> accounts.saveAndFlush(new UserAccount(canonicalEmail, canonicalName, encoder.encode(password))));
            grantAdminIfUnassigned(jdbc, account.getId());
        };
    }

    /**
     * Initial Admin provisioning is external to the application, so a local fixture account would otherwise have no
     * role at all. It becomes ADMIN only while it has no role assignment; roles changed later are never overwritten.
     */
    private static void grantAdminIfUnassigned(JdbcTemplate jdbc, UUID userId) {
        jdbc.update("INSERT INTO user_role_state(user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING", userId);
        jdbc.update("""
                INSERT INTO role_assignment(user_id, role_code)
                SELECT ?, 'ADMIN' WHERE NOT EXISTS (SELECT 1 FROM role_assignment WHERE user_id = ?)
                """, userId, userId);
    }
}
