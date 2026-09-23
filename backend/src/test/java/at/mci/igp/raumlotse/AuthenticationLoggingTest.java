package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.repository.LoginAttemptStateRepository;
import at.mci.igp.raumlotse.service.AccountAuthenticationService;
import at.mci.igp.raumlotse.service.EmailCanonicalizer;
import at.mci.igp.raumlotse.service.LoginAttemptIdentity;
import at.mci.igp.raumlotse.service.LoginAttemptService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class AuthenticationLoggingTest {
    @Test
    void failureLogsContainOnlyAnOutcomeAndNoCredentialsOrIdentity() {
        var authentication = mock(AccountAuthenticationService.class);
        when(authentication.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        var states = mock(LoginAttemptStateRepository.class);
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        when(states.lock(any(byte[].class))).thenReturn(new LoginAttemptStateRepository.LockedState(List.of(), null, now));
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT clock_timestamp()"), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(now.plusMillis(1));
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        Logger logger = (Logger) LoggerFactory.getLogger(LoginAttemptService.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            var service = new LoginAttemptService(authentication, states,
                    new LoginAttemptIdentity(new byte[32]), new EmailCanonicalizer(), jdbc, manager);
            service.authenticate("privacy-sentinel@example.test", "secret-password-sentinel");
            String output = appender.list.stream().map(event -> event.getFormattedMessage()).reduce("", String::concat);
            assertThat(output).contains("outcome=invalid_credentials")
                    .doesNotContain("privacy-sentinel@example.test", "secret-password-sentinel");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
