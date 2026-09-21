package at.mci.igp.raumlotse.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class LoginAttemptCleanupService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptCleanupService.class);
    private final JdbcTemplate jdbc;

    public LoginAttemptCleanupService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(initialDelay = 0, fixedDelay = 300_000)
    @Transactional(timeout = 10)
    public void removeExpiredBatch() {
        try {
            jdbc.execute("SET LOCAL statement_timeout = '10s'");
            int deleted = jdbc.update("""
                    WITH candidates AS (
                        SELECT identity_key FROM login_attempt_state
                        WHERE expires_at <= clock_timestamp()
                        ORDER BY expires_at
                        FOR UPDATE SKIP LOCKED
                        LIMIT 500
                    )
                    DELETE FROM login_attempt_state AS state USING candidates
                    WHERE state.identity_key = candidates.identity_key
                      AND state.expires_at <= clock_timestamp()
                    """);
            if (deleted > 0) log.info("authentication cleanup deleted_rows={}", deleted);
        } catch (RuntimeException failure) {
            log.warn("authentication cleanup outcome=unavailable");
            throw failure;
        }
    }
}
