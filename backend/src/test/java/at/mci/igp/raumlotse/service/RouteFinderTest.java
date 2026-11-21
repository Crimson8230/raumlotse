package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.ConnectionType;
import at.mci.igp.raumlotse.service.RouteFinder.Problem;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteFinderTest {

    UUID building = UUID.randomUUID();
    TestNetwork net = new TestNetwork();

    RouteFinder.Result find(UUID from, UUID to, boolean accessibleOnly) {
        return RouteFinder.find(net.network(), net.infos, from, to, accessibleOnly);
    }

    UUID roomAt(UUID map, UUID wp) {
        UUID room = UUID.randomUUID();
        net.roomAt(room, map, wp);
        return room;
    }

    @Test
    void sameFloorRouteHasDistanceTimeAndSteps() {
        UUID m = net.addMap(building, "EG", 0, true);
        UUID a = net.wp(m, 0.1, 0.1);
        UUID b = net.wp(m, 0.5, 0.1);
        UUID c = net.wp(m, 0.9, 0.1);
        net.seg(m, a, b, 12);
        net.seg(m, b, c, 18);
        UUID from = roomAt(m, a);
        UUID to = roomAt(m, c);

        var result = find(from, to, false);

        assertThat(result.problem()).isNull();
        var route = result.route();
        assertThat(route.distanceMeters()).isEqualTo(30.0);
        assertThat(route.walkingSeconds()).isEqualTo(25);
        assertThat(route.steps()).extracting(RouteFinder.Step::waypointId).containsExactly(a, b, c);
        assertThat(route.steps()).allSatisfy(s -> {
            assertThat(s.floorName()).isEqualTo("EG");
            assertThat(s.floorChange()).isNull();
        });
    }

    @Test
    void routeToItselfIsZeroWithOneStep() {
        UUID m = net.addMap(building, "EG", 0, true);
        UUID a = net.wp(m, 0.1, 0.1);
        UUID room = roomAt(m, a);

        var route = find(room, room, false).route();

        assertThat(route.distanceMeters()).isZero();
        assertThat(route.walkingSeconds()).isZero();
        assertThat(route.steps()).hasSize(1);
    }

    @Test
    void crossFloorRouteMarksTheStepReachedThroughTheFloorChange() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m1 = net.addMap(building, "1. OG", 1, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID lift0 = net.wp(m0, 0.5, 0.1);
        UUID lift1 = net.wp(m1, 0.5, 0.1);
        UUID b = net.wp(m1, 0.9, 0.1);
        net.seg(m0, a, lift0, 12);
        net.seg(m1, lift1, b, 6);
        UUID lift = net.connection("Aufzug 1", ConnectionType.ELEVATOR, new UUID[] {m0, lift0}, new UUID[] {m1, lift1});
        UUID from = roomAt(m0, a);
        UUID to = roomAt(m1, b);

        var route = find(from, to, false).route();

        assertThat(route.distanceMeters()).isEqualTo(18.0);
        assertThat(route.walkingSeconds()).isEqualTo(45);
        assertThat(route.steps()).hasSize(4);
        var change = route.steps().get(2).floorChange();
        assertThat(change).isNotNull();
        assertThat(change.connectionId()).isEqualTo(lift);
        assertThat(change.connectionName()).isEqualTo("Aufzug 1");
        assertThat(change.type()).isEqualTo(ConnectionType.ELEVATOR);
        assertThat(change.fromFloorName()).isEqualTo("EG");
        assertThat(change.toFloorName()).isEqualTo("1. OG");
        assertThat(route.steps().get(2).floorName()).isEqualTo("1. OG");
        assertThat(route.steps().get(1).floorChange()).isNull();
    }

    @Test
    void accessibleOnlyWithoutElevatorIsNoAccessibleRoute() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID m1 = net.addMap(building, "1. OG", 1, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID b = net.wp(m1, 0.1, 0.1);
        net.connection("Treppe", ConnectionType.STAIRS, new UUID[] {m0, a}, new UUID[] {m1, b});
        UUID from = roomAt(m0, a);
        UUID to = roomAt(m1, b);

        assertThat(find(from, to, false).route()).isNotNull();
        assertThat(find(from, to, true).problem()).isEqualTo(Problem.NO_ACCESSIBLE_ROUTE);
    }

    @Test
    void unattachedRoomsAreReportedWithTheRoomThatIsMissing() {
        UUID m = net.addMap(building, "EG", 0, true);
        UUID a = net.wp(m, 0.1, 0.1);
        UUID attached = roomAt(m, a);
        UUID loose = UUID.randomUUID();

        assertThat(find(loose, attached, false).problem()).isEqualTo(Problem.ROOM_NOT_ATTACHED);
        assertThat(find(attached, loose, false).problem()).isEqualTo(Problem.ROOM_NOT_ATTACHED);
    }

    @Test
    void endpointOnAMapWithoutScaleIsScaleMissingEvenWhenAlsoNotConnected() {
        UUID scaled = net.addMap(building, "EG", 0, true);
        UUID unscaled = net.addMap(building, "KG", -1, false);
        UUID from = roomAt(scaled, net.wp(scaled, 0.1, 0.1));
        UUID to = roomAt(unscaled, net.wp(unscaled, 0.1, 0.1));

        assertThat(find(from, to, false).problem()).isEqualTo(Problem.SCALE_MISSING);
    }

    @Test
    void routeThatWouldCrossAnUnscaledMapIsScaleMissing() {
        UUID m0 = net.addMap(building, "EG", 0, true);
        UUID mid = net.addMap(building, "1. OG", 1, false);
        UUID m2 = net.addMap(building, "2. OG", 2, true);
        UUID a = net.wp(m0, 0.1, 0.1);
        UUID midIn = net.wp(mid, 0.1, 0.1);
        UUID midOut = net.wp(mid, 0.9, 0.1);
        UUID top = net.wp(m2, 0.9, 0.1);
        net.seg(mid, midIn, midOut, 10);
        net.connection("Treppe 1", ConnectionType.STAIRS, new UUID[] {m0, a}, new UUID[] {mid, midIn});
        net.connection("Treppe 2", ConnectionType.STAIRS, new UUID[] {mid, midOut}, new UUID[] {m2, top});
        UUID from = roomAt(m0, a);
        UUID to = roomAt(m2, top);

        assertThat(find(from, to, false).problem()).isEqualTo(Problem.SCALE_MISSING);
    }

    @Test
    void separateNetworkPartsAndDifferentBuildingsAreNotConnected() {
        UUID m = net.addMap(building, "EG", 0, true);
        UUID a = net.wp(m, 0.1, 0.1);
        UUID b = net.wp(m, 0.9, 0.9);
        UUID from = roomAt(m, a);
        UUID to = roomAt(m, b);
        assertThat(find(from, to, false).problem()).isEqualTo(Problem.NOT_CONNECTED);

        UUID otherMap = net.addMap(UUID.randomUUID(), "EG", 0, true);
        UUID c = net.wp(otherMap, 0.1, 0.1);
        net.connection("Brücke", ConnectionType.STAIRS, new UUID[] {m, a}, new UUID[] {otherMap, c});
        UUID far = roomAt(otherMap, c);
        assertThat(find(from, far, false).problem()).isEqualTo(Problem.NOT_CONNECTED);
    }
}
