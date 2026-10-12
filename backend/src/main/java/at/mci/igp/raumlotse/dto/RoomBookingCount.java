package at.mci.igp.raumlotse.dto;

import java.util.UUID;

/** Number of counted bookings of one room, used as popularity in the search ranking (feature 017, FR-005). */
public record RoomBookingCount(UUID roomId, long count) {
}
