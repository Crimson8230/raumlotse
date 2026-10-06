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
import java.util.List;
import java.util.UUID;
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

    /** {@code groundFloor} null means false (feature 008). */
    public Floor create(UUID buildingId, String name, Boolean groundFloor) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new NotFoundException("Gebäude " + buildingId + " nicht gefunden."));
        if (building.getStatus() != EntityStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Gebäude '" + building.getName() + "' ist nicht aktiv; ein Stockwerk kann nur unter einem aktiven Gebäude angelegt werden.");
        }
        requireUniqueName(buildingId, name, null);
        Floor floor = new Floor(building, name);
        floor.setGroundFloor(Boolean.TRUE.equals(groundFloor));
        return floorRepository.save(floor);
    }

    @Transactional(readOnly = true)
    public List<Floor> listByBuilding(UUID buildingId, EntityStatus statusFilter) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new NotFoundException("Gebäude " + buildingId + " nicht gefunden.");
        }
        List<Floor> floors = floorRepository.findByBuildingId(buildingId);
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

    /** Renames the floor; {@code groundFloor} null keeps the current value (feature 008). */
    public Floor update(UUID id, String name, Boolean groundFloor) {
        Floor floor = findOrThrow(id);
        requireUniqueName(floor.getBuilding().getId(), name, id);
        floor.setName(name);
        if (groundFloor != null) {
            floor.setGroundFloor(groundFloor);
        }
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
        floorRepository.delete(floor);
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
