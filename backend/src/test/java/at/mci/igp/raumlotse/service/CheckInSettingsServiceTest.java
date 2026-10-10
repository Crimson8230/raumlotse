package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.CheckInSettings;
import at.mci.igp.raumlotse.dto.CheckInSettingsResponse;
import at.mci.igp.raumlotse.dto.CheckInSettingsUpdateRequest;
import at.mci.igp.raumlotse.repository.CheckInSettingsRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class CheckInSettingsServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");

    @Mock CheckInSettingsRepository repository;

    private CheckInSettingsService service;
    private CheckInSettings stored;

    @BeforeEach
    void setUp() {
        stored = new CheckInSettings(10, 5);
        when(repository.findById(CheckInSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));
        service = new CheckInSettingsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void currentPolicyComesFromTheStoredSettings() {
        stored.change(0, 15, UUID.randomUUID(), NOW);

        CheckInPolicy policy = service.current();

        assertThat(policy.earlyCheckIn()).isEqualTo(Duration.ZERO);
        assertThat(policy.gracePeriod()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void updateStoresBothValuesWithAuthorAndTimeAndLogsTheChange() {
        when(repository.save(any(CheckInSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID admin = UUID.randomUUID();

        Logger logger = (Logger) LoggerFactory.getLogger(CheckInSettingsService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        CheckInSettingsResponse response;
        try {
            response = service.update(new CheckInSettingsUpdateRequest(20, 8), admin);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(response).isEqualTo(new CheckInSettingsResponse(20, 8, NOW));
        assertThat(stored.getUpdatedByUserId()).isEqualTo(admin);
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .contains("check_in_settings_changed earlyCheckInMinutes=20 gracePeriodMinutes=8 by=" + admin);
    }

    @Test
    void readReturnsTheContractShape() {
        assertThat(service.read()).isEqualTo(new CheckInSettingsResponse(10, 5, stored.getUpdatedAt()));
    }
}
