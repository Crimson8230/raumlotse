package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

/**
 * What an on-site check-in for a room would do right now, without changing anything (feature 014). {@code detail}
 * explains why a booking cannot be checked in (e.g. "Der Raum ist noch belegt …"); it is null when it can.
 */
public record CheckInPreviewResponse(UUID roomId, String roomName, Outcome outcome, Booking reservation,
        Instant checkInOpensAt, String detail) {

    public enum Outcome { READY, ALREADY_ACTIVE, TOO_EARLY, EXPIRED, NO_MATCH }

    /** Only shown to the booking owner or an administrator. */
    public record Booking(UUID id, Instant startTime, Instant endTime, String reservedFor) {
        public static Booking from(Reservation reservation) {
            return new Booking(reservation.getId(), reservation.getStartTime(), reservation.getEndTime(),
                    reservation.getReservedFor());
        }
    }
}
