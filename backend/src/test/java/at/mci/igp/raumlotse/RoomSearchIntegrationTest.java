package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomSearchCriteria;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.RoomResponse;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.EquipmentTypeService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.RoomSearchService;
import at.mci.igp.raumlotse.service.RoomService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

/**
 * End-to-end search against PostgreSQL. The database is shared with other integration test classes, so every
 * assertion is restricted to the rooms created here.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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

    private Building haus1;
    private Building haus2;
    private Room a40;
    private Room b15;
    private Room c120;
    private Room deactivatedRoom;
    private Room roomOnDeactivatedFloor;
    private Room roomInDeactivatedBuilding;
    private Set<UUID> ownRoomIds;

    private Room createRoom(Floor floor, String name, int capacity) {
        return roomService.create(new RoomCreateRequest(
                name, floor.getId(), List.of(new SeatingArrangementRequest("Theater", capacity)), List.of()));
    }

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString();
        haus1 = buildingService.create("Haus 1 " + suffix);
        haus2 = buildingService.create("Haus 2 " + suffix);
        Building closedBuilding = buildingService.create("Geschlossen " + suffix);
        Floor haus1Eg = floorService.create(haus1.getId(), "EG");
        Floor haus1Og = floorService.create(haus1.getId(), "1. OG");
        Floor haus2Eg = floorService.create(haus2.getId(), "EG");
        Floor closedFloor = floorService.create(closedBuilding.getId(), "EG");

        a40 = createRoom(haus1Eg, "A", 40);
        c120 = createRoom(haus1Og, "C", 120);
        b15 = createRoom(haus2Eg, "B", 15);
        deactivatedRoom = createRoom(haus2Eg, "Deaktiviert", 40);
        roomService.deactivate(deactivatedRoom.getId());
        Floor haus1Ug = floorService.create(haus1.getId(), "UG");
        roomOnDeactivatedFloor = createRoom(haus1Ug, "Stockwerk zu", 40);
        floorService.deactivate(haus1Ug.getId());
        roomInDeactivatedBuilding = createRoom(closedFloor, "Gebäude zu", 40);
        buildingService.deactivate(closedBuilding.getId());

        ownRoomIds = Set.of(a40.getId(), b15.getId(), c120.getId(), deactivatedRoom.getId(),
                roomOnDeactivatedFloor.getId(), roomInDeactivatedBuilding.getId());
    }

    private List<UUID> ownResultIds(RoomSearchCriteria criteria) {
        return roomSearchService.search(criteria).stream()
                .map(RoomResponse::id)
                .filter(ownRoomIds::contains)
                .toList();
    }

    @Test
    void unfilteredSearchReturnsOnlyFullyActiveRoomsInSortedOrder() {
        assertThat(ownResultIds(new RoomSearchCriteria(null, null, null)))
                .containsExactly(a40.getId(), c120.getId(), b15.getId());
    }

    @Test
    void minPersonsExcludesSmallRooms() {
        assertThat(ownResultIds(new RoomSearchCriteria(20, null, null)))
                .containsExactlyInAnyOrder(a40.getId(), c120.getId());
    }

    @Test
    void maxPersonsExcludesLargeRooms() {
        assertThat(ownResultIds(new RoomSearchCriteria(null, 50, null)))
                .containsExactlyInAnyOrder(a40.getId(), b15.getId());
    }

    @Test
    void buildingRestrictsToThatBuilding() {
        List<RoomResponse> result = roomSearchService.search(new RoomSearchCriteria(null, null, haus2.getId()));

        assertThat(result).extracting(RoomResponse::id).containsExactly(b15.getId());
    }

    @Test
    void filtersNothingMatchesReturnEmptyResult() {
        List<RoomResponse> result = roomSearchService.search(new RoomSearchCriteria(1000, null, haus1.getId()));

        assertThat(result).isEmpty();
    }

    @Test
    void resultsCarryBuildingAndFloorForDisplay() {
        RoomResponse a = roomSearchService.search(new RoomSearchCriteria(null, null, haus1.getId())).stream()
                .filter(r -> r.id().equals(a40.getId()))
                .findFirst().orElseThrow();

        assertThat(a.building().name()).isEqualTo(haus1.getName());
        assertThat(a.floor().name()).isEqualTo("EG");
        assertThat(a.seatingArrangements()).extracting(s -> s.maxCapacity())
                .containsExactly(40);
    }

    /** Quickstart rooms A-D (quickstart.md §3) in their own buildings, with equipment. */
    private record QuickstartRooms(Room a, Room b, Room c, Room d, EquipmentType projector, EquipmentType whiteboard,
            Set<UUID> ids) {
    }

    private QuickstartRooms seedQuickstartRooms() {
        String suffix = UUID.randomUUID().toString();
        EquipmentType projector = equipmentTypeService.create("Projector " + suffix);
        EquipmentType whiteboard = equipmentTypeService.create("Whiteboard " + suffix);
        Building h1 = buildingService.create("QS Haus 1 " + suffix);
        Building h2 = buildingService.create("QS Haus 2 " + suffix);
        Floor h1Og = floorService.create(h1.getId(), "1. OG");
        Floor h1Eg = floorService.create(h1.getId(), "EG");
        Floor h2Og = floorService.create(h2.getId(), "1. OG");
        Floor h2Eg = floorService.create(h2.getId(), "EG");
        Room a = roomService.create(new RoomCreateRequest("A", h1Og.getId(),
                List.of(new SeatingArrangementRequest("Theater", 60), new SeatingArrangementRequest("U-Shape", 20)),
                List.of(projector.getId(), whiteboard.getId())));
        Room b = roomService.create(new RoomCreateRequest("B", h2Og.getId(),
                List.of(new SeatingArrangementRequest("Classroom", 15)), List.of(projector.getId())));
        Room c = roomService.create(new RoomCreateRequest("C", h1Eg.getId(),
                List.of(new SeatingArrangementRequest("Classroom", 120)), List.of(whiteboard.getId())));
        Room d = roomService.create(new RoomCreateRequest("D", h2Eg.getId(),
                List.of(new SeatingArrangementRequest("U-Shape", 30)), List.of()));
        return new QuickstartRooms(a, b, c, d, projector, whiteboard, Set.of(a.getId(), b.getId(), c.getId(), d.getId()));
    }

    private List<UUID> resultIds(RoomSearchCriteria criteria, Set<UUID> own) {
        return roomSearchService.search(criteria).stream().map(RoomResponse::id).filter(own::contains).toList();
    }

    @Test
    void seatingArrangementFilterIsCaseInsensitive() {
        QuickstartRooms qs = seedQuickstartRooms();

        assertThat(resultIds(new RoomSearchCriteria(null, null, null, "u-shape", Set.of()), qs.ids()))
                .containsExactlyInAnyOrder(qs.a().getId(), qs.d().getId());
    }

    @Test
    void seatingArrangementAndPersonsMustFitTheSameArrangement() {
        QuickstartRooms qs = seedQuickstartRooms();

        assertThat(resultIds(new RoomSearchCriteria(30, null, null, "U-Shape", Set.of()), qs.ids()))
                .containsExactly(qs.d().getId());
    }

    @Test
    void equipmentFilterRequiresAllSelectedTypes() {
        QuickstartRooms qs = seedQuickstartRooms();

        assertThat(resultIds(new RoomSearchCriteria(null, null, null, null,
                Set.of(qs.projector().getId(), qs.whiteboard().getId())), qs.ids()))
                .containsExactly(qs.a().getId());
    }

    @Test
    void roomWithDeactivatedEquipmentStaysVisibleWithoutEquipmentFilter() {
        QuickstartRooms qs = seedQuickstartRooms();
        equipmentTypeService.deactivate(qs.whiteboard().getId());

        assertThat(resultIds(new RoomSearchCriteria(null, null, null), qs.ids()))
                .containsExactlyInAnyOrder(qs.a().getId(), qs.b().getId(), qs.c().getId(), qs.d().getId());
    }

    @Test
    void seatingArrangementNamesIncludeNamesOfActiveRooms() {
        seedQuickstartRooms();

        assertThat(roomSearchService.listSeatingArrangementNames())
                .contains("Theater", "U-Shape", "Classroom")
                .doesNotHaveDuplicates();
    }

    @Test
    void barrierFreeFilterUsesElevatorGroundFloorAndRoomExclusion() {
        String suffix = UUID.randomUUID().toString();
        Building withElevator = buildingService.create("BF Haus 1 " + suffix, true);
        Building withoutElevator = buildingService.create("BF Haus 2 " + suffix, false);
        Floor h1Og = floorService.create(withElevator.getId(), "1. OG", false);
        Floor h1Eg = floorService.create(withElevator.getId(), "EG", true);
        Floor h2Og = floorService.create(withoutElevator.getId(), "1. OG", false);
        Floor h2Eg = floorService.create(withoutElevator.getId(), "EG", true);
        Floor h2EgNord = floorService.create(withoutElevator.getId(), "EG Nord", true);
        Room a = createRoom(h1Og, "A", 60);
        Room b = createRoom(h2Og, "B", 15);
        Room c = roomService.create(new RoomCreateRequest("C", h1Eg.getId(),
                List.of(new SeatingArrangementRequest("Classroom", 120)), List.of(), true));
        Room d = createRoom(h2Eg, "D", 30);
        Room e = createRoom(h2EgNord, "E", 20);
        Set<UUID> own = Set.of(a.getId(), b.getId(), c.getId(), d.getId(), e.getId());
        RoomSearchCriteria barrierFree = new RoomSearchCriteria(null, null, null, null, Set.of(), true);

        assertThat(resultIds(barrierFree, own))
                .containsExactlyInAnyOrder(a.getId(), d.getId(), e.getId());

        buildingService.update(withElevator.getId(), withElevator.getName(), false);

        assertThat(resultIds(barrierFree, own)).containsExactlyInAnyOrder(d.getId(), e.getId());
    }

    @Test
    void timeWindowExcludesBookedRoomsUntilTheBookingIsCancelled() {
        java.time.Instant ten = java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS)
                .truncatedTo(java.time.temporal.ChronoUnit.DAYS).plus(10, java.time.temporal.ChronoUnit.HOURS);
        java.time.Instant eleven = ten.plusSeconds(3600);
        java.time.Instant twelve = ten.plusSeconds(7200);
        java.time.Instant thirteen = ten.plusSeconds(10800);
        var booking = TestActors.create(jdbc, reservationService, a40.getId(), new at.mci.igp.raumlotse.dto.ReservationCreateRequest(
                ten, twelve, a40.getSeatingArrangements().get(0).getId(), 10, List.of(), null, "Test"));

        assertThat(ownResultIds(window(eleven, thirteen))).containsExactly(c120.getId(), b15.getId());
        assertThat(ownResultIds(window(twelve, thirteen))).containsExactly(a40.getId(), c120.getId(), b15.getId());

        reservationService.cancelReservation(booking.id(), TestActors.ADMIN);

        assertThat(ownResultIds(window(eleven, thirteen))).containsExactly(a40.getId(), c120.getId(), b15.getId());
    }

    private static RoomSearchCriteria window(java.time.Instant from, java.time.Instant to) {
        return new RoomSearchCriteria(null, null, null, null, Set.of(), false, from, to);
    }
}
