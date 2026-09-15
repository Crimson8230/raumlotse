package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.repository.EquipmentTypeRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private EquipmentTypeRepository equipmentTypeRepository;

    @Mock
    private RoomDependentHistoryChecker dependentHistoryChecker;

    @Test
    void deleteIsRejectedWhenTheRoomHasDependentHistory() {
        RoomService roomService = new RoomService(roomRepository, floorRepository, equipmentTypeRepository, dependentHistoryChecker);

        UUID roomId = UUID.randomUUID();
        Room room = new Room("Room 101", new Floor(new Building("Main"), "1"));
        when(roomRepository.findById(roomId)).thenReturn(java.util.Optional.of(room));
        when(dependentHistoryChecker.hasDependentHistory(roomId)).thenReturn(true);

        assertThatThrownBy(() -> roomService.delete(roomId)).isInstanceOf(ConflictException.class);
    }
}
