package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.RoomDeviceCommandRequest;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomDeviceStateRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RoomDeviceProjectorTest {
    @Mock RoomRepository rooms;
    @Mock ReservationRepository reservations;
    @Mock RoomDeviceStateRepository states;
    @Mock RoomDeviceGateway gateway;

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void activeProjectorIsExposed() {
        UUID roomId = UUID.randomUUID(); UUID userId = UUID.randomUUID();
        Room room = room(); room.getEquipmentTypes().add(new EquipmentType("Projector", "PROJECTOR"));
        eligible(roomId, userId, room);
        when(states.findByRoomIdAndKind(any(), any())).thenReturn(Optional.empty());
        when(states.save(any(RoomDeviceState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var controls = new RoomDeviceService(rooms, reservations, states, gateway).getControls(roomId);
        assertThat(controls.devices()).extracting(device -> device.kind()).contains(RoomDeviceKind.PROJECTOR);
    }

    @Test
    void absentProjectorRejectsDirectCommand() {
        UUID roomId = UUID.randomUUID(); UUID userId = UUID.randomUUID();
        eligible(roomId, userId, room());
        assertThatThrownBy(() -> new RoomDeviceService(rooms, reservations, states, gateway)
                .setState(roomId, RoomDeviceKind.PROJECTOR, new RoomDeviceCommandRequest(true)))
                .isInstanceOf(ConflictException.class);
    }

    private Room room() { return new Room("R", new Floor(new Building("B"), "1")); }
    private void eligible(UUID roomId, UUID userId, Room room) {
        Reservation reservation = new Reservation(); reservation.setRoom(room); reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setStartTime(Instant.now().minusSeconds(60)); reservation.setEndTime(Instant.now().plusSeconds(60));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(reservations.findEligibleDeviceReservations(any(), any(), any())).thenReturn(List.of(reservation));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, "Owner"), null, List.of()));
    }
}
