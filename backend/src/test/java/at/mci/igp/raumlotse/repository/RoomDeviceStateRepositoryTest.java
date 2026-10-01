package at.mci.igp.raumlotse.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.AbstractIntegrationTest;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.RoomService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RoomDeviceStateRepositoryTest extends AbstractIntegrationTest {
    @Autowired BuildingService buildings;
    @Autowired FloorService floors;
    @Autowired RoomService rooms;
    @Autowired RoomDeviceStateRepository states;

    @Test
    void onlyOneStatePerRoomAndKindIsAllowed() {
        var building = buildings.create("Device Test Building");
        var floor = floors.create(building.getId(), "Device Test Floor");
        var room = rooms.create(new RoomCreateRequest("Device Test Room", floor.getId(),
                List.of(new SeatingArrangementRequest("Theater", 10)), List.of()));
        states.saveAndFlush(new RoomDeviceState(room.getId(), RoomDeviceKind.LIGHTING));

        assertThatThrownBy(() -> states.saveAndFlush(new RoomDeviceState(room.getId(), RoomDeviceKind.LIGHTING)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
