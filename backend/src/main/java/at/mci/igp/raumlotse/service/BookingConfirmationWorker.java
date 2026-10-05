package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.domain.BookingConfirmation;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "booking-confirmation.worker-enabled", havingValue = "true", matchIfMissing = true)
public class BookingConfirmationWorker {
    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationWorker.class);
    private final BookingConfirmationQueueService queue;
    private final UserAccountRepository users;
    private final BookingConfirmationMailGateway gateway;

    public BookingConfirmationWorker(
            BookingConfirmationQueueService queue,
            UserAccountRepository users,
            BookingConfirmationMailGateway gateway) {
        this.queue = queue;
        this.users = users;
        this.gateway = gateway;
    }

    @Scheduled(fixedDelayString = "${booking-confirmation.poll-delay:1s}")
    public void processPending() {
        try {
            int recovered = queue.recoverStale();
            if (recovered > 0) {
                log.warn("BOOKING_CONFIRMATION_STALE_RECOVERED count={}", recovered);
            }
        } catch (RuntimeException exception) {
            log.error("BOOKING_CONFIRMATION_STALE_RECOVERY_FAILED");
        }
        for (var confirmationId : queue.claimPending()) {
            process(confirmationId);
        }
    }

    private void process(java.util.UUID confirmationId) {
        BookingConfirmationQueueService.DeliveryDetails details;
        try {
            details = queue.deliveryDetails(confirmationId);
        } catch (RuntimeException exception) {
            fail(confirmationId, BookingConfirmation.MESSAGE_FORMAT_FAILED);
            return;
        }

        if (details.userId() == null) {
            fail(confirmationId, BookingConfirmation.RECIPIENT_UNAVAILABLE);
            return;
        }
        var account = users.findById(details.userId()).orElse(null);
        if (account == null || !isUsableEmail(account.getEmail())) {
            fail(confirmationId, BookingConfirmation.RECIPIENT_UNAVAILABLE);
            return;
        }

        try {
            gateway.send(new BookingConfirmationMailGateway.Command(
                    account.getEmail(), details.roomName(), details.startTime(), details.endTime()));
        } catch (BookingConfirmationMailGateway.MessageFormatException | IllegalArgumentException exception) {
            fail(confirmationId, BookingConfirmation.MESSAGE_FORMAT_FAILED);
            return;
        } catch (BookingConfirmationMailGateway.SubmissionException exception) {
            fail(confirmationId, BookingConfirmation.SMTP_REJECTED);
            return;
        }

        try {
            queue.markSent(confirmationId);
            log.info("BOOKING_CONFIRMATION_SENT confirmationId={} reservationId={}",
                    confirmationId, details.reservationId());
        } catch (RuntimeException exception) {
            log.error("FINALIZATION_FAILED confirmationId={} reservationId={}",
                    confirmationId, details.reservationId());
        }
    }

    private void fail(java.util.UUID confirmationId, String failureCode) {
        try {
            queue.markFailed(confirmationId, failureCode);
            log.warn("BOOKING_CONFIRMATION_FAILED confirmationId={} failureCode={}", confirmationId, failureCode);
        } catch (RuntimeException exception) {
            log.error("FINALIZATION_FAILED confirmationId={}", confirmationId);
        }
    }

    private boolean isUsableEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        try {
            var address = new InternetAddress(email, true);
            address.validate();
            return true;
        } catch (AddressException exception) {
            return false;
        }
    }
}
