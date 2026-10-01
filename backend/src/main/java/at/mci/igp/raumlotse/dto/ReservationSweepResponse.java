package at.mci.igp.raumlotse.dto;

public record ReservationSweepResponse(
        int expiredCount,
        int completedCount
) {}
