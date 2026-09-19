package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.repository.EquipmentTypeRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Mock
    private ReservationRepository reservationRepository;

    @Test
    void deleteIsRejectedWhenTheRoomHasDependentHistory() {
        RoomService roomService = new RoomService(
                roomRepository, floorRepository, equipmentTypeRepository, dependentHistoryChecker, reservationRepository);

        UUID roomId = UUID.randomUUID();
        Room room = new Room("Room 101", new Floor(new Building("Main"), "1"));
        when(roomRepository.findById(roomId)).thenReturn(java.util.Optional.of(room));
        when(dependentHistoryChecker.hasDependentHistory(roomId)).thenReturn(true);

        assertThatThrownBy(() -> roomService.delete(roomId)).isInstanceOf(ConflictException.class);
    }

    @Test
    void deactivateIsRejectedWhenRoomHasActiveOrUpcomingReservations() {
        RoomService roomService = new RoomService(
                roomRepository, floorRepository, equipmentTypeRepository, dependentHistoryChecker, reservationRepository);

        UUID roomId = UUID.randomUUID();
        Room room = new Room("Room 101", new Floor(new Building("Main"), "1"));
        when(roomRepository.findById(roomId)).thenReturn(java.util.Optional.of(room));
        when(reservationRepository.existsByRoomIdAndStatusIn(roomId, List.of(ReservationStatus.RESERVED, ReservationStatus.ACTIVE)))
                .thenReturn(true);

        assertThatThrownBy(() -> roomService.deactivate(roomId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active or upcoming reservations");
    }

    @Test
    void reservationRoomHistoryChecker_delegatesToRepository() {
        ReservationRoomHistoryChecker checker = new ReservationRoomHistoryChecker(reservationRepository);
        UUID roomId = UUID.randomUUID();
        when(reservationRepository.existsByRoomId(roomId)).thenReturn(true);

        assertThat(checker.hasDependentHistory(roomId)).isTrue();
    }
}
