package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.exception.DeviceOperationException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Prepares a room when a booking goes into use and releases it when the booking ends (feature 014). Each device is
 * switched on its own; a device that does not acknowledge is logged and reported but never undoes the status change.
 */
@Service
public class RoomAutomationService {
    private static final Logger log = LoggerFactory.getLogger(RoomAutomationService.class);
    private static final List<Map.Entry<RoomDeviceKind, Boolean>> PREPARE = List.of(
            Map.entry(RoomDeviceKind.LIGHTING, true),
            Map.entry(RoomDeviceKind.VENTILATION, true),
            Map.entry(RoomDeviceKind.DOOR, true));
    /** The door is deliberately left as it is when a booking ends, so nobody is locked in or out. */
    private static final List<Map.Entry<RoomDeviceKind, Boolean>> RELEASE = List.of(
            Map.entry(RoomDeviceKind.LIGHTING, false),
            Map.entry(RoomDeviceKind.VENTILATION, false));

    /** Devices that could not be switched. */
    public record AutomationResult(List<RoomDeviceKind> failedDevices) { }

    private final RoomDeviceService devices;
    private final ReservationRepository reservations;

    public RoomAutomationService(RoomDeviceService devices, ReservationRepository reservations) {
        this.devices = devices;
        this.reservations = reservations;
    }

    public AutomationResult prepare(Reservation reservation) {
        return switchDevices(reservation, PREPARE, "prepare");
    }

    /** Switches the room off when a booking ends, unless another booking of the room is already in use. */
    public AutomationResult release(Reservation reservation) {
        if (reservations.existsByRoomIdAndStatusAndIdNot(reservation.getRoom().getId(), ReservationStatus.ACTIVE,
                reservation.getId())) {
            return new AutomationResult(List.of());
        }
        return switchDevices(reservation, RELEASE, "release");
    }

    private AutomationResult switchDevices(Reservation reservation, List<Map.Entry<RoomDeviceKind, Boolean>> targets,
            String phase) {
        UUID roomId = reservation.getRoom().getId();
        List<RoomDeviceKind> failed = new ArrayList<>();
        for (Map.Entry<RoomDeviceKind, Boolean> target : targets) {
            try {
                devices.apply(roomId, target.getKey(), target.getValue());
            } catch (DeviceOperationException ex) {
                log.warn("room_automation_device_failed roomId={} reservationId={} kind={} phase={}",
                        roomId, reservation.getId(), target.getKey(), phase);
                failed.add(target.getKey());
            }
        }
        return new AutomationResult(List.copyOf(failed));
    }
}
