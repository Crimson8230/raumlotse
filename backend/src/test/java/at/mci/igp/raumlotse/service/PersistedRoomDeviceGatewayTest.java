package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.RoomDeviceCommandRequest;
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
class PersistedRoomDeviceGatewayTest {
    @Mock RoomRepository rooms;
    @Mock ReservationRepository reservations;
    @Mock RoomDeviceStateRepository states;
    @Mock RoomDeviceGateway gateway;

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void failedAcknowledgementDoesNotPersistNewState() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Room room = new Room("R", new Floor(new Building("B"), "1"));
        Reservation reservation = new Reservation();
        reservation.setRoom(room); reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setStartTime(Instant.now().minusSeconds(60)); reservation.setEndTime(Instant.now().plusSeconds(60));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(reservations.findEligibleDeviceReservations(eq(roomId), eq(userId), any())).thenReturn(List.of(reservation));
        when(states.findByRoomIdAndKind(roomId, RoomDeviceKind.LIGHTING)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("simulated device failure")).when(gateway).setState(roomId, RoomDeviceKind.LIGHTING, true);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, "Owner"), null, List.of()));

        var service = new RoomDeviceService(rooms, reservations, states, gateway);
        assertThatThrownBy(() -> service.setState(roomId, RoomDeviceKind.LIGHTING, new RoomDeviceCommandRequest(true)))
                .isInstanceOf(RuntimeException.class);
        verify(states, never()).save(any(RoomDeviceState.class));
    }
}
