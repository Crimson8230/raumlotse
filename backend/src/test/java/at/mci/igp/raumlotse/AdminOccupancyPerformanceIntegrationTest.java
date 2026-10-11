package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.AdminOccupancyResponse;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.service.AdminOccupancyService;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.RoomService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Feature 016, SC-006: with 100 rooms and 500 running bookings the current snapshot and today's forecast are each
 * computed within 3 s. Measured at service level (queries, aggregation, mapping); HTTP and rendering are excluded.
 */
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminOccupancyPerformanceIntegrationTest extends AbstractIntegrationTest {
    private static final int BUILDINGS = 5;
    private static final int ROOMS = 100;
    private static final int BOOKINGS_PER_ROOM = 5;
    private static final Duration LIMIT = Duration.ofSeconds(3);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminOccupancyService service;

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private FloorService floorService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private ReservationService reservationService;

    private final List<UUID> bookingIds = new ArrayList<>();

    @BeforeAll
    void seed() {
        List<Floor> floors = new ArrayList<>();
        for (int b = 0; b < BUILDINGS; b++) {
            var building = buildingService.create("Perf occupancy " + b + " " + UUID.randomUUID());
            floors.add(floorService.create(building.getId(), "EG"));
            floors.add(floorService.create(building.getId(), "1. OG"));
        }
        Instant firstSlot = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS);
        for (int r = 0; r < ROOMS; r++) {
            Room room = roomService.create(new RoomCreateRequest("Perf " + r, floors.get(r % floors.size()).getId(),
                    List.of(new SeatingArrangementRequest("Theater", 200)), List.of()));
            for (int i = 0; i < BOOKINGS_PER_ROOM; i++) {
                Instant start = firstSlot.plus(i, ChronoUnit.DAYS);
                bookingIds.add(TestActors.create(jdbc, reservationService, room.getId(), new ReservationCreateRequest(
                        start, start.plus(1, ChronoUnit.HOURS), room.getSeatingArrangements().get(0).getId(),
                        10, List.of(), null, "Perf")).id());
            }
        }
        // Several bookings per room may overlap here; only the creation path checks overlaps, and the aggregation
        // must cope with whatever is stored.
        Instant now = Instant.now();
        for (int i = 0; i < bookingIds.size(); i++) {
            boolean checkedIn = i % 2 == 0;
            jdbc.update("update reservation set start_time = ?, end_time = ?, status = ?, checked_in_at = ? where id = ?",
                    Timestamp.from(now.minus(30, ChronoUnit.MINUTES)), Timestamp.from(now.plus(90, ChronoUnit.MINUTES)),
                    checkedIn ? "ACTIVE" : "RESERVED", checkedIn ? Timestamp.from(now.minus(30, ChronoUnit.MINUTES)) : null,
                    bookingIds.get(i));
        }
    }

    @Test
    void snapshotAndForecastStayWithinThreeSeconds() {
        service.snapshot(null);
        service.forecast(LocalDate.now(ZoneId.of("Europe/Berlin")), null);

        long started = System.nanoTime();
        AdminOccupancyResponse.Snapshot snapshot = service.snapshot(null);
        Duration snapshotTime = Duration.ofNanos(System.nanoTime() - started);

        started = System.nanoTime();
        AdminOccupancyResponse.Forecast forecast = service.forecast(LocalDate.now(ZoneId.of("Europe/Berlin")), null);
        Duration forecastTime = Duration.ofNanos(System.nanoTime() - started);

        assertThat(snapshot.runningBookings()).hasSizeGreaterThanOrEqualTo(ROOMS * BOOKINGS_PER_ROOM);
        assertThat(forecast.hours()).isNotEmpty();
        assertThat(snapshotTime).as("snapshot").isLessThan(LIMIT);
        assertThat(forecastTime).as("forecast").isLessThan(LIMIT);
    }
}
