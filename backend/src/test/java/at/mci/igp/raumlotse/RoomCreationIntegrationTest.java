package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.RoomService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomCreationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private FloorService floorService;

    @Test
    void createsARoomEndToEnd() {
        var building = buildingService.create("Main");
        var floor = floorService.create(building.getId(), "1");

        var room = roomService.create(new RoomCreateRequest(
                "Room 101",
                floor.getId(),
                List.of(new SeatingArrangementRequest("Theater", 40)),
                List.of()));

        assertThat(room.getId()).isNotNull();
        assertThat(room.getSeatingArrangements()).hasSize(1);
    }

    @Test
    void rejectsADuplicateRoomNameWithinTheSameBuilding() {
        var building = buildingService.create("Main 2");
        var floor = floorService.create(building.getId(), "1");
        roomService.create(new RoomCreateRequest(
                "Room 101", floor.getId(), List.of(new SeatingArrangementRequest("Theater", 40)), List.of()));

        assertThatThrownBy(() -> roomService.create(new RoomCreateRequest(
                "room 101", floor.getId(), List.of(new SeatingArrangementRequest("Theater", 40)), List.of())))
                .isInstanceOf(ConflictException.class);
    }
}
