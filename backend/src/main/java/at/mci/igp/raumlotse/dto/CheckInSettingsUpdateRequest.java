package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** New check-in times (feature 014, FR-022). */
public record CheckInSettingsUpdateRequest(
        @NotNull @Min(value = 0, message = "Der frühe Check-in muss zwischen 0 und 60 Minuten liegen.")
        @Max(value = 60, message = "Der frühe Check-in muss zwischen 0 und 60 Minuten liegen.") Integer earlyCheckInMinutes,
        @NotNull @Min(value = 1, message = "Die Kulanzzeit muss zwischen 1 und 30 Minuten liegen.")
        @Max(value = 30, message = "Die Kulanzzeit muss zwischen 1 und 30 Minuten liegen.") Integer gracePeriodMinutes) {
}
