package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.ConnectionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nearest neighbors of a room on its own floor (up to {@link RoutingDefaults#MAX_NEIGHBORS_PER_SIDE} per direction)
 * plus the nearest stairs and elevator leading up and down. Left and right are seen by a person standing in the
 * corridor in front of the room's door and facing it.
 */
public final class NeighborCalculator {

    public enum Direction { LEFT, RIGHT, STRAIGHT, UP, DOWN }

    public enum Kind { ROOM, STAIRS, ELEVATOR }

    public enum Reason { ROOM_NOT_ATTACHED, SCALE_MISSING, NO_ORIENTATION, NO_NEIGHBORS }

    /** A placed room: marker position is relative (0..1) on its map. */
    public record RoomInfo(UUID id, String name, UUID mapId, double x, double y, boolean active) {
    }

    /** {@code roomId} is set for rooms, {@code connectionId} and {@code targetFloorName} for stairs/elevators. */
    public record Entry(Direction direction, Kind kind, UUID roomId, UUID connectionId, String name,
            String targetFloorName, double meters, int seconds) {
    }

    public record Result(Reason reason, List<Entry> entries) {
        public boolean available() {
            return reason == null;
        }
    }

    private NeighborCalculator() {
    }

    public static Result calculate(RoutingGraph graph, UUID roomId, Map<UUID, RoomInfo> rooms) {
        RoomInfo self = rooms.get(roomId);
        UUID start = graph.roomWaypoint(roomId).orElse(null);
        UUID mapId = graph.roomMap(roomId).orElse(null);
        if (self == null || start == null || mapId == null) {
            return failure(Reason.ROOM_NOT_ATTACHED);
        }
        if (!graph.scaleAvailable(mapId)) {
            return failure(Reason.SCALE_MISSING);
        }
        RoutingGraph.Node startNode = graph.node(start);
        double width = graph.widthPx(mapId);
        double height = graph.heightPx(mapId);
        // Facing direction: from the corridor point into the room.
        double fx = (self.x() - startNode.x()) * width;
        double fy = (self.y() - startNode.y()) * height;
        if (Math.hypot(fx, fy) < PathGeometry.COINCIDENCE_EPSILON * Math.min(width, height)) {
            return failure(Reason.NO_ORIENTATION);
        }

        RouteCalculator.Tree tree = RouteCalculator.tree(graph, start, mapId);
        Map<Direction, List<Entry>> byDirection = new EnumMap<>(Direction.class);

        for (RoomInfo other : rooms.values()) {
            if (other.id().equals(roomId) || !other.active() || !mapId.equals(other.mapId())) {
                continue;
            }
            UUID waypoint = graph.roomWaypoint(other.id()).orElse(null);
            if (waypoint == null || !tree.reachable(waypoint)) {
                continue;
            }
            double sx;
            double sy;
            UUID hop = tree.firstHop(waypoint);
            if (hop == null) {
                sx = (other.x() - self.x()) * width;
                sy = (other.y() - self.y()) * height;
            } else {
                RoutingGraph.Node node = graph.node(hop);
                sx = (node.x() - startNode.x()) * width;
                sy = (node.y() - startNode.y()) * height;
            }
            Direction direction = classify(fx, fy, sx, sy);
            double meters = tree.meters(waypoint);
            byDirection.computeIfAbsent(direction, k -> new ArrayList<>()).add(new Entry(direction, Kind.ROOM,
                    other.id(), null, other.name(), null, round1(meters), seconds(meters)));
        }

        List<Entry> entries = new ArrayList<>();
        for (Direction direction : List.of(Direction.LEFT, Direction.STRAIGHT, Direction.RIGHT)) {
            byDirection.getOrDefault(direction, List.of()).stream()
                    .sorted(Comparator.comparingDouble(Entry::meters).thenComparing(Entry::name))
                    .limit(RoutingDefaults.MAX_NEIGHBORS_PER_SIDE).forEach(entries::add);
        }
        entries.addAll(vertical(graph, mapId, tree, Direction.UP));
        entries.addAll(vertical(graph, mapId, tree, Direction.DOWN));

        if (entries.isEmpty()) {
            return failure(Reason.NO_NEIGHBORS);
        }
        return new Result(null, List.copyOf(entries));
    }

    private static Result failure(Reason reason) {
        return new Result(reason, List.of());
    }

    /** Straight when the first step runs within the tolerance along the facing direction; otherwise by side. */
    static Direction classify(double fx, double fy, double sx, double sy) {
        double cosine = (fx * sx + fy * sy) / (Math.hypot(fx, fy) * Math.hypot(sx, sy));
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
        if (Double.isNaN(angle)) {
            return Direction.RIGHT;
        }
        if (angle <= RoutingDefaults.STRAIGHT_TOLERANCE_DEGREES) {
            return Direction.STRAIGHT;
        }
        // Left of a person facing (fx, fy) on a y-down map is (fy, -fx); ties go to the right.
        double side = sx * fy + sy * -fx;
        return side > 1e-9 ? Direction.LEFT : Direction.RIGHT;
    }

    private static List<Entry> vertical(RoutingGraph graph, UUID mapId, RouteCalculator.Tree tree,
            Direction direction) {
        int here = graph.mapInfo(mapId).floorPosition();
        Map<ConnectionType, Entry> best = new EnumMap<>(ConnectionType.class);
        for (RoutingGraph.Connection connection : graph.connections()) {
            RoutingGraph.AttachedPoint local = connection.points().stream()
                    .filter(p -> p.mapId().equals(mapId)).findFirst().orElse(null);
            if (local == null || !tree.reachable(local.waypointId())) {
                continue;
            }
            RoutingGraph.AttachedPoint target = connection.points().stream()
                    .filter(p -> direction == Direction.UP ? p.floorPosition() > here : p.floorPosition() < here)
                    .min(Comparator.comparingInt(p -> Math.abs(p.floorPosition() - here))).orElse(null);
            if (target == null) {
                continue;
            }
            double meters = tree.meters(local.waypointId());
            Entry entry = new Entry(direction, connection.type() == ConnectionType.STAIRS ? Kind.STAIRS : Kind.ELEVATOR,
                    null, connection.connectionId(), connection.name(), target.floorName(), round1(meters),
                    seconds(meters));
            Entry known = best.get(connection.type());
            if (known == null || entry.meters() < known.meters()) {
                best.put(connection.type(), entry);
            }
        }
        List<Entry> result = new ArrayList<>();
        if (best.containsKey(ConnectionType.STAIRS)) {
            result.add(best.get(ConnectionType.STAIRS));
        }
        if (best.containsKey(ConnectionType.ELEVATOR)) {
            result.add(best.get(ConnectionType.ELEVATOR));
        }
        return result;
    }

    private static double round1(double meters) {
        return Math.round(meters * 10.0) / 10.0;
    }

    private static int seconds(double meters) {
        return (int) Math.round(meters / RoutingDefaults.WALKING_SPEED_MPS);
    }
}
