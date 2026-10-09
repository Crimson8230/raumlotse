package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.config.ReservationPolicyConstants;
import java.time.Duration;

/**
 * The check-in times in force: how long before its start a booking may be checked in and how long after its start
 * it may still be checked in before it expires (feature 014, FR-022).
 */
public record CheckInPolicy(Duration earlyCheckIn, Duration gracePeriod) {
    /** The former constants, used where no settings are available (unit tests, missing row). */
    public static final CheckInPolicy DEFAULT = new CheckInPolicy(
            ReservationPolicyConstants.EARLY_CHECK_IN_PERIOD, ReservationPolicyConstants.CHECK_IN_GRACE_PERIOD);
}
