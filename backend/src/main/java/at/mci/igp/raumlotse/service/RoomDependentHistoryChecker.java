package at.mci.igp.raumlotse.service;

import java.util.UUID;

/**
 * Whether a room has history from another feature (e.g. bookings) that must block a hard
 * delete (FR-010). Extracted as its own collaborator so it can be swapped/mocked independently
 * of {@link RoomService}, and so a future feature can provide a real implementation without
 * touching RoomService itself.
 */
public interface RoomDependentHistoryChecker {

    boolean hasDependentHistory(UUID roomId);
}
