package at.mci.igp.raumlotse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MailConfigurationPrivacyTest {
    @Test
    void acceptsTypedBoundedConfiguration() {
        var properties = properties(Duration.ofSeconds(1), Duration.ofSeconds(20));

        assertThat(properties.zone()).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(properties.pollDelay()).isLessThanOrEqualTo(Duration.ofSeconds(1));
        assertThat(properties.staleThreshold()).isGreaterThan(properties.connectionTimeout());
        assertThat(properties.staleThreshold()).isGreaterThan(properties.readTimeout());
        assertThat(properties.staleThreshold()).isGreaterThan(properties.writeTimeout());
    }

    @Test
    void rejectsSlowPollingWrongZoneAndUnsafeStaleThreshold() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(Duration.ofMillis(1001), Duration.ofSeconds(20)));
        assertThatIllegalArgumentException().isThrownBy(() -> new BookingConfirmationProperties(
                "noreply@example.test", ZoneId.of("UTC"), Duration.ofSeconds(1), 20,
                Duration.ofSeconds(20), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5)));
        assertThatIllegalArgumentException().isThrownBy(() -> properties(Duration.ofSeconds(1), Duration.ofSeconds(5)));
    }

    @Test
    void trackedConfigurationUsesEnvironmentPlaceholdersAndContainsNoCredentials() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));
        assertThat(yaml).contains("${MAIL_FROM:", "${MAIL_HOST:", "${MAIL_USERNAME:", "${MAIL_PASSWORD:");
        assertThat(yaml).doesNotContain("password: secret", "password: admin", "recipient_email", "mail.body");
    }

    private static BookingConfirmationProperties properties(Duration poll, Duration stale) {
        return new BookingConfirmationProperties(
                "noreply@example.test", ZoneId.of("Europe/Berlin"), poll, 20, stale,
                Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
