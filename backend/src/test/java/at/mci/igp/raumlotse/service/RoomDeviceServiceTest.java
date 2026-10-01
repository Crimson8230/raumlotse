package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.RoomDeviceCommandRequest;
import at.mci.igp.raumlotse.exception.DeviceAccessDeniedException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomDeviceStateRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
class RoomDeviceServiceTest {
    @Mock RoomRepository rooms;
    @Mock ReservationRepository reservations;
    @Mock RoomDeviceStateRepository states;
    @Mock RoomDeviceGateway gateway;

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void ownerCanReadAndPersistDefaultDeviceState() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Room room = new Room("Room", new Floor(new Building("Building"), "1"));
        Reservation reservation = new Reservation();
        reservation.setRoom(room); reservation.setStatus(ReservationStatus.ACTIVE);
        Instant now = Instant.parse("2026-10-01T10:00:00Z");
        reservation.setStartTime(now.minusSeconds(60)); reservation.setEndTime(now.plusSeconds(60));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(reservations.findEligibleDeviceReservations(roomId, userId, now)).thenReturn(List.of(reservation));
        when(states.findByRoomIdAndKind(roomId, RoomDeviceKind.LIGHTING)).thenReturn(Optional.empty());
        when(states.save(any(RoomDeviceState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(userId, "Owner"), null, List.of()));

        var service = new RoomDeviceService(rooms, reservations, states, gateway, Clock.fixed(now, ZoneOffset.UTC));
        var controls = service.getControls(roomId);
        assertThat(controls.devices()).extracting(device -> device.kind())
                .containsExactly(RoomDeviceKind.LIGHTING, RoomDeviceKind.VENTILATION);
        service.setState(roomId, RoomDeviceKind.LIGHTING, new RoomDeviceCommandRequest(true));
        verify(gateway).setState(roomId, RoomDeviceKind.LIGHTING, true);
    }

    @Test
    void userWithoutEligibleReservationCannotMutateState() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(rooms.findById(roomId)).thenReturn(Optional.of(new Room("Room", new Floor(new Building("B"), "1"))));
        when(reservations.findEligibleDeviceReservations(eq(roomId), eq(userId), any())).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(userId, "Other"), null, List.of()));
        var service = new RoomDeviceService(rooms, reservations, states, gateway);
        assertThatThrownBy(() -> service.setState(roomId, RoomDeviceKind.LIGHTING, new RoomDeviceCommandRequest(true)))
                .isInstanceOf(DeviceAccessDeniedException.class);
        verifyNoInteractions(gateway, states);
    }
}
