package at.mci.igp.raumlotse.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transient search filters (feature 008). Only the rules that need the loaded room live here; restricting to
 * fully active rooms and to one building is done by the candidate query.
 */
public record RoomSearchCriteria(
        Integer minPersons,
        Integer maxPersons,
        UUID buildingId,
        String seatingArrangement,
        Set<UUID> equipmentTypeIds,
        boolean barrierFree,
        Instant from,
        Instant to) {

    public RoomSearchCriteria {
        seatingArrangement = seatingArrangement == null || seatingArrangement.isBlank()
                ? null
                : seatingArrangement.strip();
        equipmentTypeIds = equipmentTypeIds == null ? Set.of() : Set.copyOf(equipmentTypeIds);
    }

    /** Person range and building only. */
    public RoomSearchCriteria(Integer minPersons, Integer maxPersons, UUID buildingId) {
        this(minPersons, maxPersons, buildingId, null, Set.of());
    }

    /** Without the barrier-free restriction and time window. */
    public RoomSearchCriteria(Integer minPersons, Integer maxPersons, UUID buildingId, String seatingArrangement,
            Set<UUID> equipmentTypeIds) {
        this(minPersons, maxPersons, buildingId, seatingArrangement, equipmentTypeIds, false);
    }

    /** Without a time window. */
    public RoomSearchCriteria(Integer minPersons, Integer maxPersons, UUID buildingId, String seatingArrangement,
            Set<UUID> equipmentTypeIds, boolean barrierFree) {
        this(minPersons, maxPersons, buildingId, seatingArrangement, equipmentTypeIds, barrierFree, null, null);
    }

    public boolean hasTimeWindow() {
        return from != null && to != null;
    }

    /** Match without availability; only valid for criteria without a time window. */
    public boolean matches(Room room) {
        return matches(room, Set.of());
    }

    /** {@code occupiedRoomIds}: rooms booked in [from, to), only consulted when a time window is set (FR-014). */
    public boolean matches(Room room, Set<UUID> occupiedRoomIds) {
        return matchesArrangementRule(room) && hasAllEquipment(room)
                && (!barrierFree || room.isBarrierFreeReachable())
                && (!hasTimeWindow() || !isOccupied(room, occupiedRoomIds));
    }

    /** Immutable sets reject {@code contains(null)}; an unsaved room cannot be booked, so it is free. */
    private static boolean isOccupied(Room room, Set<UUID> occupiedRoomIds) {
        return room.getId() != null && occupiedRoomIds.contains(room.getId());
    }

    /** A single seating arrangement must satisfy every given arrangement condition at once (FR-004, FR-007). */
    private boolean matchesArrangementRule(Room room) {
        if (minPersons == null && maxPersons == null && seatingArrangement == null) {
            return true;
        }
        return room.getSeatingArrangements().stream().anyMatch(arrangement ->
                (minPersons == null || arrangement.getMaxCapacity() >= minPersons)
                        && (maxPersons == null || arrangement.getMaxCapacity() <= maxPersons)
                        && (seatingArrangement == null || arrangement.getName().equalsIgnoreCase(seatingArrangement)));
    }

    /** The room must have every requested equipment type assigned (FR-008). */
    private boolean hasAllEquipment(Room room) {
        if (equipmentTypeIds.isEmpty()) {
            return true;
        }
        Set<UUID> assigned = room.getEquipmentTypes().stream().map(EquipmentType::getId).collect(Collectors.toSet());
        return assigned.containsAll(equipmentTypeIds);
    }
}
