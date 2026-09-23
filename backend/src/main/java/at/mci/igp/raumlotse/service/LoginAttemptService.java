package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.LoginAttemptStateRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    public enum Outcome {
        SUCCESS, INVALID_CREDENTIALS, COOLDOWN
    }

    public record Attempt(Outcome outcome, UserAccount account, int retryAfterSeconds) {
    }

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration COOLDOWN = Duration.ofMinutes(15);

    private final AccountAuthenticationService authentication;
    private final LoginAttemptStateRepository states;
    private final LoginAttemptIdentity identity;
    private final EmailCanonicalizer canonicalizer;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public LoginAttemptService(AccountAuthenticationService authentication, LoginAttemptStateRepository states,
            LoginAttemptIdentity identity, EmailCanonicalizer canonicalizer, JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.authentication = authentication;
        this.states = states;
        this.identity = identity;
        this.canonicalizer = canonicalizer;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transaction.setTimeout(10);
    }

    public Attempt authenticate(String email, String password) {
        return authenticate(email, password, account -> {
        });
    }

    public Attempt authenticate(String email, String password,
            java.util.function.Consumer<UserAccount> prepareSession) {
        String canonicalEmail = canonicalizer.canonicalize(email);
        byte[] key = identity.derive(canonicalEmail);
        try {
            Attempt result = transaction.execute(status -> {
                Attempt attempt = evaluate(key, canonicalEmail, password);
                if (attempt.outcome() == Outcome.SUCCESS)
                    prepareSession.accept(attempt.account());
                return attempt;
            });
            log.info("authentication outcome={}", result.outcome().name().toLowerCase(java.util.Locale.ROOT));
            return result;
        } catch (RuntimeException ex) {
            log.warn("authentication outcome=unavailable");
            throw ex;
        }
    }

    private Attempt evaluate(byte[] key, String email, String password) {
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SET LOCAL statement_timeout = '10s'");
        var state = states.lock(key);
        Instant now = state.databaseNow();
        if (state.blockedUntil() != null && isBlocked(now, state.blockedUntil())) {
            return new Attempt(Outcome.COOLDOWN, null, secondsRemaining(now, state.blockedUntil()));
        }

        var failures = new ArrayList<>(activeFailures(state.failures(), now));
        if (state.blockedUntil() != null)
            failures.clear();

        Optional<UserAccount> account = authentication.authenticate(email, password);
        Instant completedAt = jdbc.queryForObject("SELECT clock_timestamp()",
                (rs, row) -> rs.getTimestamp(1).toInstant());
        if (account.isPresent()) {
            states.save(key, java.util.List.of(), null, completedAt);
            return new Attempt(Outcome.SUCCESS, account.get(), 0);
        }

        failures = new ArrayList<>(activeFailures(failures, completedAt));
        failures.add(completedAt);
        while (failures.size() > 5)
            failures.remove(0);
        Instant blockedUntil = failures.size() == 5 ? completedAt.plus(COOLDOWN) : null;
        Instant expiresAt = blockedUntil != null ? blockedUntil : completedAt.plus(WINDOW);
        states.save(key, failures, blockedUntil, expiresAt);
        return new Attempt(Outcome.INVALID_CREDENTIALS, null, blockedUntil == null ? 0 : 900);
    }

    static int secondsRemaining(Instant now, Instant deadline) {
        long millis = Math.max(0, Duration.between(now, deadline).toMillis());
        return (int) Math.max(1, Math.min(900, (millis + 999) / 1000));
    }

    static java.util.List<Instant> activeFailures(java.util.List<Instant> failures, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        return failures.stream().filter(time -> time.isAfter(cutoff) && !time.isAfter(now)).toList();
    }

    static boolean isBlocked(Instant now, Instant deadline) {
        return deadline != null && now.isBefore(deadline);
    }
}
