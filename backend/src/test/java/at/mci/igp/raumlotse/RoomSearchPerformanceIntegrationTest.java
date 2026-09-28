package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomSearchCriteria;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.RoomResponse;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.EquipmentTypeService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.RoomSearchService;
import at.mci.igp.raumlotse.service.RoomService;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * SC-003: with 500 rooms, searches finish within 2 s (p95), and a search does not load rooms one by one (N+1).
 * The search is measured at service level (query, filtering, sorting, mapping); HTTP and JSON are excluded.
 * The database is shared with other integration tests, so the statement bound is derived from the actual number
 * of candidate rooms: 1 candidate query + 1 occupancy query + ceil(N / 100) batch loads for each of the two
 * collections, plus a reserve of 3. For exactly 500 rooms that is 15.
 */
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomSearchPerformanceIntegrationTest extends AbstractIntegrationTest {

    private static final int ROOMS = 500;
    private static final int BUILDINGS = 10;
    private static final int RESERVATIONS = 200;
    private static final int BATCH_SIZE = 100;

    @Autowired
    private RoomSearchService roomSearchService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private FloorService floorService;

    @Autowired
    private EquipmentTypeService equipmentTypeService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<Building> buildings = new ArrayList<>();
    private EquipmentType projector;
    private Instant windowStart;

    @BeforeAll
    void seed() {
        String suffix = UUID.randomUUID().toString();
        projector = equipmentTypeService.create("Perf Projector " + suffix);
        EquipmentType whiteboard = equipmentTypeService.create("Perf Whiteboard " + suffix);
        windowStart = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS);

        List<Room> rooms = new ArrayList<>();
        for (int b = 0; b < BUILDINGS; b++) {
            Building building = buildingService.create("Perf Haus " + b + " " + suffix, b % 2 == 0);
            buildings.add(building);
            Floor ground = floorService.create(building.getId(), "EG", true);
            Floor upper = floorService.create(building.getId(), "1. OG", false);
            for (int r = 0; r < ROOMS / BUILDINGS; r++) {
                rooms.add(roomService.create(new RoomCreateRequest("Raum " + r, (r % 2 == 0 ? ground : upper).getId(),
                        List.of(new SeatingArrangementRequest("Theater", 20 + r),
                                new SeatingArrangementRequest("U-Shape", 10 + r % 20),
                                new SeatingArrangementRequest("Classroom", 15 + r % 30)),
                        List.of(projector.getId(), whiteboard.getId()))));
            }
        }
        for (int i = 0; i < RESERVATIONS; i++) {
            Room room = rooms.get(i * (ROOMS / RESERVATIONS));
            reservationService.createReservation(room.getId(), new ReservationCreateRequest(
                    windowStart.plus(i % 8, ChronoUnit.HOURS), windowStart.plus(i % 8 + 1, ChronoUnit.HOURS),
                    room.getSeatingArrangements().get(0).getId(), 10, List.of(), null, "Perf"));
        }
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private RoomSearchCriteria windowOnly() {
        return new RoomSearchCriteria(null, null, null, null, Set.of(), false,
                windowStart, windowStart.plus(1, ChronoUnit.HOURS));
    }

    private List<RoomSearchCriteria> mixedSearches() {
        Instant from = windowStart.plus(2, ChronoUnit.HOURS);
        return List.of(
                windowOnly(),
                new RoomSearchCriteria(30, 60, null),
                new RoomSearchCriteria(null, null, buildings.get(3).getId()),
                new RoomSearchCriteria(null, null, null, "u-shape", Set.of(projector.getId()), false, null, null),
                new RoomSearchCriteria(20, null, null, null, Set.of(), true, from, from.plus(2, ChronoUnit.HOURS)));
    }

    @Test
    void searchDoesNotLoadRoomsOneByOne() {
        roomSearchService.search(windowOnly());
        Statistics statistics = statistics();
        statistics.clear();

        List<RoomResponse> result = roomSearchService.search(windowOnly());
        // Read before any further search, so the count covers exactly the measured search.
        long statements = statistics.getPrepareStatementCount();

        long candidates = roomSearchService.search(new RoomSearchCriteria(null, null, null)).size();
        long batchesPerCollection = (candidates + BATCH_SIZE - 1) / BATCH_SIZE;
        long allowed = 2 + 2 * batchesPerCollection + 3;
        assertThat(candidates).isGreaterThanOrEqualTo(ROOMS);
        assertThat(result).isNotEmpty();
        assertThat(statements)
                .as("SQL statements for one search over %d candidate rooms", candidates)
                .isLessThanOrEqualTo(allowed);
    }

    @Test
    void ninetyFifthPercentileStaysBelowTwoSeconds() {
        List<RoomSearchCriteria> searches = mixedSearches();
        searches.forEach(roomSearchService::search);

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long started = System.nanoTime();
            roomSearchService.search(searches.get(i % searches.size()));
            durations.add((System.nanoTime() - started) / 1_000_000);
        }
        durations.sort(null);
        long p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);

        assertThat(p95).as("p95 search duration in ms, all: %s", durations).isLessThan(2000);
    }
}
