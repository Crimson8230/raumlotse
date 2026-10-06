package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.dto.AdminStatisticsResponse;
import at.mci.igp.raumlotse.repository.StatisticsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatisticsService {
    private final StatisticsRepository repository;

    public AdminStatisticsService(StatisticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AdminStatisticsResponse getStatistics(LocalDate from, LocalDate to) {
        AdminStatisticsPeriod period = AdminStatisticsPeriod.of(from, to);
        List<StatisticsRepository.RoomRow> rooms = repository.findRooms();
        List<StatisticsRepository.FeatureRow> features = repository.findFeatures();
        List<StatisticsRepository.ReservationRow> reservations = repository.findReservations(period.fromInstant(), period.toInstant());

        Map<UUID, RoomAccumulator> roomValues = new HashMap<>();
        rooms.forEach(room -> roomValues.put(room.id(), new RoomAccumulator(room)));
        Map<UUID, FeatureAccumulator> featureValues = new HashMap<>();
        features.forEach(feature -> featureValues.put(feature.id(), new FeatureAccumulator(feature)));

        long validCount = 0;
        long cancelledCount = 0;
        long attendeeSum = 0;
        long attendeeCount = 0;
        long missingAttendees = 0;

        for (StatisticsRepository.ReservationRow reservation : reservations) {
            if (reservation.status() == ReservationStatus.CANCELLED) {
                cancelledCount++;
            }
            if (isValidUsage(reservation.status())) {
                validCount++;
                long seconds = overlapSeconds(reservation.startTime(), reservation.endTime(), period.fromInstant(), period.toInstant());
                RoomAccumulator room = roomValues.get(reservation.roomId());
                if (room != null) {
                    room.bookingCount++;
                    room.bookedSeconds += seconds;
                }
                for (UUID featureId : reservation.featureIds()) {
                    FeatureAccumulator feature = featureValues.get(featureId);
                    if (feature != null) {
                        feature.bookingCount++;
                        feature.bookedSeconds += seconds;
                    }
                }
                if (reservation.expectedAttendees() > 0) {
                    attendeeSum += reservation.expectedAttendees();
                    attendeeCount++;
                } else {
                    missingAttendees++;
                }
            }
        }

        long totalSeconds = Duration.between(period.fromInstant(), period.toInstant()).getSeconds();
        Double cancellationRate = reservations.isEmpty() ? null : roundedPercent(cancelledCount, reservations.size());
        Double averageAttendees = attendeeCount == 0 ? null : rounded(attendeeSum / (double) attendeeCount);
        AdminStatisticsResponse.SummaryStatistics summary = new AdminStatisticsResponse.SummaryStatistics(
                reservations.size(), validCount, cancelledCount, cancellationRate, attendeeSum, attendeeCount,
                averageAttendees, missingAttendees);

        List<AdminStatisticsResponse.RoomStatistics> roomStats = rooms.stream().map(room -> {
            RoomAccumulator value = roomValues.get(room.id());
            return new AdminStatisticsResponse.RoomStatistics(room.id(), room.name(), room.status().name(),
                    value.bookingCount, value.bookedSeconds, roundedPercent(value.bookedSeconds, totalSeconds));
        }).toList();
        List<AdminStatisticsResponse.FeatureStatistics> featureStats = features.stream().map(feature -> {
            FeatureAccumulator value = featureValues.get(feature.id());
            return new AdminStatisticsResponse.FeatureStatistics(feature.id(), feature.name(), feature.status().name(),
                    value.bookingCount, value.bookedSeconds);
        }).toList();
        return new AdminStatisticsResponse(
                new AdminStatisticsResponse.StatisticsPeriod(period.from(), period.to(), AdminStatisticsPeriod.ZONE.getId()),
                summary, roomStats, featureStats);
    }

    private static boolean isValidUsage(ReservationStatus status) {
        return status == ReservationStatus.RESERVED || status == ReservationStatus.ACTIVE || status == ReservationStatus.COMPLETED;
    }

    private static long overlapSeconds(Instant start, Instant end, Instant from, Instant to) {
        Instant clippedStart = start.isAfter(from) ? start : from;
        Instant clippedEnd = end.isBefore(to) ? end : to;
        return clippedEnd.isAfter(clippedStart) ? Duration.between(clippedStart, clippedEnd).getSeconds() : 0;
    }

    private static double roundedPercent(long numerator, long denominator) {
        return denominator == 0 ? 0 : rounded(numerator * 100.0 / denominator);
    }

    private static double rounded(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static final class RoomAccumulator {
        private long bookingCount;
        private long bookedSeconds;
        private RoomAccumulator(StatisticsRepository.RoomRow ignored) {}
    }

    private static final class FeatureAccumulator {
        private long bookingCount;
        private long bookedSeconds;
        private FeatureAccumulator(StatisticsRepository.FeatureRow ignored) {}
    }
}
