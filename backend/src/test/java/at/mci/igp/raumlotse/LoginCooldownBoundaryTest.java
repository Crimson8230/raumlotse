package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.service.LoginAttemptIdentity;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class LoginCooldownBoundaryTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LoginAttemptIdentity identity;
    @Autowired JdbcTemplate jdbc;

    @Test
    void exactWindowBoundaryIsExcludedAndSuccessfulLoginClearsActiveHistory() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        accounts.saveAndFlush(new UserAccount(email, "Boundary User", passwordEncoder.encode("correct-password")));
        seed(email, new Integer[] {15, 14, 13, 12}, null);

        var failed = submit(email, "wrong-password");
        assertThat(failed.getResponse().getStatus()).isEqualTo(401);
        assertThat(failureCount(email)).isEqualTo(4); // Exact 15-minute failure was removed.

        var success = submit(email, "correct-password");
        assertThat(success.getResponse().getStatus()).isEqualTo(200);
        assertThat(failureCount(email)).isZero();
        assertThat(blockedUntil(email)).isNull();
    }

    @Test
    void expiredCooldownHistoryDoesNotBlockAndValidationDoesNotCreateHistory() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        seed(email, new Integer[] {20, 19, 18, 17, 16}, Timestamp.from(java.time.Instant.now().minusSeconds(1)));

        var failed = submit(email, "wrong-password");
        assertThat(failed.getResponse().getStatus()).isEqualTo(401);
        assertThat(failureCount(email)).isEqualTo(1);
        assertThat(blockedUntil(email)).isNull();

        String invalidEmail = UUID.randomUUID() + "@example.test";
        assertThat(submit(invalidEmail, "").getResponse().getStatus()).isEqualTo(400);
        assertThat(historyExists(invalidEmail)).isFalse();
    }

    private MvcResult submit(String email, String password) throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        MockHttpSession session = (MockHttpSession) bootstrap.getRequest().getSession(false);
        return mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private void seed(String email, Integer[] minutesAgo, Timestamp blockedUntil) {
        byte[] key = identity.derive(email);
        jdbc.update("""
                INSERT INTO login_attempt_state(identity_key, failure_times, expires_at)
                VALUES (?, '{}'::timestamptz[], clock_timestamp() + interval '1 hour')
                ON CONFLICT (identity_key) DO NOTHING
                """, key);
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    UPDATE login_attempt_state
                    SET failure_times = ARRAY(
                            SELECT clock_timestamp() - minute_value * interval '1 minute'
                            FROM unnest(?::integer[]) AS minute_value
                            ORDER BY minute_value DESC),
                        blocked_until = ?,
                        expires_at = clock_timestamp() + interval '1 hour'
                    WHERE identity_key = ?
                    """);
            statement.setArray(1, connection.createArrayOf("integer", minutesAgo));
            statement.setTimestamp(2, blockedUntil);
            statement.setBytes(3, key);
            return statement;
        });
    }

    private int failureCount(String email) {
        return jdbc.queryForObject("SELECT cardinality(failure_times) FROM login_attempt_state WHERE identity_key = ?",
                Integer.class, identity.derive(email));
    }

    private Timestamp blockedUntil(String email) {
        return jdbc.queryForObject("SELECT blocked_until FROM login_attempt_state WHERE identity_key = ?",
                Timestamp.class, identity.derive(email));
    }

    private boolean historyExists(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM login_attempt_state WHERE identity_key = ?)", Boolean.class,
                identity.derive(email)));
    }
}
