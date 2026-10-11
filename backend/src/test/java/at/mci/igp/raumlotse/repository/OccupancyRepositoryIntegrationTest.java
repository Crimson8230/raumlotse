package at.mci.igp.raumlotse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.AbstractIntegrationTest;
import at.mci.igp.raumlotse.TestActors;
import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.RoomService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** Feature 016: covering and overlap queries for the occupancy overview. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OccupancyRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OccupancyRepository repository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private FloorService floorService;

    private Building buildingA;
    private Building buildingB;
    private Floor floorA;
    private Room roomA;
    private Room roomB;
    /** A fixed reference point far in the future so other tests' data never interferes. */
    private Instant now;
    private int creationSlot;

    @BeforeEach
    void seed() {
        buildingA = buildingService.create("Occupancy A " + UUID.randomUUID());
        buildingB = buildingService.create("Occupancy B " + UUID.randomUUID());
        floorA = floorService.create(buildingA.getId(), "1. OG");
        Floor floorB = floorService.create(buildingB.getId(), "EG");
        roomA = room("A-101", floorA);
        roomB = room("B-001", floorB);
        now = Instant.now().plus(400 + (long) (Math.random() * 1000), ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    private Room room(String name, Floor floor) {
        return roomService.create(new RoomCreateRequest(name, floor.getId(),
                List.of(new SeatingArrangementRequest("Theater", 50)), List.of()));
    }

    /** Creates a reservation through the service and then moves it to the given times and state via SQL. */
    private UUID booking(Room room, Instant start, Instant end, ReservationStatus status, int attendees, Instant checkedInAt) {
        // Distinct creation slots avoid the overlap check; the real times are set afterwards.
        Instant createStart = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(8, ChronoUnit.HOURS)
                .plus(creationSlot++, ChronoUnit.DAYS);
        var created = TestActors.create(jdbc, reservationService, room.getId(), new ReservationCreateRequest(
                createStart, createStart.plus(1, ChronoUnit.HOURS), room.getSeatingArrangements().get(0).getId(),
                attendees, List.of(), null, "Test"));
        jdbc.update("update reservation set start_time = ?, end_time = ?, status = ?, checked_in_at = ? where id = ?",
                Timestamp.from(start), Timestamp.from(end), status.name(),
                checkedInAt == null ? null : Timestamp.from(checkedInAt), created.id());
        return created.id();
    }

    private Instant at(int minutesFromNow) {
        return now.plus(minutesFromNow, ChronoUnit.MINUTES);
    }

    private List<UUID> ids(List<OccupancyRepository.BookingRow> rows) {
        return rows.stream().map(OccupancyRepository.BookingRow::reservationId).toList();
    }

    @Test
    void findCoveringReturnsStartedReservedAndCheckedInBookings() {
        UUID reserved = booking(roomA, at(-30), at(30), ReservationStatus.RESERVED, 6, null);
        UUID early = booking(roomB, at(5), at(60), ReservationStatus.ACTIVE, 10, at(-5));
        UUID notStarted = booking(roomA, at(10), at(60), ReservationStatus.RESERVED, 3, null);
        UUID endedUnswept = booking(roomB, at(-60), at(0), ReservationStatus.ACTIVE, 4, at(-60));
        UUID cancelled = booking(roomA, at(-30), at(30), ReservationStatus.CANCELLED, 9, null);
        UUID expired = booking(roomA, at(-30), at(30), ReservationStatus.EXPIRED, 9, null);
        UUID completed = booking(roomA, at(-30), at(30), ReservationStatus.COMPLETED, 9, at(-30));

        List<UUID> covering = ids(repository.findCovering(now, null));

        assertThat(covering).contains(reserved, early)
                .doesNotContain(notStarted, endedUnswept, cancelled, expired, completed);
    }

    @Test
    void findCoveringFiltersByBuildingAndFillsRowFields() {
        UUID inA = booking(roomA, at(-30), at(30), ReservationStatus.ACTIVE, 8, at(-31));
        UUID inB = booking(roomB, at(-30), at(30), ReservationStatus.ACTIVE, 6, at(-30));

        List<OccupancyRepository.BookingRow> rows = repository.findCovering(now, buildingA.getId());

        assertThat(ids(rows)).contains(inA).doesNotContain(inB);
        OccupancyRepository.BookingRow row = rows.stream().filter(r -> r.reservationId().equals(inA)).findFirst().orElseThrow();
        assertThat(row.roomId()).isEqualTo(roomA.getId());
        assertThat(row.roomName()).isEqualTo("A-101");
        assertThat(row.buildingId()).isEqualTo(buildingA.getId());
        assertThat(row.buildingName()).isEqualTo(buildingA.getName());
        assertThat(row.buildingStatus()).isEqualTo(EntityStatus.ACTIVE);
        assertThat(row.floorId()).isEqualTo(floorA.getId());
        assertThat(row.floorName()).isEqualTo("1. OG");
        assertThat(row.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(row.expectedAttendees()).isEqualTo(8);
        assertThat(row.checkedInAt()).isEqualTo(at(-31));
        assertThat(row.effectiveStart()).isEqualTo(at(-31));
        assertThat(row.startTime()).isEqualTo(at(-30));
        assertThat(row.endTime()).isEqualTo(at(30));
    }

    @Test
    void findOverlappingUsesHalfOpenEffectiveIntervalsAndValidStatusesOnly() {
        Instant from = at(0);
        Instant to = at(60);
        UUID touchingBefore = booking(roomA, at(-60), at(0), ReservationStatus.RESERVED, 1, null);
        UUID touchingAfter = booking(roomA, at(60), at(120), ReservationStatus.RESERVED, 1, null);
        UUID inside = booking(roomA, at(10), at(20), ReservationStatus.RESERVED, 1, null);
        UUID earlyCheckIn = booking(roomB, at(65), at(120), ReservationStatus.ACTIVE, 1, at(55));
        UUID cancelled = booking(roomB, at(10), at(20), ReservationStatus.CANCELLED, 1, null);
        UUID otherBuilding = booking(roomB, at(10), at(20), ReservationStatus.RESERVED, 1, null);

        assertThat(ids(repository.findOverlapping(from, to, null)))
                .contains(inside, earlyCheckIn, otherBuilding)
                .doesNotContain(touchingBefore, touchingAfter, cancelled);
        assertThat(ids(repository.findOverlapping(from, to, buildingA.getId())))
                .contains(inside).doesNotContain(earlyCheckIn, otherBuilding);
    }

    @Test
    void findBuildingsAndBuildingExists() {
        assertThat(repository.findBuildings())
                .extracting(OccupancyRepository.BuildingRow::id)
                .contains(buildingA.getId(), buildingB.getId());
        assertThat(repository.buildingExists(buildingA.getId())).isTrue();
        assertThat(repository.buildingExists(UUID.randomUUID())).isFalse();
    }
}
