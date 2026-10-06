package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class StatisticsRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public List<RoomRow> findRooms() {
        return entityManager.createQuery(
                        "select distinct r from Room r left join fetch r.equipmentTypes order by r.name", Room.class)
                .getResultList().stream()
                .map(room -> new RoomRow(room.getId(), room.getName(), room.getStatus(),
                        room.getEquipmentTypes().stream().map(EquipmentType::getId).toList()))
                .toList();
    }

    public List<FeatureRow> findFeatures() {
        return entityManager.createQuery("select e from EquipmentType e order by e.name", EquipmentType.class)
                .getResultList().stream()
                .map(feature -> new FeatureRow(feature.getId(), feature.getName(), feature.getStatus()))
                .toList();
    }

    public List<ReservationRow> findReservations(Instant from, Instant to) {
        return entityManager.createQuery("""
                select distinct r from Reservation r
                where r.startTime < :to and r.endTime > :from
                order by r.startTime
                """, Reservation.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList().stream()
                .map(reservation -> new ReservationRow(
                        reservation.getId(), reservation.getRoom().getId(), reservation.getStartTime(),
                        reservation.getEndTime(), reservation.getStatus(), reservation.getExpectedAttendees(),
                        reservation.getRoom().getEquipmentTypes().stream().map(EquipmentType::getId).toList()))
                .toList();
    }

    public record RoomRow(UUID id, String name, EntityStatus status, List<UUID> featureIds) {}

    public record FeatureRow(UUID id, String name, EntityStatus status) {}

    public record ReservationRow(
            UUID id,
            UUID roomId,
            Instant startTime,
            Instant endTime,
            ReservationStatus status,
            int expectedAttendees,
            List<UUID> featureIds) {}
}
