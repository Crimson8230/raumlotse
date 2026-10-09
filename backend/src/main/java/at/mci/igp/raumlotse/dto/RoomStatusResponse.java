package at.mci.igp.raumlotse.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only room status for the room display and future polling devices (feature 014). Carries no personal data;
 * {@code lastPresenceAt} is only filled for administrators.
 */
public record RoomStatusResponse(UUID roomId, String status, DeviceStatesResponse devices, Instant lastPresenceAt) {
}
