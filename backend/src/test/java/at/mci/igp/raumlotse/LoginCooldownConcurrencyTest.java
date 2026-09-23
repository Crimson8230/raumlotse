package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.repository.LoginAttemptStateRepository;
import at.mci.igp.raumlotse.service.LoginAttemptCleanupService;
import at.mci.igp.raumlotse.service.LoginAttemptIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
class LoginCooldownConcurrencyTest extends AbstractIntegrationTest {
    private record SessionToken(MockHttpSession session, String token) {
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    UserAccountRepository accounts;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    LoginAttemptIdentity identities;
    @Autowired
    LoginAttemptStateRepository states;
    @Autowired
    LoginAttemptCleanupService cleanup;

    @Test
    void simultaneousFailuresSerializeAndCanonicalEmailVariantsShareTheCooldown() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        accounts.saveAndFlush(new UserAccount(email, "Concurrent User", passwordEncoder.encode("correct-password")));
        List<SessionToken> clients = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            clients.add(bootstrap());

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(5);
        try {
            List<java.util.concurrent.Future<Integer>> attempts = new ArrayList<>();
            for (SessionToken client : clients) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return post(email, "wrong-password", client).getResponse().getStatus();
                }));
            }
            start.countDown();
            for (var attempt : attempts)
                assertThat(attempt.get(30, TimeUnit.SECONDS)).isEqualTo(401);
        } finally {
            executor.shutdownNow();
        }

        SessionToken equivalentAddress = bootstrap();
        var blocked = post("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", "correct-password",
                equivalentAddress);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);

        SessionToken independentAddress = bootstrap();
        var independent = post(UUID.randomUUID() + "@example.test", "wrong-password", independentAddress);
        assertThat(independent.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void correctPasswordQueuedBehindFifthFailureSeesCommittedCooldown() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        accounts.saveAndFlush(new UserAccount(email, "Queued User", passwordEncoder.encode("correct-password")));
        for (int i = 0; i < 4; i++)
            assertThat(post(email, "wrong-password", bootstrap()).getResponse().getStatus()).isEqualTo(401);
        byte[] key = identities.derive(email);

        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        Future<?> holder = executor
                .submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.queryForObject(
                            "SELECT identity_key FROM login_attempt_state WHERE identity_key = ? FOR UPDATE",
                            (rs, row) -> rs.getBytes(1), key);
                    acquired.countDown();
                    await(release);
                }));
        try {
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Integer> fifthFailure = executor
                    .submit(() -> post(email, "wrong-password", bootstrap()).getResponse().getStatus());
            awaitLockWaiters(1);
            Future<Integer> correctPassword = executor
                    .submit(() -> post(email, "correct-password", bootstrap()).getResponse().getStatus());
            awaitLockWaiters(2);
            release.countDown();
            assertThat(fifthFailure.get(10, TimeUnit.SECONDS)).isEqualTo(401);
            assertThat(correctPassword.get(10, TimeUnit.SECONDS)).isEqualTo(429);
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void cleanupRacingAnExpiredFirstAttemptDoesNotDeleteTheNewActiveHistory() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        byte[] key = identities.derive(email);
        jdbc.update(
                "insert into login_attempt_state(identity_key, failure_times, expires_at) values (?, '{}'::timestamptz[], clock_timestamp() - interval '1 minute')",
                key);
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Future<?> holder = executor
                .submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.queryForObject(
                            "SELECT identity_key FROM login_attempt_state WHERE identity_key = ? FOR UPDATE",
                            (rs, row) -> rs.getBytes(1), key);
                    acquired.countDown();
                    await(release);
                }));
        try {
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Integer> firstAttempt = executor
                    .submit(() -> post(email, "wrong-password", bootstrap()).getResponse().getStatus());
            awaitLockWaiters(1);
            cleanup.removeExpiredBatch(); // SKIP LOCKED must leave the row for the login transaction.
            assertThat(jdbc.queryForObject("select count(*) from login_attempt_state where identity_key=?",
                    Integer.class, key)).isEqualTo(1);
            release.countDown();
            assertThat(firstAttempt.get(10, TimeUnit.SECONDS)).isEqualTo(401);
            cleanup.removeExpiredBatch();
            assertThat(jdbc.queryForObject(
                    "select cardinality(failure_times) from login_attempt_state where identity_key=?", Integer.class,
                    key)).isEqualTo(1);
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    private void awaitLockWaiters(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event_type='Lock' and query ilike '%login_attempt_state%' and pid <> pg_backend_pid()",
                    Integer.class);
            if (waiting != null && waiting >= expected)
                return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("Timed out waiting for login-attempt row lock requests: " + expected);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out waiting for test release.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test interrupted.", interrupted);
        }
    }

    private SessionToken bootstrap() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return new SessionToken((MockHttpSession) result.getRequest().getSession(false), token);
    }

    private MvcResult post(String email, String password, SessionToken client) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/login").session(client.session()).header("X-CSRF-TOKEN", client.token())
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }
}
