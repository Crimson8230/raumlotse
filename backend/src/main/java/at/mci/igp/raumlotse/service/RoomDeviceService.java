package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.RoomDeviceCommandRequest;
import at.mci.igp.raumlotse.dto.RoomDeviceControlsResponse;
import at.mci.igp.raumlotse.dto.RoomDeviceResponse;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.DeviceAccessDeniedException;
import at.mci.igp.raumlotse.exception.DeviceOperationException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomDeviceStateRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional
public class RoomDeviceService {
    private static final Logger log = LoggerFactory.getLogger(RoomDeviceService.class);
    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final RoomDeviceStateRepository stateRepository;
    private final RoomDeviceGateway gateway;
    private final Clock clock;

    @Autowired
    public RoomDeviceService(RoomRepository rooms, ReservationRepository reservations,
            RoomDeviceStateRepository states, RoomDeviceGateway gateway) {
        this(rooms, reservations, states, gateway, Clock.systemUTC());
    }

    public RoomDeviceService(RoomRepository rooms, ReservationRepository reservations,
            RoomDeviceStateRepository states, RoomDeviceGateway gateway, Clock clock) {
        this.roomRepository = rooms; this.reservationRepository = reservations;
        this.stateRepository = states; this.gateway = gateway; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RoomDeviceControlsResponse getControls(UUID roomId) {
        Reservation reservation = authorize(roomId, Instant.now(clock));
        List<RoomDeviceResponse> devices = new ArrayList<>();
        devices.add(response(roomId, RoomDeviceKind.LIGHTING));
        devices.add(response(roomId, RoomDeviceKind.VENTILATION));
        devices.add(response(roomId, RoomDeviceKind.DOOR));
        if (hasActiveProjector(reservation.getRoom())) devices.add(response(roomId, RoomDeviceKind.PROJECTOR));
        return new RoomDeviceControlsResponse(roomId, reservation.getId(), devices);
    }

    public RoomDeviceResponse setState(UUID roomId, RoomDeviceKind kind, RoomDeviceCommandRequest command) {
        Reservation reservation = authorize(roomId, Instant.now(clock));
        if (kind == RoomDeviceKind.PROJECTOR && !hasActiveProjector(reservation.getRoom())) {
            throw new ConflictException("In diesem Raum ist kein Beamer verfügbar.");
        }
        return RoomDeviceResponse.from(apply(roomId, kind, command.state()));
    }

    /**
     * Switches a device through the gateway without user authorization, for room automation (feature 014). The state
     * is stored only after the gateway acknowledged it; an unchanged state sends no command. A device that does not
     * acknowledge must not roll back the caller's transaction (the check-in or the sweep), so the exception is excluded
     * from rollback: nothing has been written when it is thrown.
     */
    @Transactional(noRollbackFor = DeviceOperationException.class)
    public RoomDeviceState apply(UUID roomId, RoomDeviceKind kind, boolean state) {
        RoomDeviceState current = stateRepository.findByRoomIdAndKind(roomId, kind)
                .orElseGet(() -> new RoomDeviceState(roomId, kind));
        if (current.isState() == state) {
            return current;
        }
        try {
            gateway.setState(roomId, kind, state);
        } catch (RuntimeException ex) {
            log.warn("device_command_failed kind={} status=503", kind);
            throw new DeviceOperationException("Das Gerät hat nicht bestätigt.");
        }
        current.setState(state);
        current.setEnabled(true);
        return stateRepository.save(current);
    }

    private RoomDeviceResponse response(UUID roomId, RoomDeviceKind kind) {
        RoomDeviceState state = stateRepository.findByRoomIdAndKind(roomId, kind)
                .orElseGet(() -> stateRepository.save(new RoomDeviceState(roomId, kind)));
        return RoomDeviceResponse.from(state);
    }

    private Reservation authorize(UUID roomId, Instant now) {
        var room = roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Raum " + roomId + " nicht gefunden."));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new DeviceAccessDeniedException("Anmeldung erforderlich.");
        }
        return reservationRepository.findEligibleDeviceReservations(roomId, user.userId(), now).stream()
                .findFirst().orElseThrow(() -> new DeviceAccessDeniedException("Keine passende aktive Reservierung."));
    }

    private boolean hasActiveProjector(at.mci.igp.raumlotse.domain.Room room) {
        return room.getEquipmentTypes().stream().anyMatch(e -> e.getStatus() == EntityStatus.ACTIVE
                && "PROJECTOR".equalsIgnoreCase(e.getCode()));
    }
}
