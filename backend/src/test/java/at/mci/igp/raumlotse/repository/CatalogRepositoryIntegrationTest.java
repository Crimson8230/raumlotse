package at.mci.igp.raumlotse.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.AbstractIntegrationTest;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.EquipmentTypeService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.RoomService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatCode;

@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private FloorRepository floorRepository;

    @Autowired
    private EquipmentTypeRepository equipmentTypeRepository;

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private FloorService floorService;

    @Autowired
    private EquipmentTypeService equipmentTypeService;

    @Autowired
    private RoomService roomService;

    @Test
    void buildingNameUniquenessIsCaseInsensitiveAtTheDbLevel() {
        var building = buildingService.create("Main Building");

        assertThatThrownBy(() -> buildingRepository.saveAndFlush(new at.mci.igp.raumlotse.domain.Building("main building")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(building.getId()).isNotNull();
    }

    @Test
    void floorNameUniquenessWithinBuildingIsCaseInsensitiveAtTheDbLevel() {
        var building = buildingService.create("Building B");
        floorService.create(building.getId(), "Ground Floor");

        assertThatThrownBy(() -> floorRepository.saveAndFlush(
                new at.mci.igp.raumlotse.domain.Floor(
                        buildingRepository.getReferenceById(building.getId()), "ground floor")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void equipmentTypeNameUniquenessIsCaseInsensitiveAtTheDbLevel() {
        equipmentTypeRepository.saveAndFlush(new at.mci.igp.raumlotse.domain.EquipmentType("Flipchart"));

        assertThatThrownBy(() -> equipmentTypeRepository.saveAndFlush(
                new at.mci.igp.raumlotse.domain.EquipmentType("flipchart")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deactivatingBuildingCascadesToItsFloors() {
        var building = buildingService.create("Building C");
        var floor = floorService.create(building.getId(), "1");

        buildingService.deactivate(building.getId());
        buildingRepository.flush();
        floorRepository.flush();

        var reloadedFloor = floorRepository.findById(floor.getId()).orElseThrow();
        assertThat(reloadedFloor.getStatus()).isEqualTo(EntityStatus.DEACTIVATED);
    }

    @Test
    void reactivatingFloorWhileBuildingStillDeactivatedIsRejected() {
        var building = buildingService.create("Building D");
        var floor = floorService.create(building.getId(), "2");
        buildingService.deactivate(building.getId());

        assertThatThrownBy(() -> floorService.reactivate(floor.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void creatingAFloorUnderADeactivatedBuildingIsRejected() {
        var building = buildingService.create("Building E");
        buildingService.deactivate(building.getId());

        assertThatThrownBy(() -> floorService.create(building.getId(), "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listingFloorsForAnUnknownBuildingIsRejected() {
        UUID unknownBuildingId = UUID.randomUUID();

        assertThatThrownBy(() -> floorService.listByBuilding(unknownBuildingId, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void equipmentTypeAssignedToARoomCannotBeDeletedButCanBeDeactivated() {
        var building = buildingService.create("Building F");
        var floor = floorService.create(building.getId(), "1");
        var equipmentType = equipmentTypeService.create("Flipchart Extra");
        roomService.create(new RoomCreateRequest(
                "Room 201", floor.getId(), List.of(new SeatingArrangementRequest("Theater", 10)),
                List.of(equipmentType.getId())));

        assertThatThrownBy(() -> equipmentTypeService.delete(equipmentType.getId()))
                .isInstanceOf(ConflictException.class);

        assertThatCode(() -> equipmentTypeService.deactivate(equipmentType.getId())).doesNotThrowAnyException();
    }
}
