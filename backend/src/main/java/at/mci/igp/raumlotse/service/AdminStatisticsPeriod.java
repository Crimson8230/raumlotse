package at.mci.igp.raumlotse.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record AdminStatisticsPeriod(LocalDate from, LocalDate to, Instant fromInstant, Instant toInstant) {
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final int MAX_DAYS = 366;

    public static AdminStatisticsPeriod of(LocalDate from, LocalDate to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("The statistics start date must be before the end date.");
        }
        if (to.toEpochDay() - from.toEpochDay() > MAX_DAYS) {
            throw new IllegalArgumentException("The statistics period may not exceed 366 days.");
        }
        return new AdminStatisticsPeriod(from, to, from.atStartOfDay(ZONE).toInstant(), to.atStartOfDay(ZONE).toInstant());
    }
}
