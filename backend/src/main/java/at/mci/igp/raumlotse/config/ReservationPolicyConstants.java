package at.mci.igp.raumlotse.config;

import java.time.Duration;

public final class ReservationPolicyConstants {

    private ReservationPolicyConstants() {
        // utility class
    }

    /**
     * Standardized grace period before an unattended RESERVED booking transitions to EXPIRED.
     */
    public static final Duration CHECK_IN_GRACE_PERIOD = Duration.ofMinutes(5);

    /**
     * Interval in milliseconds between background checks for overdue reservations.
     */
    public static final long EXPIRATION_POLL_INTERVAL_MS = 30_000L;
}
