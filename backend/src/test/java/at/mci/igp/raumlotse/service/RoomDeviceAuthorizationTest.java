package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
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
class RoomDeviceAuthorizationTest {
    @Mock RoomRepository rooms;
    @Mock ReservationRepository reservations;
    @Mock RoomDeviceStateRepository states;
    @Mock RoomDeviceGateway gateway;

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void usesOneCapturedInstantForHalfOpenWindow() {
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant exactStart = Instant.parse("2026-10-01T10:00:00Z");
        when(rooms.findById(roomId)).thenReturn(Optional.of(new Room("R", new Floor(new Building("B"), "1"))));
        when(reservations.findEligibleDeviceReservations(roomId, userId, exactStart)).thenReturn(List.of());
        authenticate(userId);

        var service = new RoomDeviceService(rooms, reservations, states, gateway, Clock.fixed(exactStart, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.getControls(roomId)).isInstanceOf(DeviceAccessDeniedException.class);
        verify(reservations).findEligibleDeviceReservations(roomId, userId, exactStart);
    }

    @Test
    void doesNotTrustAnotherUserEvenWhenRoomIsKnown() {
        UUID roomId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        when(rooms.findById(roomId)).thenReturn(Optional.of(new Room("R", new Floor(new Building("B"), "1"))));
        when(reservations.findEligibleDeviceReservations(eq(roomId), eq(attackerId), any())).thenReturn(List.of());
        authenticate(attackerId);
        var service = new RoomDeviceService(rooms, reservations, states, gateway);
        assertThatThrownBy(() -> service.setState(roomId, RoomDeviceKind.LIGHTING,
                new at.mci.igp.raumlotse.dto.RoomDeviceCommandRequest(true)))
                .isInstanceOf(DeviceAccessDeniedException.class);
        verify(reservations, never()).findEligibleDeviceReservations(eq(roomId), eq(ownerId), any());
        verifyNoInteractions(gateway, states);
    }

    @Test
    void exactStartIsIncludedInTheAuthorizationWindow() {
        UUID roomId = UUID.randomUUID(); UUID userId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        Room room = new Room("R", new Floor(new Building("B"), "1"));
        Reservation reservation = new Reservation(); reservation.setRoom(room); reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setStartTime(start); reservation.setEndTime(start.plusSeconds(3600));
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));
        when(reservations.findEligibleDeviceReservations(roomId, userId, start)).thenReturn(List.of(reservation));
        when(states.findByRoomIdAndKind(any(), any())).thenReturn(Optional.empty());
        when(states.save(any(RoomDeviceState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        authenticate(userId);

        var controls = new RoomDeviceService(rooms, reservations, states, gateway, Clock.fixed(start, ZoneOffset.UTC)).getControls(roomId);
        assertThat(controls.devices()).hasSize(2);
    }

    @Test
    void exactEndIsExcludedFromTheAuthorizationWindow() {
        UUID roomId = UUID.randomUUID(); UUID userId = UUID.randomUUID();
        Instant end = Instant.parse("2026-10-01T01:00:00Z");
        when(rooms.findById(roomId)).thenReturn(Optional.of(new Room("R", new Floor(new Building("B"), "1"))));
        when(reservations.findEligibleDeviceReservations(roomId, userId, end)).thenReturn(List.of());
        authenticate(userId);

        assertThatThrownBy(() -> new RoomDeviceService(rooms, reservations, states, gateway,
                Clock.fixed(end, ZoneOffset.UTC)).getControls(roomId))
                .isInstanceOf(at.mci.igp.raumlotse.exception.DeviceAccessDeniedException.class);
    }

    @Test
    void nonActiveLifecycleStatusesHaveNoEligibleDeviceReservation() {
        UUID roomId = UUID.randomUUID(); UUID userId = UUID.randomUUID();
        when(rooms.findById(roomId)).thenReturn(Optional.of(new Room("R", new Floor(new Building("B"), "1"))));
        when(reservations.findEligibleDeviceReservations(eq(roomId), eq(userId), any())).thenReturn(List.of());
        authenticate(userId);
        var service = new RoomDeviceService(rooms, reservations, states, gateway);

        for (ReservationStatus ignored : List.of(ReservationStatus.RESERVED, ReservationStatus.COMPLETED,
                ReservationStatus.EXPIRED, ReservationStatus.CANCELLED)) {
            assertThatThrownBy(() -> service.getControls(roomId))
                    .isInstanceOf(at.mci.igp.raumlotse.exception.DeviceAccessDeniedException.class);
        }
        verifyNoInteractions(states, gateway);
    }

    private void authenticate(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedUser(userId, "User"), null, List.of()));
    }
}
