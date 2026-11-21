package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.ConnectionType;
import at.mci.igp.raumlotse.dto.ConnectionGroupResponse;
import at.mci.igp.raumlotse.dto.MapNetworkResponse;
import at.mci.igp.raumlotse.dto.NetworkWideResponse;
import at.mci.igp.raumlotse.dto.RoomAttachmentResponse;
import at.mci.igp.raumlotse.dto.SegmentResponse;
import at.mci.igp.raumlotse.dto.WaypointResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Routing graph built from the path network read model of feature 023: waypoints are nodes, segments of maps with a
 * scale are undirected walking edges (meters and seconds), and fully attached connections of one building link the
 * floors. Connections between different buildings are not supported and ignored.
 */
public final class RoutingGraph {

    /** Floor data of one map; {@code floorPosition} is the order of the floor in its building (0 = ground floor). */
    public record MapInfo(UUID mapId, UUID buildingId, int floorPosition, String floorName) {
    }

    public record Node(UUID id, UUID mapId, double x, double y) {
    }

    public record ConnectionRef(UUID connectionId, String name, ConnectionType type) {
    }

    /** A walking edge has {@code connection == null}; a floor change has zero meters and a fixed time cost. */
    public record Edge(UUID to, double meters, double seconds, ConnectionRef connection) {
    }

    public record AttachedPoint(UUID connectionPointId, UUID mapId, UUID waypointId, int floorPosition,
            String floorName) {
    }

    public record Connection(UUID connectionId, String name, ConnectionType type, List<AttachedPoint> points) {
    }

    private record MapMeta(boolean scaleAvailable, int widthPx, int heightPx) {
    }

    private final Map<UUID, Node> nodes = new HashMap<>();
    private final Map<UUID, List<Edge>> edges = new HashMap<>();
    private final Map<UUID, UUID> roomWaypoints = new HashMap<>();
    private final Map<UUID, UUID> roomMaps = new HashMap<>();
    private final Map<UUID, MapMeta> metas = new HashMap<>();
    private final Map<UUID, MapInfo> infos;
    private final List<Connection> connections = new ArrayList<>();

    private RoutingGraph(Map<UUID, MapInfo> infos) {
        this.infos = infos;
    }

    public static RoutingGraph build(NetworkWideResponse network, Map<UUID, MapInfo> infos, boolean accessibleOnly) {
        return build(network, infos, accessibleOnly, false);
    }

    /**
     * With {@code assumeUnitLengths} the segments of maps without a scale count as one meter each. That graph is only
     * used to tell "not connected" from "would cross a map without scale"; its distances are never reported.
     */
    public static RoutingGraph build(NetworkWideResponse network, Map<UUID, MapInfo> infos, boolean accessibleOnly,
            boolean assumeUnitLengths) {
        RoutingGraph graph = new RoutingGraph(infos);
        for (MapNetworkResponse map : network.maps()) {
            boolean scaled = map.scale().available();
            graph.metas.put(map.mapId(), new MapMeta(scaled, map.scale().widthPx(), map.scale().heightPx()));
            for (WaypointResponse w : map.waypoints()) {
                graph.nodes.put(w.id(), new Node(w.id(), w.mapId(), w.x(), w.y()));
            }
            for (RoomAttachmentResponse a : map.roomAttachments()) {
                graph.roomWaypoints.put(a.roomId(), a.waypointId());
                graph.roomMaps.put(a.roomId(), a.mapId());
            }
            if (scaled || assumeUnitLengths) {
                for (SegmentResponse s : map.segments()) {
                    double meters = s.lengthMeters() == null ? 1.0 : s.lengthMeters();
                    if (s.lengthMeters() == null && !assumeUnitLengths) {
                        continue;
                    }
                    double seconds = meters / RoutingDefaults.WALKING_SPEED_MPS;
                    graph.addEdge(s.fromWaypointId(), new Edge(s.toWaypointId(), meters, seconds, null));
                    graph.addEdge(s.toWaypointId(), new Edge(s.fromWaypointId(), meters, seconds, null));
                }
            }
        }
        for (ConnectionGroupResponse group : network.connectionGroups()) {
            graph.addConnection(group, accessibleOnly);
        }
        return graph;
    }

    private void addEdge(UUID from, Edge edge) {
        edges.computeIfAbsent(from, k -> new ArrayList<>()).add(edge);
    }

    private void addConnection(ConnectionGroupResponse group, boolean accessibleOnly) {
        if (!group.fullyAttached()) {
            return;
        }
        ConnectionType type = ConnectionType.valueOf(group.type());
        if (accessibleOnly && type == ConnectionType.STAIRS) {
            return;
        }
        List<AttachedPoint> points = new ArrayList<>();
        UUID building = null;
        for (ConnectionGroupResponse.Point p : group.points()) {
            MapInfo info = infos.get(p.mapId());
            if (info == null || p.waypointId() == null) {
                return;
            }
            if (building == null) {
                building = info.buildingId();
            } else if (!building.equals(info.buildingId())) {
                return;
            }
            points.add(new AttachedPoint(p.connectionPointId(), p.mapId(), p.waypointId(), info.floorPosition(),
                    info.floorName()));
        }
        ConnectionRef ref = new ConnectionRef(group.connectionId(), group.name(), type);
        for (AttachedPoint p : points) {
            for (AttachedPoint q : points) {
                if (p == q || p.mapId().equals(q.mapId())) {
                    continue;
                }
                int steps = Math.max(1, Math.abs(p.floorPosition() - q.floorPosition()));
                double seconds = type == ConnectionType.STAIRS ? steps * RoutingDefaults.STAIRS_SECONDS_PER_FLOOR
                        : RoutingDefaults.ELEVATOR_SECONDS_PER_CHANGE;
                addEdge(p.waypointId(), new Edge(q.waypointId(), 0.0, seconds, ref));
            }
        }
        connections.add(new Connection(group.connectionId(), group.name(), type, List.copyOf(points)));
    }

    public Node node(UUID waypointId) {
        return nodes.get(waypointId);
    }

    public List<Edge> edgesFrom(UUID waypointId) {
        return edges.getOrDefault(waypointId, List.of());
    }

    public Optional<UUID> roomWaypoint(UUID roomId) {
        return Optional.ofNullable(roomWaypoints.get(roomId));
    }

    public Optional<UUID> roomMap(UUID roomId) {
        return Optional.ofNullable(roomMaps.get(roomId));
    }

    public boolean scaleAvailable(UUID mapId) {
        MapMeta meta = metas.get(mapId);
        return meta != null && meta.scaleAvailable();
    }

    public int widthPx(UUID mapId) {
        return metas.get(mapId).widthPx();
    }

    public int heightPx(UUID mapId) {
        return metas.get(mapId).heightPx();
    }

    public MapInfo mapInfo(UUID mapId) {
        return infos.get(mapId);
    }

    /** Usable connections: fully attached, within one building, and allowed by the accessibility option. */
    public List<Connection> connections() {
        return connections;
    }
}
