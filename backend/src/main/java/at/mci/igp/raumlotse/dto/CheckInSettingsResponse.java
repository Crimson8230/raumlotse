package at.mci.igp.raumlotse.dto;

import java.time.Instant;

public record CheckInSettingsResponse(int earlyCheckInMinutes, int gracePeriodMinutes, Instant updatedAt) {
}
