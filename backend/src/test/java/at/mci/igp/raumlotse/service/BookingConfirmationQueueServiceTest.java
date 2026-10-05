package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.config.BookingConfirmationProperties;
import at.mci.igp.raumlotse.domain.BookingConfirmation;
import at.mci.igp.raumlotse.domain.BookingConfirmationStatus;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.repository.BookingConfirmationRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingConfirmationQueueServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    @Mock BookingConfirmationRepository repository;
    BookingConfirmationQueueService queue;

    @BeforeEach
    void setUp() {
        queue = new BookingConfirmationQueueService(repository, Clock.fixed(NOW, ZoneOffset.UTC), properties());
    }

    @Test
    void enqueuesOnePendingRowAndRefusesDuplicates() throws Exception {
        Reservation reservation = reservation(UUID.randomUUID());
        when(repository.findByReservationId(reservation.getId())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(queue.enqueue(reservation).getStatus()).isEqualTo(BookingConfirmationStatus.PENDING);
        verify(repository).save(any(BookingConfirmation.class));

        when(repository.findByReservationId(reservation.getId())).thenReturn(Optional.of(
                BookingConfirmation.pending(reservation, NOW)));
        assertThatIllegalStateException().isThrownBy(() -> queue.enqueue(reservation));
    }

    @Test
    void claimsOnceAndFinalizesSentAsTerminal() throws Exception {
        Reservation reservation = reservation(UUID.randomUUID());
        BookingConfirmation confirmation = BookingConfirmation.pending(reservation, NOW.minusSeconds(2));
        UUID confirmationId = UUID.randomUUID();
        setField(confirmation, "id", confirmationId);
        when(repository.findOldestPendingForUpdate(20)).thenReturn(List.of(confirmation));

        assertThat(queue.claimPending()).containsExactly(confirmationId);
        assertThat(confirmation.getStatus()).isEqualTo(BookingConfirmationStatus.PROCESSING);
        assertThat(confirmation.getAttemptCount()).isEqualTo(1);

        when(repository.findById(confirmationId)).thenReturn(Optional.of(confirmation));
        queue.markSent(confirmationId);
        assertThat(confirmation.getStatus()).isEqualTo(BookingConfirmationStatus.SENT);
        assertThatIllegalStateException().isThrownBy(() -> queue.markSent(confirmationId));
        verify(repository, never()).delete(any());
    }

    @Test
    void persistsAllowlistedTerminalFailureAndRecoversStaleWorkWithoutRetry() throws Exception {
        Reservation reservation = reservation(UUID.randomUUID());
        BookingConfirmation failed = BookingConfirmation.pending(reservation, NOW.minusSeconds(10));
        failed.markProcessing(NOW.minusSeconds(9));
        UUID failedId = UUID.randomUUID();
        setField(failed, "id", failedId);
        when(repository.findById(failedId)).thenReturn(Optional.of(failed));

        queue.markFailed(failedId, BookingConfirmation.SMTP_REJECTED);
        assertThat(failed.getStatus()).isEqualTo(BookingConfirmationStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo(BookingConfirmation.SMTP_REJECTED);
        assertThat(failed.getFailedAt()).isEqualTo(NOW);
        assertThatIllegalStateException().isThrownBy(() -> queue.markFailed(
                failedId, BookingConfirmation.SMTP_REJECTED));

        BookingConfirmation stale = BookingConfirmation.pending(reservation(UUID.randomUUID()), NOW.minusSeconds(60));
        stale.markProcessing(NOW.minusSeconds(40));
        when(repository.findStaleProcessingForUpdate(NOW.minusSeconds(30), 20)).thenReturn(List.of(stale));
        assertThat(queue.recoverStale()).isEqualTo(1);
        assertThat(stale.getFailureCode()).isEqualTo(BookingConfirmation.WORKER_INTERRUPTED);
        assertThat(stale.getAttemptCount()).isEqualTo(1);
    }

    private static Reservation reservation(UUID id) throws Exception {
        Reservation reservation = new Reservation();
        setField(reservation, "id", id);
        return reservation;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static BookingConfirmationProperties properties() {
        return new BookingConfirmationProperties("noreply@example.test", ZoneId.of("Europe/Berlin"),
                Duration.ofSeconds(1), 20, Duration.ofSeconds(30), Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
