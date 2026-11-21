package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.service.NeighborCalculator.Direction;
import at.mci.igp.raumlotse.service.NeighborCalculator.Reason;
import at.mci.igp.raumlotse.service.NeighborCalculator.RoomInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Horizontal corridor at y = 0.5 on a 1000x1000 px map, 1 m = 0.01 relative. */
class NeighborCalculatorTest {

    UUID building = UUID.randomUUID();
    TestNetwork net = new TestNetwork();
    UUID map;
    Map<UUID, RoomInfo> rooms = new HashMap<>();

    @BeforeEach
    void setUp() {
        map = net.addMap(building, "EG", 0, true);
    }

    /** Attaches a new room at the waypoint, marker at the given relative position. */
    UUID room(String name, UUID waypoint, double markerX, double markerY) {
        UUID id = UUID.randomUUID();
        net.roomAt(id, map, waypoint);
        rooms.put(id, new RoomInfo(id, name, map, markerX, markerY, true));
        return id;
    }

    UUID corridorPoint(double x) {
        return net.wp(map, x, 0.5);
    }

    NeighborCalculator.Result calc(UUID room) {
        return NeighborCalculator.calculate(RoutingGraph.build(net.network(), net.infos, false), room, rooms);
    }

    @Test
    void roomNorthOfCorridorHasWestNeighborsOnTheLeftAndEastOnTheRight() {
        UUID w = corridorPoint(0.2);
        UUID a = corridorPoint(0.5);
        UUID e = corridorPoint(0.8);
        net.seg(map, w, a, 30);
        net.seg(map, a, e, 30);
        UUID self = room("A", a, 0.5, 0.4);
        room("West", w, 0.2, 0.4);
        room("East", e, 0.8, 0.4);

        var result = calc(self);

        assertThat(result.available()).isTrue();
        assertThat(result.entries()).extracting(NeighborCalculator.Entry::name, NeighborCalculator.Entry::direction)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("West", Direction.LEFT),
                        org.assertj.core.groups.Tuple.tuple("East", Direction.RIGHT));
        assertThat(result.entries().get(0).meters()).isEqualTo(30.0);
        assertThat(result.entries().get(0).seconds()).isEqualTo(25);
    }

    @Test
    void roomSouthOfCorridorMirrorsLeftAndRight() {
        UUID w = corridorPoint(0.2);
        UUID a = corridorPoint(0.5);
        UUID e = corridorPoint(0.8);
        net.seg(map, w, a, 30);
        net.seg(map, a, e, 30);
        UUID self = room("A", a, 0.5, 0.6);
        room("West", w, 0.2, 0.6);
        room("East", e, 0.8, 0.6);

        var byName = calc(self).entries().stream()
                .collect(java.util.stream.Collectors.toMap(NeighborCalculator.Entry::name, NeighborCalculator.Entry::direction));

        assertThat(byName).containsEntry("West", Direction.RIGHT).containsEntry("East", Direction.LEFT);
    }

    @Test
    void keepsOnlyThreeNearestPerSideOrderedByDistance() {
        UUID a = corridorPoint(0.1);
        UUID self = room("A", a, 0.1, 0.4);
        UUID previous = a;
        for (int i = 1; i <= 5; i++) {
            UUID next = corridorPoint(0.1 + i * 0.15);
            net.seg(map, previous, next, 10 * i);
            room("R" + i, next, 0.1 + i * 0.15, 0.4);
            previous = next;
        }

        var entries = calc(self).entries();

        assertThat(entries).extracting(NeighborCalculator.Entry::name).containsExactly("R1", "R2", "R3");
        assertThat(entries).extracting(NeighborCalculator.Entry::direction).containsOnly(Direction.RIGHT);
        assertThat(entries).extracting(NeighborCalculator.Entry::meters).isSorted();
    }

    @Test
    void roomsBeyondAJunctionAreIncludedWithTheSideOfTheirFirstStep() {
        UUID a = corridorPoint(0.5);
        UUID junction = corridorPoint(0.7);
        UUID branch = net.wp(map, 0.7, 0.2);
        net.seg(map, a, junction, 20);
        net.seg(map, junction, branch, 30);
        UUID self = room("A", a, 0.5, 0.4);
        room("Beyond", branch, 0.75, 0.2);

        var entries = calc(self).entries();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).name()).isEqualTo("Beyond");
        assertThat(entries.get(0).direction()).isEqualTo(Direction.RIGHT);
        assertThat(entries.get(0).meters()).isEqualTo(50.0);
    }

    @Test
    void firstStepAlongTheFacingDirectionIsStraight() {
        UUID a = corridorPoint(0.5);
        UUID north = net.wp(map, 0.5, 0.3);
        net.seg(map, a, north, 20);
        UUID self = room("A", a, 0.5, 0.4);
        room("Ahead", north, 0.4, 0.3);

        assertThat(calc(self).entries()).extracting(NeighborCalculator.Entry::direction)
                .containsExactly(Direction.STRAIGHT);
    }

    @Test
    void firstStepExactlyBehindIsAmbiguousAndGoesToTheRight() {
        UUID a = corridorPoint(0.5);
        UUID south = net.wp(map, 0.5, 0.7);
        net.seg(map, a, south, 20);
        UUID self = room("A", a, 0.5, 0.4);
        room("Behind", south, 0.4, 0.7);

        assertThat(calc(self).entries()).extracting(NeighborCalculator.Entry::direction)
                .containsExactly(Direction.RIGHT);
    }

    @Test
    void roomsAtTheSameWaypointAreClassifiedByTheirMarkers() {
        UUID a = corridorPoint(0.5);
        UUID self = room("A", a, 0.5, 0.4);
        room("EastDoor", a, 0.6, 0.4);
        room("WestDoor", a, 0.4, 0.4);

        var byName = calc(self).entries().stream()
                .collect(java.util.stream.Collectors.toMap(NeighborCalculator.Entry::name, NeighborCalculator.Entry::direction));

        assertThat(byName).containsEntry("EastDoor", Direction.RIGHT).containsEntry("WestDoor", Direction.LEFT);
        assertThat(calc(self).entries().get(0).meters()).isZero();
    }

    @Test
    void inactiveRoomsRoomsOnOtherFloorsAndOtherNetworkPartsAreNotNeighbors() {
        UUID a = corridorPoint(0.5);
        UUID near = corridorPoint(0.6);
        UUID island = corridorPoint(0.9);
        net.seg(map, a, near, 10);
        UUID self = room("A", a, 0.5, 0.4);
        UUID inactive = room("Inactive", near, 0.6, 0.4);
        rooms.put(inactive, new RoomInfo(inactive, "Inactive", map, 0.6, 0.4, false));
        room("Island", island, 0.9, 0.4);
        UUID otherMap = net.addMap(building, "1.OG", 1, true);
        UUID up = net.wp(otherMap, 0.5, 0.5);
        UUID upRoom = UUID.randomUUID();
        net.roomAt(upRoom, otherMap, up);
        rooms.put(upRoom, new RoomInfo(upRoom, "Upstairs", otherMap, 0.5, 0.4, true));

        var result = calc(self);

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.NO_NEIGHBORS);
    }

    @Test
    void roomWithoutAttachmentIsNotAvailable() {
        UUID id = UUID.randomUUID();
        rooms.put(id, new RoomInfo(id, "Loose", map, 0.5, 0.4, true));
        assertThat(calc(id).reason()).isEqualTo(Reason.ROOM_NOT_ATTACHED);
        assertThat(calc(UUID.randomUUID()).reason()).isEqualTo(Reason.ROOM_NOT_ATTACHED);
    }

    @Test
    void mapWithoutScaleIsNotAvailable() {
        UUID unscaled = net.addMap(building, "KG", -1, false);
        UUID w = net.wp(unscaled, 0.5, 0.5);
        UUID id = UUID.randomUUID();
        net.roomAt(id, unscaled, w);
        rooms.put(id, new RoomInfo(id, "Cellar", unscaled, 0.5, 0.4, true));
        assertThat(calc(id).reason()).isEqualTo(Reason.SCALE_MISSING);
    }

    @Test
    void markerOnTopOfItsWaypointHasNoOrientation() {
        UUID a = corridorPoint(0.5);
        UUID b = corridorPoint(0.7);
        net.seg(map, a, b, 20);
        UUID self = room("A", a, 0.5, 0.5);
        room("B", b, 0.7, 0.4);

        assertThat(calc(self).reason()).isEqualTo(Reason.NO_ORIENTATION);
    }

    @Test
    void aloneOnTheCorridorHasNoNeighbors() {
        UUID a = corridorPoint(0.5);
        UUID self = room("A", a, 0.5, 0.4);
        var result = calc(self);
        assertThat(result.available()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.NO_NEIGHBORS);
        assertThat(result.entries()).isEqualTo(List.of());
    }
}
