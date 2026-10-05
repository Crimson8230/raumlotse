package at.mci.igp.raumlotse.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "booking-confirmation")
public record BookingConfirmationProperties(
        @NotBlank String sender,
        @NotNull ZoneId zone,
        @NotNull Duration pollDelay,
        @Min(1) @Max(100) int batchSize,
        @NotNull Duration staleThreshold,
        @NotNull Duration connectionTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration writeTimeout) {

    public BookingConfirmationProperties {
        if (!ZoneId.of("Europe/Berlin").equals(zone)) {
            throw new IllegalArgumentException("Booking confirmation zone must be Europe/Berlin.");
        }
        requirePositive(pollDelay, "Poll delay");
        if (pollDelay.compareTo(Duration.ofSeconds(1)) > 0) {
            throw new IllegalArgumentException("Poll delay must not exceed one second.");
        }
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Batch size must be between 1 and 100.");
        }
        requirePositive(connectionTimeout, "Connection timeout");
        requirePositive(readTimeout, "Read timeout");
        requirePositive(writeTimeout, "Write timeout");
        if (staleThreshold == null
                || staleThreshold.compareTo(connectionTimeout) <= 0
                || staleThreshold.compareTo(readTimeout) <= 0
                || staleThreshold.compareTo(writeTimeout) <= 0) {
            throw new IllegalArgumentException("Stale threshold must exceed all SMTP timeouts.");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }
}
