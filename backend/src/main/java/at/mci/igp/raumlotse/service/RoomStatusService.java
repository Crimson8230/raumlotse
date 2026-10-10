package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.DeviceStatesResponse;
import at.mci.igp.raumlotse.dto.RoomStatusResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomDeviceStateRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only room status (feature 014). Uses the same rule as the room display (feature 009): OCCUPIED while an ACTIVE
 * reservation covers now, RESERVED while only a RESERVED one does, otherwise AVAILABLE. Missing device rows are
 * reported with their defaults (off, locked) and never created here.
 */
@Service
@Transactional(readOnly = true)
public class RoomStatusService {
    private final RoomRepository rooms;
    private final ReservationRepository reservations;
    private final RoomDeviceStateRepository states;
    private final Clock clock;

    public RoomStatusService(RoomRepository rooms, ReservationRepository reservations,
            RoomDeviceStateRepository states, Clock clock) {
        this.rooms = rooms;
        this.reservations = reservations;
        this.states = states;
        this.clock = clock;
    }

    public RoomStatusResponse status(UUID roomId, Actor actor) {
        if (rooms.findById(roomId).isEmpty()) {
            throw new NotFoundException("Raum " + roomId + " nicht gefunden.");
        }
        Instant now = clock.instant();
        List<Reservation> covering = reservations.findCovering(roomId, now);
        Instant lastPresenceAt = actor.admin() ? covering.stream()
                .filter(r -> r.getStatus() == ReservationStatus.ACTIVE)
                .map(Reservation::getLastPresenceAt)
                .filter(Objects::nonNull)
                .findFirst().orElse(null) : null;
        return new RoomStatusResponse(roomId, statusOf(covering), devicesFor(roomId), lastPresenceAt);
    }

    public DeviceStatesResponse devicesFor(UUID roomId) {
        return new DeviceStatesResponse(stateOf(roomId, RoomDeviceKind.LIGHTING), stateOf(roomId, RoomDeviceKind.VENTILATION),
                DeviceStatesResponse.doorState(stateOf(roomId, RoomDeviceKind.DOOR)));
    }

    private boolean stateOf(UUID roomId, RoomDeviceKind kind) {
        return states.findByRoomIdAndKind(roomId, kind).map(RoomDeviceState::isState).orElse(false);
    }

    private static String statusOf(List<Reservation> covering) {
        if (covering.stream().anyMatch(r -> r.getStatus() == ReservationStatus.ACTIVE)) {
            return "OCCUPIED";
        }
        return covering.stream().anyMatch(r -> r.getStatus() == ReservationStatus.RESERVED) ? "RESERVED" : "AVAILABLE";
    }
}
