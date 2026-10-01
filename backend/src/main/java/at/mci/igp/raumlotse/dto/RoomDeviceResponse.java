package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import java.time.Instant;

public record RoomDeviceResponse(RoomDeviceKind kind, boolean enabled, boolean state, Instant updatedAt) {
    public static RoomDeviceResponse from(RoomDeviceState value) {
        return new RoomDeviceResponse(value.getKind(), value.isEnabled(), value.isState(), value.getUpdatedAt());
    }
}
