package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.LoginAttemptStateRepository;
import at.mci.igp.raumlotse.repository.LoginAttemptStateRepository.LockedState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class LoginAttemptResetTest {
        @Test
        void failureThatExpiresDuringVerificationDoesNotTriggerCooldown() {
                Instant now = Instant.parse("2026-09-20T12:00:00Z");
                var authentication = mock(AccountAuthenticationService.class);
                var states = mock(LoginAttemptStateRepository.class);
                when(states.lock(any(byte[].class))).thenReturn(new LockedState(
                                List.of(now.minusSeconds(899), now.minusSeconds(120), now.minusSeconds(60),
                                                now.minusSeconds(30)),
                                null, now));
                when(authentication.authenticate("user@example.test", "wrong")).thenReturn(Optional.empty());
                var jdbc = mock(JdbcTemplate.class);
                when(jdbc.queryForObject(eq("SELECT clock_timestamp()"), any(RowMapper.class)))
                                .thenReturn(now.plusSeconds(2));
                var result = service(authentication, states, jdbc).authenticate("user@example.test", "wrong");
                assertThat(result.retryAfterSeconds()).isZero();
                verify(states).save(any(byte[].class), eq(List.of(now.minusSeconds(120), now.minusSeconds(60),
                                now.minusSeconds(30), now.plusSeconds(2))), eq(null), eq(now.plusSeconds(902)));
        }

        @Test
        void successfulAuthenticationClearsAllPreviousFailures() {
                Instant now = Instant.parse("2026-09-20T12:00:00Z");
                var authentication = mock(AccountAuthenticationService.class);
                var states = mock(LoginAttemptStateRepository.class);
                when(states.lock(any(byte[].class))).thenReturn(new LockedState(
                                List.of(now.minusSeconds(120), now.minusSeconds(60)), null, now));
                when(authentication.authenticate("user@example.test", "correct-password"))
                                .thenReturn(Optional.of(mock(UserAccount.class)));
                var jdbc = mock(JdbcTemplate.class);
                when(jdbc.queryForObject(eq("SELECT clock_timestamp()"), any(RowMapper.class)))
                                .thenReturn(now.plusMillis(5));
                var service = service(authentication, states, jdbc);

                var result = service.authenticate("user@example.test", "correct-password");

                assertThat(result.outcome()).isEqualTo(LoginAttemptService.Outcome.SUCCESS);
                verify(states).save(any(byte[].class), eq(List.of()), eq(null), eq(now.plusMillis(5)));
        }

        @Test
        void expiredCooldownHistoryIsReplacedByOnlyTheNewFailure() {
                Instant now = Instant.parse("2026-09-20T12:00:00Z");
                var authentication = mock(AccountAuthenticationService.class);
                var states = mock(LoginAttemptStateRepository.class);
                when(states.lock(any(byte[].class))).thenReturn(new LockedState(
                                List.of(now.minusSeconds(1200), now.minusSeconds(1140), now.minusSeconds(1080),
                                                now.minusSeconds(1020), now.minusSeconds(960)),
                                now.minusSeconds(1), now));
                when(authentication.authenticate("user@example.test", "wrong-password")).thenReturn(Optional.empty());
                var jdbc = mock(JdbcTemplate.class);
                when(jdbc.queryForObject(eq("SELECT clock_timestamp()"), any(RowMapper.class)))
                                .thenReturn(now.plusMillis(5));
                var service = service(authentication, states, jdbc);

                var result = service.authenticate("user@example.test", "wrong-password");

                assertThat(result.outcome()).isEqualTo(LoginAttemptService.Outcome.INVALID_CREDENTIALS);
                var failures = org.mockito.ArgumentCaptor.forClass(List.class);
                verify(states).save(any(byte[].class), failures.capture(), eq(null), eq(now.plusMillis(900_005)));
                assertThat(failures.getValue()).containsExactly(now.plusMillis(5));
        }

        private LoginAttemptService service(AccountAuthenticationService authentication,
                        LoginAttemptStateRepository states, JdbcTemplate jdbc) {
                var manager = mock(PlatformTransactionManager.class);
                when(manager.getTransaction(any(TransactionDefinition.class)))
                                .thenReturn(new SimpleTransactionStatus());
                return new LoginAttemptService(authentication, states, new LoginAttemptIdentity(new byte[32]),
                                new EmailCanonicalizer(), jdbc, manager);
        }
}
