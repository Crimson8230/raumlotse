package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import at.mci.igp.raumlotse.domain.ConnectionType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteCalculatorTest {

    UUID building = UUID.randomUUID();
    TestNetwork net = new TestNetwork();

    RoutingGraph graph(boolean accessibleOnly) {
        return RoutingGraph.build(net.network(), net.infos, accessibleOnly);
    }

    @Test
    void picksTheShorterOfTwoWalkingPaths() {
        UUID m = net.addMap(building, "EG", 0, true);
        UUID a = net.wp(m, 0.1, 0.1);
        UUID b = net.wp(m, 0.5, 0.1);
        UUID c = net.wp(m, 0.9, 0.1);
        UUID d = net.wp(m, 0.5, 0.9);
        net.seg(m, a, b, 10);
        net.seg(m, b, c, 10);
        net.seg(m, a, d, 15);
        net.seg(m, d, c, 15);

        var path = RouteCalculator.shortest(graph(false), a, c).orElseThrow();

        assertThat(path.meters()).isEqualTo(20.0);
        assertThat(path.seconds()).isCloseTo(20.0 / RoutingDefaults.WALKING_SPEED_MPS, within(1e-9));
        assertThat(path.nodes()).containsExactly(a, b, c);
        assertThat(path.edges()).hasSize(2);
    }

    @Test
    void routeToItselfHasZeroDistanceAndOneNode() {
        UUID m = net.addMap(building, "EG", 0, true);
        UUID a = net.wp(m, 0.1, 0.1);

        var path = RouteCalculator.shortest(graph(false), a, a).orElseThrow();

        assertThat(path.meters()).isZero();
        assertThat(path.seconds()).isZero();
        assertThat(path.nodes()).containsExactly(a);
    }

    @Test
    void unreachableTargetGivesEmpty() {
        UUID m = net.addMap(building, "EG", 0, true);
        UUID a = net.wp(m, 0.1, 0.1);
        UUID b = net.wp(m, 0.9, 0.9);

        assertThat(RouteCalculator.shortest(graph(false), a, b)).isEmpty();
    }

    @Test
    void crossesFloorsViaTheConnectionAndAddsItsCost() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m1 = net.addMap(building, "1.OG", 1, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID lift0 = net.wp(m0, 0.5, 0.1);
        UUID lift1 = net.wp(m1, 0.5, 0.1);
        UUID b = net.wp(m1, 0.9, 0.1);
        net.seg(m0, a, lift0, 12);
        net.seg(m1, lift1, b, 6);
        net.connection("Lift", ConnectionType.ELEVATOR, new UUID[] {m0, lift0}, new UUID[] {m1, lift1});

        var path = RouteCalculator.shortest(graph(false), a, b).orElseThrow();

        assertThat(path.meters()).isEqualTo(18.0);
        assertThat(path.seconds()).isCloseTo(18.0 / RoutingDefaults.WALKING_SPEED_MPS
                + RoutingDefaults.ELEVATOR_SECONDS_PER_CHANGE, within(1e-9));
        assertThat(path.edges().stream().filter(e -> e.connection() != null)).hasSize(1);
    }

    @Test
    void choosesStairsOverAFarElevatorWhenFaster() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m1 = net.addMap(building, "1.OG", 1, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID stairs0 = net.wp(m0, 0.2, 0.1);
        UUID lift0 = net.wp(m0, 0.9, 0.1);
        UUID stairs1 = net.wp(m1, 0.2, 0.1);
        UUID lift1 = net.wp(m1, 0.9, 0.1);
        net.seg(m0, a, stairs0, 5);
        net.seg(m0, a, lift0, 60);
        net.seg(m1, stairs1, lift1, 55);
        net.connection("Treppe", ConnectionType.STAIRS, new UUID[] {m0, stairs0}, new UUID[] {m1, stairs1});
        net.connection("Lift", ConnectionType.ELEVATOR, new UUID[] {m0, lift0}, new UUID[] {m1, lift1});

        var path = RouteCalculator.shortest(graph(false), a, stairs1).orElseThrow();
        assertThat(path.edges().stream().filter(e -> e.connection() != null).findFirst().orElseThrow()
                .connection().type()).isEqualTo(ConnectionType.STAIRS);

        var accessible = RouteCalculator.shortest(graph(true), a, stairs1).orElseThrow();
        assertThat(accessible.edges().stream().filter(e -> e.connection() != null).findFirst().orElseThrow()
                .connection().type()).isEqualTo(ConnectionType.ELEVATOR);
        assertThat(accessible.meters()).isEqualTo(115.0);
    }

    @Test
    void accessibleOnlyWithoutElevatorHasNoPath() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m1 = net.addMap(building, "1.OG", 1, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID b = net.wp(m1, 0.1, 0.1);
        net.connection("Treppe", ConnectionType.STAIRS, new UUID[] {m0, a}, new UUID[] {m1, b});

        assertThat(RouteCalculator.shortest(graph(false), a, b)).isPresent();
        assertThat(RouteCalculator.shortest(graph(true), a, b)).isEmpty();
    }

    @Test
    void treeRestrictedToOneMapIgnoresConnectionsAndTracksFirstHop() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m1 = net.addMap(building, "1.OG", 1, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID b = net.wp(m0, 0.5, 0.1);
        UUID c = net.wp(m0, 0.9, 0.1);
        UUID up = net.wp(m1, 0.5, 0.1);
        net.seg(m0, a, b, 10);
        net.seg(m0, b, c, 7);
        net.connection("Lift", ConnectionType.ELEVATOR, new UUID[] {m0, b}, new UUID[] {m1, up});

        var tree = RouteCalculator.tree(graph(false), a, m0);

        assertThat(tree.meters(c)).isEqualTo(17.0);
        assertThat(tree.firstHop(c)).isEqualTo(b);
        assertThat(tree.reachable(up)).isFalse();
        assertThat(tree.reachable(a)).isTrue();
        assertThat(tree.meters(a)).isZero();
    }
}
