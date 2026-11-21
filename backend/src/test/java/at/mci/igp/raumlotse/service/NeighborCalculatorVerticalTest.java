package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.ConnectionType;
import at.mci.igp.raumlotse.service.NeighborCalculator.Direction;
import at.mci.igp.raumlotse.service.NeighborCalculator.Entry;
import at.mci.igp.raumlotse.service.NeighborCalculator.Kind;
import at.mci.igp.raumlotse.service.NeighborCalculator.RoomInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NeighborCalculatorVerticalTest {

    UUID building = UUID.randomUUID();
    TestNetwork net = new TestNetwork();
    Map<UUID, RoomInfo> rooms = new HashMap<>();
    UUID cellar;
    UUID ground;
    UUID upper;
    UUID top;
    UUID self;
    UUID roomWp;
    UUID stairsWp;
    UUID liftWp;

    @BeforeEach
    void setUp() {
        cellar = net.addMap(building, "KG", 0, true);
        ground = net.addMap(building, "EG", 1, true);
        upper = net.addMap(building, "1. OG", 2, true);
        top = net.addMap(building, "2. OG", 3, true);
        roomWp = net.wp(ground, 0.5, 0.5);
        stairsWp = net.wp(ground, 0.7, 0.5);
        liftWp = net.wp(ground, 0.2, 0.5);
        net.seg(ground, roomWp, stairsWp, 20);
        net.seg(ground, roomWp, liftWp, 35);
        self = UUID.randomUUID();
        net.roomAt(self, ground, roomWp);
        rooms.put(self, new RoomInfo(self, "A", ground, 0.5, 0.4, true));
    }

    UUID[] at(UUID map) {
        return new UUID[] {map, net.wp(map, 0.5, 0.5)};
    }

    java.util.List<Entry> vertical() {
        return NeighborCalculator.calculate(RoutingGraph.build(net.network(), net.infos, false), self, rooms)
                .entries().stream().filter(e -> e.kind() != Kind.ROOM).toList();
    }

    @Test
    void listsNearestStairsAndNearestElevatorSeparatelyPerDirection() {
        net.connection("Treppe A", ConnectionType.STAIRS, new UUID[] {ground, stairsWp}, at(upper), at(top));
        net.connection("Aufzug 1", ConnectionType.ELEVATOR, new UUID[] {ground, liftWp}, at(cellar), at(upper));

        var entries = vertical();

        assertThat(entries).extracting(Entry::direction, Entry::kind, Entry::name, Entry::targetFloorName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Direction.UP, Kind.STAIRS, "Treppe A", "1. OG"),
                        org.assertj.core.groups.Tuple.tuple(Direction.UP, Kind.ELEVATOR, "Aufzug 1", "1. OG"),
                        org.assertj.core.groups.Tuple.tuple(Direction.DOWN, Kind.ELEVATOR, "Aufzug 1", "KG"));
        assertThat(entries.get(0).meters()).isEqualTo(20.0);
        assertThat(entries.get(1).meters()).isEqualTo(35.0);
        assertThat(entries.get(0).seconds()).isEqualTo(17);
    }

    @Test
    void picksTheNearerOfTwoStairs() {
        UUID farWp = net.wp(ground, 0.9, 0.5);
        net.seg(ground, stairsWp, farWp, 40);
        net.connection("Treppe far", ConnectionType.STAIRS, new UUID[] {ground, farWp}, at(upper));
        net.connection("Treppe near", ConnectionType.STAIRS, new UUID[] {ground, stairsWp}, at(upper));

        assertThat(vertical()).extracting(Entry::name).containsExactly("Treppe near");
    }

    @Test
    void connectionsNotFullyAttachedOrAcrossBuildingsOrUnreachableAreNotListed() {
        UUID otherBuildingMap = net.addMap(UUID.randomUUID(), "EG", 0, true);
        net.connection("Halb", ConnectionType.ELEVATOR, new UUID[] {ground, liftWp}, new UUID[] {upper, null});
        net.connection("Brücke", ConnectionType.STAIRS, new UUID[] {ground, stairsWp}, at(otherBuildingMap));
        UUID island = net.wp(ground, 0.95, 0.95);
        net.connection("Insel", ConnectionType.ELEVATOR, new UUID[] {ground, island}, at(upper));

        assertThat(vertical()).isEmpty();
    }

    @Test
    void connectionToTheSameFloorOnlyIsNotListed() {
        net.connection("Lift nur hier", ConnectionType.ELEVATOR, new UUID[] {ground, liftWp});
        assertThat(vertical()).isEmpty();
    }
}
