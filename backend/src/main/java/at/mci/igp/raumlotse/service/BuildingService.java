package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.BuildingRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;

    public BuildingService(BuildingRepository buildingRepository, FloorRepository floorRepository) {
        this.buildingRepository = buildingRepository;
        this.floorRepository = floorRepository;
    }

    public Building create(String name) {
        requireUniqueName(name, null);
        return buildingRepository.save(new Building(name));
    }

    @Transactional(readOnly = true)
    public List<Building> list(EntityStatus statusFilter) {
        if (statusFilter == null) {
            return buildingRepository.findAll();
        }
        return buildingRepository.findByStatus(statusFilter);
    }

    @Transactional(readOnly = true)
    public Building get(UUID id) {
        return findOrThrow(id);
    }

    public Building rename(UUID id, String name) {
        Building building = findOrThrow(id);
        requireUniqueName(name, id);
        building.setName(name);
        return building;
    }

    /**
     * Cascades to deactivate every floor under this building (FR-021) — a floor must never be
     * ACTIVE while its building is DEACTIVATED.
     */
    public Building deactivate(UUID id) {
        Building building = findOrThrow(id);
        building.setStatus(EntityStatus.DEACTIVATED);
        for (Floor floor : floorRepository.findByBuildingId(id)) {
            floor.setStatus(EntityStatus.DEACTIVATED);
        }
        return building;
    }

    /** Does NOT cascade to floors — each must be reactivated individually (FR-021). */
    public Building reactivate(UUID id) {
        Building building = findOrThrow(id);
        building.setStatus(EntityStatus.ACTIVE);
        return building;
    }

    public void delete(UUID id) {
        Building building = findOrThrow(id);
        if (floorRepository.countByBuildingId(id) > 0) {
            throw new ConflictException(
                    "Building '" + building.getName() + "' still has one or more floors; remove them first.");
        }
        buildingRepository.delete(building);
    }

    private void requireUniqueName(String name, UUID excludingId) {
        boolean exists = excludingId == null
                ? buildingRepository.existsByNameIgnoreCase(name)
                : buildingRepository.existsByNameIgnoreCaseAndIdNot(name, excludingId);
        if (exists) {
            throw new ConflictException("A building named '" + name + "' already exists.");
        }
    }

    private Building findOrThrow(UUID id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Building " + id + " not found."));
    }
}
