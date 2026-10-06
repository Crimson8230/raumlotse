package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.config.BookingConfirmationProperties;
import at.mci.igp.raumlotse.domain.BookingConfirmation;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.BookingConfirmationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingConfirmationQueueService {
    private final BookingConfirmationRepository repository;
    private final Clock clock;
    private final BookingConfirmationProperties properties;

    public BookingConfirmationQueueService(
            BookingConfirmationRepository repository, Clock clock, BookingConfirmationProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public BookingConfirmation enqueue(Reservation reservation) {
        if (reservation == null || reservation.getId() == null) {
            throw new IllegalArgumentException("A persisted reservation is required.");
        }
        if (repository.findByReservationId(reservation.getId()).isPresent()) {
            throw new IllegalStateException("A confirmation already exists for this reservation.");
        }
        return repository.save(BookingConfirmation.pending(reservation, clock.instant()));
    }

    @Transactional
    public List<UUID> claimPending() {
        var confirmations = repository.findOldestPendingForUpdate(properties.batchSize());
        Instant now = clock.instant();
        confirmations.forEach(confirmation -> confirmation.markProcessing(now));
        return confirmations.stream().map(BookingConfirmation::getId).toList();
    }

    @Transactional(readOnly = true)
    public DeliveryDetails deliveryDetails(UUID confirmationId) {
        var confirmation = repository.findForDelivery(confirmationId)
                .orElseThrow(() -> new NotFoundException("Confirmation nicht gefunden."));
        var reservation = confirmation.getReservation();
        return new DeliveryDetails(confirmation.getId(), reservation.getId(), reservation.getCreatedByUserId(),
                reservation.getRoom().getName(), reservation.getStartTime(), reservation.getEndTime());
    }

    @Transactional
    public void markSent(UUID confirmationId) {
        var confirmation = repository.findById(confirmationId)
                .orElseThrow(() -> new NotFoundException("Confirmation nicht gefunden."));
        confirmation.markSent(clock.instant());
    }

    @Transactional
    public void markFailed(UUID confirmationId, String failureCode) {
        var confirmation = repository.findById(confirmationId)
                .orElseThrow(() -> new NotFoundException("Confirmation nicht gefunden."));
        confirmation.markFailed(failureCode, clock.instant());
    }

    @Transactional
    public int recoverStale() {
        Instant threshold = clock.instant().minus(properties.staleThreshold());
        var stale = repository.findStaleProcessingForUpdate(threshold, properties.batchSize());
        Instant now = clock.instant();
        stale.forEach(confirmation -> confirmation.markFailed(BookingConfirmation.WORKER_INTERRUPTED, now));
        return stale.size();
    }

    public record DeliveryDetails(
            UUID confirmationId, UUID reservationId, UUID userId, String roomName, Instant startTime, Instant endTime) {}
}
