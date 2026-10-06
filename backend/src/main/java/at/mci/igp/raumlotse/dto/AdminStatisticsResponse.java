package at.mci.igp.raumlotse.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminStatisticsResponse(
        StatisticsPeriod period,
        SummaryStatistics summary,
        List<RoomStatistics> rooms,
        List<FeatureStatistics> features) {

    public record StatisticsPeriod(LocalDate from, LocalDate to, String timezone) {}

    public record SummaryStatistics(
            long totalReservations,
            long validReservationCount,
            long cancelledReservationCount,
            Double cancellationRatePercent,
            long attendeeSum,
            long attendeeBookingCount,
            Double averageExpectedAttendees,
            long missingAttendeeCount) {}

    public record RoomStatistics(
            UUID roomId,
            String roomName,
            String roomStatus,
            long bookingCount,
            long bookedSeconds,
            double utilizationPercent) {}

    public record FeatureStatistics(
            UUID featureId,
            String featureName,
            String featureStatus,
            long bookingCount,
            long bookedSeconds) {}
}
