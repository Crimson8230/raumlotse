package at.mci.igp.raumlotse.exception;

import at.mci.igp.raumlotse.dto.CheckInPreviewResponse.Outcome;

/** An on-site check-in that cannot confirm a booking now (TOO_EARLY, EXPIRED or NO_MATCH). */
public class CheckInRejectedException extends RuntimeException {
    private final Outcome reason;

    public CheckInRejectedException(Outcome reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public Outcome getReason() {
        return reason;
    }
}
