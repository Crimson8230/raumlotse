package at.mci.igp.raumlotse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingConfirmationTest {
    private static final Instant CREATED = Instant.parse("2026-10-05T10:00:00Z");

    @Test
    void followsTheSingleAttemptHappyPathWithConsistentTimestamps() {
        var confirmation = BookingConfirmation.pending(new Reservation(), CREATED);
        var processing = CREATED.plusSeconds(1);
        var sent = CREATED.plusSeconds(2);

        confirmation.markProcessing(processing);
        confirmation.markSent(sent);

        assertThat(confirmation.getStatus()).isEqualTo(BookingConfirmationStatus.SENT);
        assertThat(confirmation.getAttemptCount()).isEqualTo(1);
        assertThat(confirmation.getCreatedAt()).isEqualTo(CREATED);
        assertThat(confirmation.getProcessingStartedAt()).isEqualTo(processing);
        assertThat(confirmation.getSentAt()).isEqualTo(sent);
        assertThat(confirmation.getFailedAt()).isNull();
        assertThat(confirmation.getFailureCode()).isNull();
    }

    @Test
    void processingCanEndInAnAllowlistedFailure() {
        var confirmation = BookingConfirmation.pending(new Reservation(), CREATED);
        confirmation.markProcessing(CREATED.plusSeconds(1));
        confirmation.markFailed("SMTP_REJECTED", CREATED.plusSeconds(2));

        assertThat(confirmation.getStatus()).isEqualTo(BookingConfirmationStatus.FAILED);
        assertThat(confirmation.getAttemptCount()).isEqualTo(1);
        assertThat(confirmation.getFailedAt()).isEqualTo(CREATED.plusSeconds(2));
        assertThat(confirmation.getFailureCode()).isEqualTo("SMTP_REJECTED");
        assertThat(confirmation.getSentAt()).isNull();
    }

    @Test
    void terminalStatesAreImmutableAndAttemptCountCannotExceedOne() {
        var confirmation = BookingConfirmation.pending(new Reservation(), CREATED);
        confirmation.markProcessing(CREATED.plusSeconds(1));
        confirmation.markSent(CREATED.plusSeconds(2));

        assertThatIllegalStateException().isThrownBy(() -> confirmation.markProcessing(CREATED.plusSeconds(3)));
        assertThatIllegalStateException().isThrownBy(() -> confirmation.markFailed(
                "SMTP_REJECTED", CREATED.plusSeconds(3)));
    }

    @Test
    void rejectsInvalidTransitionsTimestampsAndFailureCodes() {
        var confirmation = BookingConfirmation.pending(new Reservation(), CREATED);

        assertThatIllegalStateException().isThrownBy(() -> confirmation.markSent(CREATED.plusSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> confirmation.markProcessing(CREATED.minusSeconds(1)));
        confirmation.markProcessing(CREATED.plusSeconds(1));
        assertThatIllegalArgumentException().isThrownBy(() -> confirmation.markFailed(
                "raw smtp exception text", CREATED.plusSeconds(2)));
        assertThatIllegalArgumentException().isThrownBy(() -> confirmation.markSent(CREATED));
    }
}
