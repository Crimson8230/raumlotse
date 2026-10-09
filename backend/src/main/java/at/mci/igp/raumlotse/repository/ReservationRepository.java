package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
        and r.createdByUserId = :userId and r.status = at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE
        and coalesce(r.checkedInAt, r.startTime) <= :now and :now < r.endTime
    """)
    List<Reservation> findEligibleDeviceReservations(@Param("roomId") UUID roomId, @Param("userId") UUID userId, @Param("now") Instant now);
    
    /** Today's reservations of a room that an on-site check-in can classify (feature 014). */
    @Query("""
        select r from Reservation r
        where r.room.id = :roomId
          and r.status in (at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED,
                           at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE,
                           at.mci.igp.raumlotse.domain.ReservationStatus.EXPIRED)
          and r.startTime >= :dayStart and r.startTime < :dayEnd
        order by r.startTime asc
    """)
    List<Reservation> findCheckInCandidates(@Param("roomId") UUID roomId, @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    /** The ACTIVE reservation of a room in use now: from its check-in (or start) until its end (feature 014). */
    @Query("""
        select r from Reservation r
        where r.room.id = :roomId
          and r.status = at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE
          and coalesce(r.checkedInAt, r.startTime) <= :now and :now < r.endTime
    """)
    Optional<Reservation> findActiveCovering(@Param("roomId") UUID roomId, @Param("now") Instant now);

    /**
     * Reservations of a room ongoing now: RESERVED ones within [startTime, endTime), ACTIVE ones from their check-in
     * (which may be up to 10 minutes before the start) until endTime (feature 014).
     */
    @Query("""
        select r from Reservation r
        where r.room.id = :roomId
          and :now < r.endTime
          and ((r.status = at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED and r.startTime <= :now)
            or (r.status = at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE
                and coalesce(r.checkedInAt, r.startTime) <= :now))
    """)
    List<Reservation> findCovering(@Param("roomId") UUID roomId, @Param("now") Instant now);

    boolean existsByRoomIdAndStatusAndIdNot(UUID roomId, ReservationStatus status, UUID id);

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

    List<Reservation> findTop10ByCreatedByUserIdAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc(
            UUID createdByUserId, Collection<ReservationStatus> statuses, Instant now);
}
