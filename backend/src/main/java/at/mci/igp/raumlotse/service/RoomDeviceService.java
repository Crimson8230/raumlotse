package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import at.mci.igp.raumlotse.domain.PermissionCode;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.RoomDeviceCommandRequest;
import at.mci.igp.raumlotse.dto.RoomDeviceControlsResponse;
import at.mci.igp.raumlotse.dto.RoomDeviceResponse;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.DeviceAccessDeniedException;
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
    private final EffectivePermissionService permissions;

    @Autowired
    public RoomDeviceService(RoomRepository rooms, ReservationRepository reservations,
            RoomDeviceStateRepository states, RoomDeviceGateway gateway, EffectivePermissionService permissions) {
        this(rooms, reservations, states, gateway, Clock.systemUTC(), permissions);
    }

    public RoomDeviceService(RoomRepository rooms, ReservationRepository reservations,
            RoomDeviceStateRepository states, RoomDeviceGateway gateway, Clock clock) {
        this(rooms, reservations, states, gateway, clock, null);
    }

    /** Isolated legacy tests supply their own clock or use the system clock. */
    public RoomDeviceService(RoomRepository rooms, ReservationRepository reservations,
            RoomDeviceStateRepository states, RoomDeviceGateway gateway) {
        this(rooms, reservations, states, gateway, Clock.systemUTC(), null);
    }

    public RoomDeviceService(RoomRepository rooms, ReservationRepository reservations,
            RoomDeviceStateRepository states, RoomDeviceGateway gateway, Clock clock,
            EffectivePermissionService permissions) {
        this.roomRepository = rooms; this.reservationRepository = reservations;
        this.stateRepository = states; this.gateway = gateway; this.clock = clock;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public RoomDeviceControlsResponse getControls(UUID roomId) {
        Reservation reservation = authorize(roomId, Instant.now(clock));
        List<RoomDeviceResponse> devices = new ArrayList<>();
        devices.add(response(roomId, RoomDeviceKind.LIGHTING));
        devices.add(response(roomId, RoomDeviceKind.VENTILATION));
        if (hasActiveProjector(reservation.getRoom())) devices.add(response(roomId, RoomDeviceKind.PROJECTOR));
        return new RoomDeviceControlsResponse(roomId, reservation.getId(), devices);
    }

    public RoomDeviceResponse setState(UUID roomId, RoomDeviceKind kind, RoomDeviceCommandRequest command) {
        Reservation reservation = authorize(roomId, Instant.now(clock));
        if (kind == RoomDeviceKind.PROJECTOR && !hasActiveProjector(reservation.getRoom())) {
            throw new ConflictException("In diesem Raum ist kein Beamer verfügbar.");
        }
        RoomDeviceState current = stateRepository.findByRoomIdAndKind(roomId, kind)
                .orElseGet(() -> new RoomDeviceState(roomId, kind));
        try {
            gateway.setState(roomId, kind, command.state());
        } catch (RuntimeException ex) {
            log.warn("device_command_failed kind={} status=503", kind);
            throw new at.mci.igp.raumlotse.exception.DeviceOperationException("Das Gerät hat nicht bestätigt.");
        }
        current.setState(command.state());
        current.setEnabled(true);
        return RoomDeviceResponse.from(stateRepository.save(current));
    }

    private RoomDeviceResponse response(UUID roomId, RoomDeviceKind kind) {
        RoomDeviceState state = stateRepository.findByRoomIdAndKind(roomId, kind)
                .orElseGet(() -> stateRepository.save(new RoomDeviceState(roomId, kind)));
        return RoomDeviceResponse.from(state);
    }

    private Reservation authorize(UUID roomId, Instant now) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new DeviceAccessDeniedException("Anmeldung erforderlich.");
        }
        if (permissions != null) permissions.require(user.userId(), PermissionCode.OWN_ACTIVE_DEVICE_CONTROL);
        roomRepository.findById(roomId).orElseThrow(() -> new NotFoundException("Raum " + roomId + " nicht gefunden."));
        return reservationRepository.findEligibleDeviceReservations(roomId, user.userId(), now).stream()
                .findFirst().orElseThrow(() -> new DeviceAccessDeniedException("Keine passende aktive Reservierung."));
    }

    private boolean hasActiveProjector(at.mci.igp.raumlotse.domain.Room room) {
        return room.getEquipmentTypes().stream().anyMatch(e -> e.getStatus() == EntityStatus.ACTIVE
                && "PROJECTOR".equalsIgnoreCase(e.getCode()));
    }
}
