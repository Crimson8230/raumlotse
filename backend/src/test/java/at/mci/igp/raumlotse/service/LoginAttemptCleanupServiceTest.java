package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

class LoginAttemptCleanupServiceTest {
    @Test
    void cleanupOutageLogsOnlyASafeOutcome() {
        var jdbc = mock(JdbcTemplate.class);
        var failure = new IllegalStateException("private cleanup detail");
        org.mockito.Mockito.doThrow(failure).when(jdbc).execute("SET LOCAL statement_timeout = '10s'");
        Logger logger = (Logger) LoggerFactory.getLogger(LoginAttemptCleanupService.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> new LoginAttemptCleanupService(jdbc).removeExpiredBatch())
                    .isSameAs(failure);
            String output = appender.list.stream().map(event -> event.getFormattedMessage()).reduce("", String::concat);
            org.assertj.core.api.Assertions.assertThat(output).contains("authentication cleanup outcome=unavailable")
                    .doesNotContain("private cleanup detail");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
