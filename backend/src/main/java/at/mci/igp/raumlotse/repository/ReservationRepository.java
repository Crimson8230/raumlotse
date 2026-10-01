package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByRoomIdOrderByStartTimeAsc(UUID roomId);

    List<Reservation> findByRoomIdAndEndTimeGreaterThanEqualOrderByStartTimeAsc(UUID roomId, Instant from);

    List<Reservation> findByRoomIdAndStartTimeLessThanEqualOrderByStartTimeAsc(UUID roomId, Instant to);

    List<Reservation> findByRoomIdAndEndTimeGreaterThanEqualAndStartTimeLessThanEqualOrderByStartTimeAsc(UUID roomId, Instant from, Instant to);

    @Query("""
        select r from Reservation r
        where r.room.id = :roomId
          and r.status in (at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED, at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE)
          and r.startTime < :endTime
          and r.endTime > :startTime
    """)
    List<Reservation> findConflictingReservations(
            @Param("roomId") UUID roomId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    boolean existsByRoomId(UUID roomId);

    boolean existsByRoomIdAndStatusIn(UUID roomId, Collection<ReservationStatus> statuses);

    @Query("""
        select r from Reservation r where r.room.id = :roomId
        and r.createdBy = :userId and r.status = at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE
        and r.startTime <= :now and :now < r.endTime
    """)
    List<Reservation> findEligibleDeviceReservations(@Param("roomId") UUID roomId, @Param("userId") String userId, @Param("now") Instant now);
    
    @Query("""
        select r from Reservation r
        where r.status = :status
          and (r.startTime < :graceCutoff or r.endTime <= :now)
    """)
    List<Reservation> findUnattendedReservationsForExpiration(
            @Param("status") ReservationStatus status,
            @Param("graceCutoff") Instant graceCutoff,
            @Param("now") Instant now);

    /**
     * Rooms blocked in the window by the same rule as {@link #findConflictingReservations}: RESERVED or ACTIVE
     * reservations overlapping the half-open interval [from, to) (feature 008, FR-014).
     */
    @Query("""
        select distinct r.room.id from Reservation r
        where r.status in (at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED, at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE)
          and r.startTime < :to
          and r.endTime > :from
    """)
    Set<UUID> findOccupiedRoomIds(@Param("from") Instant from, @Param("to") Instant to);

    List<Reservation> findByStatusAndEndTimeLessThanEqual(ReservationStatus status, Instant endTime);

    List<Reservation> findTop10ByCreatedByAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc(
            String createdBy, Collection<ReservationStatus> statuses, Instant now);
}
