package at.mci.igp.raumlotse.config;

import java.time.Duration;

public final class ReservationPolicyConstants {

    private ReservationPolicyConstants() {
        // utility class
    }

    /**
     * Default grace period before an unattended RESERVED booking transitions to EXPIRED. Since feature 014 the value
     * in force is the admin setting in {@code check_in_settings} (FR-022); this is its migrated default.
     */
    public static final Duration CHECK_IN_GRACE_PERIOD = Duration.ofMinutes(5);

    /**
     * Default for how long before its start a booking may be checked in, if no other reservation of the room is
     * ongoing (feature 014). The value in force is the admin setting in {@code check_in_settings} (FR-022).
     */
    public static final Duration EARLY_CHECK_IN_PERIOD = Duration.ofMinutes(10);

    /**
     * Interval in milliseconds between background checks for overdue reservations.
     */
    public static final long EXPIRATION_POLL_INTERVAL_MS = 30_000L;
}
