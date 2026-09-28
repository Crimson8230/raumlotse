package at.mci.igp.raumlotse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoomBarrierFreeTest {

    @ParameterizedTest(name = "groundFloor={0}, hasElevator={1}, notBarrierFree={2} -> {3}")
    @CsvSource({
            "false, false, false, false",
            "false, false, true,  false",
            "false, true,  false, true",
            "false, true,  true,  false",
            "true,  false, false, true",
            "true,  false, true,  false",
            "true,  true,  false, true",
            "true,  true,  true,  false",
    })
    void barrierFreeReachabilityTruthTable(boolean groundFloor, boolean hasElevator, boolean notBarrierFree,
            boolean expected) {
        Building building = new Building("Haus");
        building.setHasElevator(hasElevator);
        Floor floor = new Floor(building, "EG");
        floor.setGroundFloor(groundFloor);
        Room room = new Room("Raum", floor);
        room.setNotBarrierFree(notBarrierFree);

        assertThat(room.isBarrierFreeReachable()).isEqualTo(expected);
    }
}
