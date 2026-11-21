package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.ConnectionType;
import at.mci.igp.raumlotse.dto.NetworkWideResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Finds the route between two rooms and explains why there is none; pure logic without data access. */
public final class RouteFinder {

    public enum Problem { ROOM_NOT_ATTACHED, SCALE_MISSING, NOT_CONNECTED, NO_ACCESSIBLE_ROUTE }

    public record FloorChange(UUID connectionId, String connectionName, ConnectionType type, String fromFloorName,
            String toFloorName) {
    }

    /** {@code floorChange} is set on the step that is reached through a stairs or elevator. */
    public record Step(UUID mapId, String floorName, UUID waypointId, double x, double y, FloorChange floorChange) {
    }

    public record Route(double distanceMeters, int walkingSeconds, List<Step> steps) {
    }

    /** Exactly one of {@code route} and {@code problem} is set. */
    public record Result(Route route, Problem problem) {
    }

    private RouteFinder() {
    }

    public static Result find(NetworkWideResponse network, Map<UUID, RoutingGraph.MapInfo> infos, UUID fromRoom,
            UUID toRoom, boolean accessibleOnly) {
        RoutingGraph graph = RoutingGraph.build(network, infos, accessibleOnly);
        UUID from = graph.roomWaypoint(fromRoom).orElse(null);
        UUID to = graph.roomWaypoint(toRoom).orElse(null);
        if (from == null || to == null) {
            return problem(Problem.ROOM_NOT_ATTACHED);
        }
        if (!graph.scaleAvailable(graph.roomMap(fromRoom).orElseThrow())
                || !graph.scaleAvailable(graph.roomMap(toRoom).orElseThrow())) {
            return problem(Problem.SCALE_MISSING);
        }
        var path = RouteCalculator.shortest(graph, from, to);
        if (path.isPresent()) {
            return new Result(toRoute(graph, path.get()), null);
        }
        if (accessibleOnly && RouteCalculator.shortest(RoutingGraph.build(network, infos, false), from, to).isPresent()) {
            return problem(Problem.NO_ACCESSIBLE_ROUTE);
        }
        boolean wouldConnect = RouteCalculator.shortest(RoutingGraph.build(network, infos, false, true), from, to)
                .isPresent();
        return problem(wouldConnect ? Problem.SCALE_MISSING : Problem.NOT_CONNECTED);
    }

    private static Result problem(Problem problem) {
        return new Result(null, problem);
    }

    private static Route toRoute(RoutingGraph graph, RouteCalculator.Path path) {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < path.nodes().size(); i++) {
            RoutingGraph.Node node = graph.node(path.nodes().get(i));
            RoutingGraph.MapInfo info = graph.mapInfo(node.mapId());
            FloorChange change = null;
            if (i > 0 && path.edges().get(i - 1).connection() != null) {
                RoutingGraph.ConnectionRef ref = path.edges().get(i - 1).connection();
                RoutingGraph.MapInfo previous = graph.mapInfo(graph.node(path.nodes().get(i - 1)).mapId());
                change = new FloorChange(ref.connectionId(), ref.name(), ref.type(), previous.floorName(),
                        info.floorName());
            }
            steps.add(new Step(node.mapId(), info.floorName(), node.id(), node.x(), node.y(), change));
        }
        return new Route(Math.round(path.meters() * 10.0) / 10.0, (int) Math.round(path.seconds()),
                List.copyOf(steps));
    }
}
