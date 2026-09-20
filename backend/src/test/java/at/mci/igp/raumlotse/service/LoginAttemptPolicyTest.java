package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginAttemptPolicyTest {
    private final Instant now = Instant.parse("2026-09-20T12:00:00Z");

    @Test
    void rollingWindowExcludesFailureAtExactFifteenMinuteBoundary() {
        assertThat(LoginAttemptService.activeFailures(List.of(now.minusSeconds(900), now.minusSeconds(899)), now))
                .containsExactly(now.minusSeconds(899));
    }

    @Test
    void cooldownIncludesItsStartAndExcludesExactDeadline() {
        Instant deadline = now.plusSeconds(900);
        assertThat(LoginAttemptService.isBlocked(now, deadline)).isTrue();
        assertThat(LoginAttemptService.isBlocked(deadline, deadline)).isFalse();
        assertThat(LoginAttemptService.secondsRemaining(now.plusMillis(1), deadline)).isEqualTo(900);
    }
}
