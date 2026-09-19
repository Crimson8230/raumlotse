package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
}
