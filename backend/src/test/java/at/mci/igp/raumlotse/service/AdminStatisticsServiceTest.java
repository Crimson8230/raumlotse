package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.dto.AdminStatisticsResponse;
import at.mci.igp.raumlotse.repository.StatisticsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminStatisticsServiceTest {
    @Test
    void rejectsInvalidPeriod() {
        AdminStatisticsService service = new AdminStatisticsService(new StatisticsRepository());

        assertThatThrownBy(() -> service.getStatistics(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculatesClippedUsageAndExcludesCancelledReservations() {
        UUID roomId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        StatisticsRepository repository = new StatisticsRepository() {
            @Override
            public List<RoomRow> findRooms() {
                return List.of(new RoomRow(roomId, "Room A", EntityStatus.ACTIVE, List.of(featureId)));
            }

            @Override
            public List<FeatureRow> findFeatures() {
                return List.of(new FeatureRow(featureId, "Projector", EntityStatus.ACTIVE));
            }

            @Override
            public List<ReservationRow> findReservations(Instant from, Instant to) {
                return List.of(
                        new ReservationRow(UUID.randomUUID(), roomId,
                                Instant.parse("2025-12-31T23:00:00Z"), Instant.parse("2026-01-01T02:00:00Z"),
                                ReservationStatus.COMPLETED, 10, List.of(featureId)),
                        new ReservationRow(UUID.randomUUID(), roomId,
                                Instant.parse("2026-01-01T10:00:00Z"), Instant.parse("2026-01-01T11:00:00Z"),
                                ReservationStatus.CANCELLED, 20, List.of(featureId)));
            }
        };
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-03T00:00:00Z");

        AdminStatisticsResponse response = new AdminStatisticsService(repository)
                .getStatistics(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));

        assertThat(response.rooms()).singleElement().satisfies(room -> {
            assertThat(room.bookingCount()).isEqualTo(1);
            assertThat(room.bookedSeconds()).isEqualTo(10800);
            assertThat(room.utilizationPercent()).isEqualTo(6.25);
        });
        assertThat(response.features()).singleElement().extracting(
                AdminStatisticsResponse.FeatureStatistics::bookingCount).isEqualTo(1L);
        assertThat(response.summary().cancelledReservationCount()).isEqualTo(1L);
        assertThat(response.summary().cancellationRatePercent()).isEqualTo(50.0);
        assertThat(response.summary().averageExpectedAttendees()).isEqualTo(10.0);
    }
}
