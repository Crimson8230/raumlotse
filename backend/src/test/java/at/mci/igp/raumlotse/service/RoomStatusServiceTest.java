package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.RoomStatusResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomDeviceStateRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomStatusServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

    @Mock RoomRepository rooms;
    @Mock ReservationRepository reservations;
    @Mock RoomDeviceStateRepository states;

    private final Actor user = new Actor(UUID.randomUUID(), false);
    private RoomStatusService service;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        lenient().when(rooms.findById(roomId)).thenReturn(Optional.of(new Room("R", new Floor(new Building("B"), "1"))));
        lenient().when(reservations.findCovering(roomId, NOW)).thenReturn(List.of());
        service = new RoomStatusService(rooms, reservations, states, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Reservation covering(ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setStatus(status);
        reservation.setStartTime(NOW.minus(Duration.ofMinutes(10)));
        reservation.setEndTime(NOW.plus(Duration.ofMinutes(50)));
        return reservation;
    }

    @Test
    void activeBookingCoveringNowMeansOccupied() {
        when(reservations.findCovering(roomId, NOW)).thenReturn(List.of(covering(ReservationStatus.ACTIVE)));
        assertThat(service.status(roomId, user).status()).isEqualTo("OCCUPIED");
    }

    @Test
    void onlyReservedBookingCoveringNowMeansReserved() {
        when(reservations.findCovering(roomId, NOW)).thenReturn(List.of(covering(ReservationStatus.RESERVED)));
        assertThat(service.status(roomId, user).status()).isEqualTo("RESERVED");
    }

    @Test
    void activeWinsOverReserved() {
        when(reservations.findCovering(roomId, NOW))
                .thenReturn(List.of(covering(ReservationStatus.RESERVED), covering(ReservationStatus.ACTIVE)));
        assertThat(service.status(roomId, user).status()).isEqualTo("OCCUPIED");
    }

    @Test
    void noCoveringBookingMeansAvailable() {
        assertThat(service.status(roomId, user).status()).isEqualTo("AVAILABLE");
    }

    @Test
    void missingDeviceRowsAreReportedOffAndLockedWithoutWriting() {
        RoomStatusResponse status = service.status(roomId, user);

        assertThat(status.devices().lighting()).isFalse();
        assertThat(status.devices().ventilation()).isFalse();
        assertThat(status.devices().door()).isEqualTo("LOCKED");
        verify(states, never()).save(any());
    }

    @Test
    void storedDeviceStatesAreReported() {
        RoomDeviceState light = new RoomDeviceState(roomId, RoomDeviceKind.LIGHTING);
        light.setState(true);
        RoomDeviceState door = new RoomDeviceState(roomId, RoomDeviceKind.DOOR);
        door.setState(true);
        lenient().when(states.findByRoomIdAndKind(roomId, RoomDeviceKind.LIGHTING)).thenReturn(Optional.of(light));
        lenient().when(states.findByRoomIdAndKind(roomId, RoomDeviceKind.DOOR)).thenReturn(Optional.of(door));

        RoomStatusResponse status = service.status(roomId, user);

        assertThat(status.devices().lighting()).isTrue();
        assertThat(status.devices().ventilation()).isFalse();
        assertThat(status.devices().door()).isEqualTo("UNLOCKED");
    }

    @Test
    void lastPresenceIsHiddenFromNonAdministrators() {
        Reservation active = covering(ReservationStatus.ACTIVE);
        active.setLastPresenceAt(NOW.minusSeconds(30));
        lenient().when(reservations.findCovering(roomId, NOW)).thenReturn(List.of(active));
        lenient().when(reservations.findActiveCovering(roomId, NOW)).thenReturn(Optional.of(active));

        assertThat(service.status(roomId, user).lastPresenceAt()).isNull();
    }

    @Test
    void administratorsSeeTheLatestPresenceOfTheBookingInUse() {
        Reservation active = covering(ReservationStatus.ACTIVE);
        active.setLastPresenceAt(NOW.minusSeconds(30));
        when(reservations.findCovering(roomId, NOW)).thenReturn(List.of(active));

        assertThat(service.status(roomId, new Actor(UUID.randomUUID(), true)).lastPresenceAt())
                .isEqualTo(NOW.minusSeconds(30));
    }

    @Test
    void unknownRoomIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(rooms.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.status(unknown, user)).isInstanceOf(NotFoundException.class);
    }
}
