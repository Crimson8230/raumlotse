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

    private RoomService service() {
        return new RoomService(
                roomRepository, floorRepository, equipmentTypeRepository, dependentHistoryChecker, reservationRepository);
    }

    private at.mci.igp.raumlotse.dto.RoomCreateRequest createRequest(Boolean notBarrierFree) {
        return new at.mci.igp.raumlotse.dto.RoomCreateRequest("Room 101", UUID.randomUUID(),
                List.of(new at.mci.igp.raumlotse.dto.SeatingArrangementRequest("Theater", 40)), List.of(), notBarrierFree);
    }

    private at.mci.igp.raumlotse.dto.RoomUpdateRequest updateRequest(Boolean notBarrierFree) {
        return new at.mci.igp.raumlotse.dto.RoomUpdateRequest("Room 101", UUID.randomUUID(),
                List.of(new at.mci.igp.raumlotse.dto.SeatingArrangementRequest("Theater", 40)), List.of(), 0L,
                notBarrierFree);
    }

    private void stubSelectableFloor() {
        when(floorRepository.findById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(new Floor(new Building("Main"), "1")));
    }

    @Test
    void createWithoutNotBarrierFreeDefaultsToFalse() {
        stubSelectableFloor();
        when(roomRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service().create(createRequest(null)).isNotBarrierFree()).isFalse();
    }

    @Test
    void createWithNotBarrierFreeStoresIt() {
        stubSelectableFloor();
        when(roomRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service().create(createRequest(true)).isNotBarrierFree()).isTrue();
    }

    private Room existingRoom(UUID id, boolean notBarrierFree) {
        Room room = new Room("Room 101", new Floor(new Building("Main"), "1"));
        room.setNotBarrierFree(notBarrierFree);
        org.springframework.test.util.ReflectionTestUtils.setField(room, "version", 0L);
        when(roomRepository.findById(id)).thenReturn(java.util.Optional.of(room));
        return room;
    }

    @Test
    void updateWithNullNotBarrierFreeKeepsTheCurrentValue() {
        UUID id = UUID.randomUUID();
        existingRoom(id, true);
        stubSelectableFloor();

        assertThat(service().update(id, updateRequest(null)).isNotBarrierFree()).isTrue();
    }

    @Test
    void updateWithFalseClearsTheExclusion() {
        UUID id = UUID.randomUUID();
        existingRoom(id, true);
        stubSelectableFloor();

        assertThat(service().update(id, updateRequest(false)).isNotBarrierFree()).isFalse();
    }
}
