package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.BookingConfirmation;
import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingConfirmationWorkerTest {
    @Mock BookingConfirmationQueueService queue;
    @Mock UserAccountRepository users;
    @Mock BookingConfirmationMailGateway gateway;
    BookingConfirmationWorker worker;
    UUID confirmationId;
    UUID reservationId;
    UUID userId;

    @BeforeEach
    void setUp() {
        worker = new BookingConfirmationWorker(queue, users, gateway);
        confirmationId = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        when(queue.claimPending()).thenReturn(List.of(confirmationId));
        when(queue.deliveryDetails(confirmationId)).thenReturn(new BookingConfirmationQueueService.DeliveryDetails(
                confirmationId, reservationId, userId, "Raum Ä", Instant.parse("2026-10-05T10:00:00Z"),
                Instant.parse("2026-10-05T11:00:00Z")));
    }

    @Test
    void missingRecipientBecomesTerminalFailureWithoutSubmission() {
        when(users.findById(userId)).thenReturn(Optional.empty());

        assertThatCode(worker::processPending).doesNotThrowAnyException();

        verify(queue).markFailed(confirmationId, BookingConfirmation.RECIPIENT_UNAVAILABLE);
        verify(gateway, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void smtpRejectionBecomesTerminalFailureAndIsNotRetried() {
        when(users.findById(userId)).thenReturn(Optional.of(new UserAccount(
                "booker@example.test", "Booker", "hash")));
        org.mockito.Mockito.doThrow(new BookingConfirmationMailGateway.SubmissionException(
                new RuntimeException("private smtp detail booker@example.test")))
                .when(gateway).send(org.mockito.ArgumentMatchers.any());

        assertThatCode(worker::processPending).doesNotThrowAnyException();
        verify(queue).markFailed(confirmationId, BookingConfirmation.SMTP_REJECTED);

        when(queue.claimPending()).thenReturn(List.of());
        worker.processPending();
        verify(gateway).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void messageFormattingFailureUsesFixedCode() {
        when(users.findById(userId)).thenReturn(Optional.of(new UserAccount(
                "booker@example.test", "Booker", "hash")));
        org.mockito.Mockito.doThrow(new BookingConfirmationMailGateway.MessageFormatException(
                new RuntimeException("room and body detail")))
                .when(gateway).send(org.mockito.ArgumentMatchers.any());

        assertThatCode(worker::processPending).doesNotThrowAnyException();
        verify(queue).markFailed(confirmationId, BookingConfirmation.MESSAGE_FORMAT_FAILED);
    }
}
