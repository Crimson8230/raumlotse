package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Reservation;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * When a booking can be checked in, for every check-in method (feature 014, FR-004, FR-022): from
 * {@code startTime - early check-in} to {@code startTime + grace period} (admin settings, default 10 and 5 minutes)
 * and before {@code endTime}. Before the start only while no
 * other reservation of the room is ongoing; then check-in opens when that reservation ends.
 */
final class CheckInWindow {
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(AdminStatisticsPeriod.ZONE);

    private CheckInWindow() {
    }

    /** The other reservations of the room that are ongoing now ({@code ongoing} may contain the booking itself). */
    static Optional<Instant> occupiedUntil(Reservation booking, List<Reservation> ongoing) {
        return ongoing.stream()
                .filter(other -> !other.getId().equals(booking.getId()))
                .map(Reservation::getEndTime)
                .max(Comparator.naturalOrder());
    }

    static boolean beforeStart(Reservation booking, Instant now) {
        return now.isBefore(booking.getStartTime());
    }

    static Instant earliest(Reservation booking, CheckInPolicy policy) {
        return booking.getStartTime().minus(policy.earlyCheckIn());
    }

    /** The time window alone, without the "room still occupied" rule. */
    static boolean timeOpen(Reservation booking, Instant now, CheckInPolicy policy) {
        Instant graceEnd = booking.getStartTime().plus(policy.gracePeriod());
        return !now.isBefore(earliest(booking, policy)) && !now.isAfter(graceEnd) && now.isBefore(booking.getEndTime());
    }

    static boolean missed(Reservation booking, Instant now, CheckInPolicy policy) {
        Instant graceEnd = booking.getStartTime().plus(policy.gracePeriod());
        return now.isAfter(graceEnd) || !now.isBefore(booking.getEndTime());
    }

    /** When check-in opens for a booking that cannot be checked in yet. */
    static Instant opensAt(Reservation booking, Optional<Instant> occupiedUntil, CheckInPolicy policy) {
        Instant earliest = earliest(booking, policy);
        return occupiedUntil.filter(end -> end.isAfter(earliest)).orElse(earliest);
    }

    static String tooEarlyMessage(Instant opensAt, boolean occupied) {
        String time = TIME.format(opensAt);
        return occupied
                ? "Der Raum ist noch belegt. Der Check-in ist ab " + time + " Uhr möglich."
                : "Der Check-in ist ab " + time + " Uhr möglich.";
    }
}
