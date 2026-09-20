package at.mci.igp.raumlotse.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoginAttemptStateRepository {
    public record LockedState(List<Instant> failures, Instant blockedUntil, Instant databaseNow) { }
    private final JdbcTemplate jdbc;

    public LoginAttemptStateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public LockedState lock(byte[] identityKey) {
        return jdbc.queryForObject("""
                INSERT INTO login_attempt_state(identity_key, failure_times, expires_at)
                VALUES (?, '{}'::timestamptz[], clock_timestamp() + interval '30 minutes')
                ON CONFLICT (identity_key) DO UPDATE SET identity_key = EXCLUDED.identity_key
                RETURNING failure_times, blocked_until, clock_timestamp()
                """, (rs, rowNum) -> {
            List<Instant> failures = new ArrayList<>();
            java.sql.Array array = rs.getArray("failure_times");
            if (array != null) {
                Object[] values = (Object[]) array.getArray();
                for (Object value : values) {
                    if (value instanceof Timestamp timestamp) failures.add(timestamp.toInstant());
                    else if (value instanceof java.time.OffsetDateTime dateTime) failures.add(dateTime.toInstant());
                }
                array.free();
            }
            Timestamp blocked = rs.getTimestamp("blocked_until");
            return new LockedState(List.copyOf(failures), blocked == null ? null : blocked.toInstant(),
                    rs.getTimestamp("clock_timestamp").toInstant());
        }, identityKey);
    }

    public void save(byte[] identityKey, List<Instant> failures, Instant blockedUntil, Instant expiresAt) {
        String[] values = failures.stream().map(Instant::toString).toArray(String[]::new);
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    UPDATE login_attempt_state
                    SET failure_times = ARRAY(SELECT unnest(?::text[])::timestamptz),
                        blocked_until = ?, expires_at = ?
                    WHERE identity_key = ?
                    """);
            statement.setArray(1, connection.createArrayOf("text", values));
            statement.setTimestamp(2, blockedUntil == null ? null : Timestamp.from(blockedUntil));
            statement.setTimestamp(3, Timestamp.from(expiresAt));
            statement.setBytes(4, identityKey);
            return statement;
        });
    }
}
