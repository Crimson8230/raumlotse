package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "booking_confirmation")
public class BookingConfirmation {
    public static final String RECIPIENT_UNAVAILABLE = "RECIPIENT_UNAVAILABLE";
    public static final String MESSAGE_FORMAT_FAILED = "MESSAGE_FORMAT_FAILED";
    public static final String SMTP_REJECTED = "SMTP_REJECTED";
    public static final String WORKER_INTERRUPTED = "WORKER_INTERRUPTED";
    private static final Set<String> FAILURE_CODES = Set.of(
            RECIPIENT_UNAVAILABLE, MESSAGE_FORMAT_FAILED, SMTP_REJECTED, WORKER_INTERRUPTED);

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true, updatable = false)
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingConfirmationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Version
    @Column(nullable = false)
    private long version;

    protected BookingConfirmation() {
    }

    private BookingConfirmation(Reservation reservation, Instant createdAt) {
        if (reservation == null || createdAt == null) {
            throw new IllegalArgumentException("Reservation and creation time are required.");
        }
        this.reservation = reservation;
        this.createdAt = createdAt;
        this.status = BookingConfirmationStatus.PENDING;
    }

    public static BookingConfirmation pending(Reservation reservation, Instant createdAt) {
        return new BookingConfirmation(reservation, createdAt);
    }

    public void markProcessing(Instant at) {
        requireStatus(BookingConfirmationStatus.PENDING);
        requireNotBeforeCreation(at);
        status = BookingConfirmationStatus.PROCESSING;
        attemptCount = 1;
        processingStartedAt = at;
    }

    public void markSent(Instant at) {
        requireStatus(BookingConfirmationStatus.PROCESSING);
        requireNotBeforeProcessing(at);
        status = BookingConfirmationStatus.SENT;
        sentAt = at;
    }

    public void markFailed(String code, Instant at) {
        requireStatus(BookingConfirmationStatus.PROCESSING);
        if (!FAILURE_CODES.contains(code)) {
            throw new IllegalArgumentException("Unsupported failure code.");
        }
        requireNotBeforeProcessing(at);
        status = BookingConfirmationStatus.FAILED;
        failureCode = code;
        failedAt = at;
    }

    private void requireStatus(BookingConfirmationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Invalid confirmation state transition.");
        }
    }

    private void requireNotBeforeCreation(Instant at) {
        if (at == null || at.isBefore(createdAt)) {
            throw new IllegalArgumentException("Transition timestamp precedes creation.");
        }
    }

    private void requireNotBeforeProcessing(Instant at) {
        if (at == null || processingStartedAt == null || at.isBefore(processingStartedAt)) {
            throw new IllegalArgumentException("Terminal timestamp precedes processing.");
        }
    }

    public UUID getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public BookingConfirmationStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getFailedAt() { return failedAt; }
    public String getFailureCode() { return failureCode; }
    public long getVersion() { return version; }
}
