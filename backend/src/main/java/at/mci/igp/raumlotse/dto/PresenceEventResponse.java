package at.mci.igp.raumlotse.dto;

import java.time.Instant;

/** Outcome of a (simulated) motion event: recorded only while a booking of the room is in use (feature 014). */
public record PresenceEventResponse(boolean recorded, Instant lastPresenceAt) {
}
