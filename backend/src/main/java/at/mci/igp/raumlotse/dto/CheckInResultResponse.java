package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.CheckInMethod;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Result of an accepted (or repeated) on-site check-in, including the room's device states (feature 014). */
public record CheckInResultResponse(UUID reservationId, String status, boolean alreadyActive,
        CheckInMethod checkInMethod, Instant checkedInAt, DeviceStatesResponse devices,
        List<RoomDeviceKind> failedDevices) {
}
