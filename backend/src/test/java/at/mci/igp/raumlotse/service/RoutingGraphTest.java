package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.ConnectionType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoutingGraphTest {

    UUID building = UUID.randomUUID();
    TestNetwork net = new TestNetwork();

    @Test
    void segmentsBecomeUndirectedEdgesInSecondsAndMeters() {
        UUID m = net.addMap(building, "EG", 0, true);
        UUID a = net.wp(m, 0.1, 0.1);
        UUID b = net.wp(m, 0.2, 0.1);
        net.seg(m, a, b, 12.0);
        RoutingGraph g = RoutingGraph.build(net.network(), net.infos, false);

        var fromA = g.edgesFrom(a);
        var fromB = g.edgesFrom(b);
        assertThat(fromA).hasSize(1);
        assertThat(fromA.get(0).to()).isEqualTo(b);
        assertThat(fromA.get(0).meters()).isEqualTo(12.0);
        assertThat(fromA.get(0).seconds()).isEqualTo(12.0 / RoutingDefaults.WALKING_SPEED_MPS);
        assertThat(fromB.get(0).to()).isEqualTo(a);
        assertThat(fromA.get(0).connection()).isNull();
    }

    @Test
    void mapsWithoutScaleContributeNoWalkingEdges() {
        UUID m = net.addMap(building, "EG", 0, false);
        UUID a = net.wp(m, 0.1, 0.1);
        UUID b = net.wp(m, 0.2, 0.1);
        net.seg(m, a, b, 12.0);
        RoutingGraph g = RoutingGraph.build(net.network(), net.infos, false);

        assertThat(g.edgesFrom(a)).isEmpty();
        assertThat(g.scaleAvailable(m)).isFalse();
    }

    @Test
    void stairsCostFifteenSecondsPerFloorStep() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m2 = net.addMap(building, "2.OG", 2, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID b = net.wp(m2, 0.1, 0.1);
        UUID stairs = net.connection("Treppe", ConnectionType.STAIRS, new UUID[] {m0, a}, new UUID[] {m2, b});
        RoutingGraph g = RoutingGraph.build(net.network(), net.infos, false);

        var edge = g.edgesFrom(a).get(0);
        assertThat(edge.to()).isEqualTo(b);
        assertThat(edge.seconds()).isEqualTo(2 * RoutingDefaults.STAIRS_SECONDS_PER_FLOOR);
        assertThat(edge.meters()).isZero();
        assertThat(edge.connection().connectionId()).isEqualTo(stairs);
        assertThat(edge.connection().type()).isEqualTo(ConnectionType.STAIRS);
        assertThat(g.edgesFrom(b).get(0).to()).isEqualTo(a);
    }

    @Test
    void elevatorCostsThirtySecondsPerChangeRegardlessOfFloors() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m3 = net.addMap(building, "3.OG", 3, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID b = net.wp(m3, 0.1, 0.1);
        net.connection("Lift", ConnectionType.ELEVATOR, new UUID[] {m0, a}, new UUID[] {m3, b});
        RoutingGraph g = RoutingGraph.build(net.network(), net.infos, false);

        assertThat(g.edgesFrom(a).get(0).seconds()).isEqualTo(RoutingDefaults.ELEVATOR_SECONDS_PER_CHANGE);
    }

    @Test
    void connectionsThatAreNotFullyAttachedAreSkipped() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m1 = net.addMap(building, "1.OG", 1, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        net.connection("Lift", ConnectionType.ELEVATOR, new UUID[] {m0, a}, new UUID[] {m1, null});
        RoutingGraph g = RoutingGraph.build(net.network(), net.infos, false);

        assertThat(g.edgesFrom(a)).isEmpty();
        assertThat(g.connections()).isEmpty();
    }

    @Test
    void connectionsBetweenDifferentBuildingsAreIgnored() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID other = net.addMap(UUID.randomUUID(), "EG", 0, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID b = net.wp(other, 0.1, 0.1);
        net.connection("Brücke", ConnectionType.STAIRS, new UUID[] {m0, a}, new UUID[] {other, b});
        RoutingGraph g = RoutingGraph.build(net.network(), net.infos, false);

        assertThat(g.edgesFrom(a)).isEmpty();
        assertThat(g.connections()).isEmpty();
    }

    @Test
    void accessibleOnlyDropsStairsButKeepsElevators() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m1 = net.addMap(building, "1.OG", 1, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID b = net.wp(m1, 0.1, 0.1);
        UUID c = net.wp(m0, 0.3, 0.1);
        UUID d = net.wp(m1, 0.3, 0.1);
        net.connection("Treppe", ConnectionType.STAIRS, new UUID[] {m0, a}, new UUID[] {m1, b});
        net.connection("Lift", ConnectionType.ELEVATOR, new UUID[] {m0, c}, new UUID[] {m1, d});
        RoutingGraph g = RoutingGraph.build(net.network(), net.infos, true);

        assertThat(g.edgesFrom(a)).isEmpty();
        assertThat(g.edgesFrom(c)).hasSize(1);
        assertThat(g.connections()).hasSize(1);
    }

    @Test
    void roomEntranceAndMapLookup() {
        UUID m = net.addMap(building, "EG", 0, true);
        UUID a = net.wp(m, 0.1, 0.1);
        UUID room = UUID.randomUUID();
        net.roomAt(room, m, a);
        RoutingGraph g = RoutingGraph.build(net.network(), net.infos, false);

        assertThat(g.roomWaypoint(room)).contains(a);
        assertThat(g.roomMap(room)).contains(m);
        assertThat(g.roomWaypoint(UUID.randomUUID())).isEmpty();
    }
}
