package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.BookingConfirmation;
import at.mci.igp.raumlotse.domain.BookingConfirmationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingConfirmationRepository extends JpaRepository<BookingConfirmation, UUID> {
    Optional<BookingConfirmation> findByReservationId(UUID reservationId);

    @Query("""
            SELECT confirmation FROM BookingConfirmation confirmation
            JOIN FETCH confirmation.reservation reservation
            JOIN FETCH reservation.room
            WHERE confirmation.id = :id
            """)
    Optional<BookingConfirmation> findForDelivery(@Param("id") UUID id);

    List<BookingConfirmation> findByStatus(BookingConfirmationStatus status);

    @Query(value = """
            SELECT * FROM booking_confirmation
            WHERE status = 'PENDING'
            ORDER BY created_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BookingConfirmation> findOldestPendingForUpdate(@Param("batchSize") int batchSize);

    @Query(value = """
            SELECT * FROM booking_confirmation
            WHERE status = 'PROCESSING' AND processing_started_at < :before
            ORDER BY processing_started_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<BookingConfirmation> findStaleProcessingForUpdate(
            @Param("before") Instant before, @Param("batchSize") int batchSize);
}
