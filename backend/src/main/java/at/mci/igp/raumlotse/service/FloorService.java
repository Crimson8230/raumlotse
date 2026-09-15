package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.BuildingRepository;
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

    public FloorService(FloorRepository floorRepository, BuildingRepository buildingRepository, RoomRepository roomRepository) {
        this.floorRepository = floorRepository;
        this.buildingRepository = buildingRepository;
        this.roomRepository = roomRepository;
    }

    public Floor create(UUID buildingId, String name) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new NotFoundException("Building " + buildingId + " not found."));
        if (building.getStatus() != EntityStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Building '" + building.getName() + "' is not active; a floor can only be created under an active building.");
        }
        requireUniqueName(buildingId, name, null);
        return floorRepository.save(new Floor(building, name));
    }

    @Transactional(readOnly = true)
    public List<Floor> listByBuilding(UUID buildingId, EntityStatus statusFilter) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new NotFoundException("Building " + buildingId + " not found.");
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
                    "Building '" + floor.getBuilding().getName() + "' is still deactivated; reactivate it first.");
        }
        floor.setStatus(EntityStatus.ACTIVE);
        return floor;
    }

    public void delete(UUID id) {
        Floor floor = findOrThrow(id);
        if (roomRepository.existsByFloorId(id)) {
            throw new ConflictException(
                    "Floor '" + floor.getName() + "' is referenced by one or more rooms; deactivate it instead.");
        }
        floorRepository.delete(floor);
    }

    private void requireUniqueName(UUID buildingId, String name, UUID excludingId) {
        boolean exists = excludingId == null
                ? floorRepository.existsByBuildingIdAndNameIgnoreCase(buildingId, name)
                : floorRepository.existsByBuildingIdAndNameIgnoreCaseAndIdNot(buildingId, name, excludingId);
        if (exists) {
            throw new ConflictException("A floor named '" + name + "' already exists in this building.");
        }
    }

    private Floor findOrThrow(UUID id) {
        return floorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Floor " + id + " not found."));
    }
}
