package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.BuildingRepository;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FloorService {

    private final FloorRepository floorRepository;
    private final BuildingRepository buildingRepository;
    private final RoomRepository roomRepository;
    private final FloorMapRepository floorMapRepository;

    public FloorService(FloorRepository floorRepository, BuildingRepository buildingRepository,
            RoomRepository roomRepository, FloorMapRepository floorMapRepository) {
        this.floorRepository = floorRepository;
        this.buildingRepository = buildingRepository;
        this.roomRepository = roomRepository;
        this.floorMapRepository = floorMapRepository;
    }

    public Floor create(UUID buildingId, String name) {
        return create(buildingId, name, null);
    }

    /** The ground-floor flag is derived from the floor order (feature 024); {@code groundFloor} is ignored. */
    public Floor create(UUID buildingId, String name, Boolean groundFloor) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new NotFoundException("Gebäude " + buildingId + " nicht gefunden."));
        if (building.getStatus() != EntityStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Gebäude '" + building.getName() + "' ist nicht aktiv; ein Stockwerk kann nur unter einem aktiven Gebäude angelegt werden.");
        }
        requireUniqueName(buildingId, name, null);
        Floor floor = new Floor(building, name);
        int next = floorRepository.findByBuildingId(buildingId).stream().mapToInt(Floor::getPosition).max().orElse(-1) + 1;
        floor.setPosition(next);
        floor.setGroundFloor(next == 0);
        return floorRepository.save(floor);
    }

    @Transactional(readOnly = true)
    public List<Floor> listByBuilding(UUID buildingId, EntityStatus statusFilter) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new NotFoundException("Gebäude " + buildingId + " nicht gefunden.");
        }
        List<Floor> floors = ordered(floorRepository.findByBuildingId(buildingId));
        if (statusFilter == null) {
            return floors;
        }
        return floors.stream().filter(f -> f.getStatus() == statusFilter).toList();
    }

    @Transactional(readOnly = true)
    public Floor get(UUID id) {
        return findOrThrow(id);
    }

    public Floor rename(UUID id, String name) {
        return update(id, name, null);
    }

    /** Renames the floor; {@code groundFloor} is ignored, the ground floor is the first one of the order. */
    public Floor update(UUID id, String name, Boolean groundFloor) {
        Floor floor = findOrThrow(id);
        requireUniqueName(floor.getBuilding().getId(), name, id);
        floor.setName(name);
        return floor;
    }

    public Floor deactivate(UUID id) {
        Floor floor = findOrThrow(id);
        floor.setStatus(EntityStatus.DEACTIVATED);
        return floor;
    }

    /** Rejected if the parent building is still deactivated (FR-021). */
    public Floor reactivate(UUID id) {
        Floor floor = findOrThrow(id);
        if (floor.getBuilding().getStatus() == EntityStatus.DEACTIVATED) {
            throw new ConflictException(
                    "Gebäude '" + floor.getBuilding().getName() + "' ist noch deaktiviert; bitte zuerst reaktivieren.");
        }
        floor.setStatus(EntityStatus.ACTIVE);
        return floor;
    }

    public void delete(UUID id) {
        Floor floor = findOrThrow(id);
        if (roomRepository.existsByFloorId(id)) {
            throw new ConflictException(
                    "Stockwerk '" + floor.getName() + "' wird von Räumen verwendet; bitte stattdessen deaktivieren.");
        }
        if (floorMapRepository.existsByFloorId(id)) {
            throw new ConflictException(
                    "Stockwerk '" + floor.getName() + "' hat eine Karte; bitte zuerst die Karte löschen.");
        }
        UUID buildingId = floor.getBuilding().getId();
        floorRepository.delete(floor);
        applyOrder(ordered(floorRepository.findByBuildingId(buildingId)).stream()
                .filter(other -> !other.getId().equals(id)).toList());
    }

    /** Sets the order of all floors of a building: first entry is the ground floor, last the top floor. */
    public List<Floor> reorder(UUID buildingId, List<UUID> floorIds) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new NotFoundException("Gebäude " + buildingId + " nicht gefunden.");
        }
        List<Floor> current = floorRepository.findByBuildingId(buildingId);
        Map<UUID, Floor> byId = current.stream().collect(Collectors.toMap(Floor::getId, f -> f));
        if (floorIds.size() != byId.size() || new HashSet<>(floorIds).size() != floorIds.size()
                || !byId.keySet().equals(new HashSet<>(floorIds))) {
            throw new IllegalArgumentException("Die Liste muss genau die Stockwerke des Gebäudes enthalten.");
        }
        List<Floor> ordered = floorIds.stream().map(byId::get).toList();
        applyOrder(ordered);
        return ordered;
    }

    private static List<Floor> ordered(List<Floor> floors) {
        return floors.stream().sorted(Comparator.comparingInt(Floor::getPosition)
                .thenComparing(f -> f.getName().toLowerCase()).thenComparing(f -> f.getId().toString())).toList();
    }

    private void applyOrder(List<Floor> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            Floor f = ordered.get(i);
            f.setPosition(i);
            f.setGroundFloor(i == 0);
        }
    }

    private void requireUniqueName(UUID buildingId, String name, UUID excludingId) {
        boolean exists = excludingId == null
                ? floorRepository.existsByBuildingIdAndNameIgnoreCase(buildingId, name)
                : floorRepository.existsByBuildingIdAndNameIgnoreCaseAndIdNot(buildingId, name, excludingId);
        if (exists) {
            throw new ConflictException("Ein Stockwerk mit dem Namen '" + name + "' existiert in diesem Gebäude bereits.");
        }
    }

    private Floor findOrThrow(UUID id) {
        return floorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Stockwerk " + id + " nicht gefunden."));
    }
}
