package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries for the occupancy overview (feature 016). The covering rule is the one of
 * {@link ReservationRepository#findCovering}: RESERVED from its start, ACTIVE from its check-in, both until the end.
 */
@Repository
public class OccupancyRepository {
    private static final String SELECT_BOOKINGS = """
            select r from Reservation r
            join fetch r.room ro
            join fetch ro.floor f
            join fetch f.building b
            """;
    private static final String EFFECTIVE_START = """
            case when r.status = at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE
                 then coalesce(r.checkedInAt, r.startTime) else r.startTime end""";

    @PersistenceContext
    private EntityManager entityManager;

    public List<BookingRow> findCovering(Instant now, UUID buildingIdOrNull) {
        String jpql = SELECT_BOOKINGS + """
                where :now < r.endTime
                  and ((r.status = at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED and r.startTime <= :now)
                    or (r.status = at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE
                        and coalesce(r.checkedInAt, r.startTime) <= :now))
                """ + buildingFilter(buildingIdOrNull);
        TypedQuery<Reservation> query = entityManager.createQuery(jpql, Reservation.class).setParameter("now", now);
        return rows(withBuilding(query, buildingIdOrNull));
    }

    public List<BookingRow> findOverlapping(Instant from, Instant to, UUID buildingIdOrNull) {
        String jpql = SELECT_BOOKINGS + """
                where r.status in (at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED,
                                   at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE)
                  and r.endTime > :from
                """ + "  and " + EFFECTIVE_START + " < :to\n" + buildingFilter(buildingIdOrNull);
        TypedQuery<Reservation> query = entityManager.createQuery(jpql, Reservation.class)
                .setParameter("from", from)
                .setParameter("to", to);
        return rows(withBuilding(query, buildingIdOrNull));
    }

    public List<BuildingRow> findBuildings() {
        return entityManager.createQuery("select b from Building b order by b.name", Building.class)
                .getResultList().stream()
                .map(building -> new BuildingRow(building.getId(), building.getName(), building.getStatus()))
                .toList();
    }

    public boolean buildingExists(UUID id) {
        return entityManager.find(Building.class, id) != null;
    }

    private static String buildingFilter(UUID buildingIdOrNull) {
        return buildingIdOrNull == null ? "" : "  and b.id = :buildingId\n";
    }

    private static TypedQuery<Reservation> withBuilding(TypedQuery<Reservation> query, UUID buildingIdOrNull) {
        return buildingIdOrNull == null ? query : query.setParameter("buildingId", buildingIdOrNull);
    }

    private static List<BookingRow> rows(TypedQuery<Reservation> query) {
        return query.getResultList().stream().map(OccupancyRepository::row).toList();
    }

    private static BookingRow row(Reservation reservation) {
        var room = reservation.getRoom();
        var floor = room.getFloor();
        var building = floor.getBuilding();
        Instant effectiveStart = reservation.getStatus() == ReservationStatus.ACTIVE && reservation.getCheckedInAt() != null
                ? reservation.getCheckedInAt() : reservation.getStartTime();
        return new BookingRow(reservation.getId(), room.getId(), room.getName(), building.getId(), building.getName(),
                building.getStatus(), floor.getId(), floor.getName(), reservation.getStartTime(), reservation.getEndTime(),
                effectiveStart, reservation.getStatus(), reservation.getExpectedAttendees(), reservation.getCheckedInAt());
    }

    public record BookingRow(
            UUID reservationId,
            UUID roomId,
            String roomName,
            UUID buildingId,
            String buildingName,
            EntityStatus buildingStatus,
            UUID floorId,
            String floorName,
            Instant startTime,
            Instant endTime,
            Instant effectiveStart,
            ReservationStatus status,
            int expectedAttendees,
            Instant checkedInAt) {}

    public record BuildingRow(UUID id, String name, EntityStatus status) {}
}
