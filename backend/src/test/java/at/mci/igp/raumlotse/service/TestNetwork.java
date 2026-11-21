package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.ConnectionType;
import at.mci.igp.raumlotse.dto.ConnectionGroupResponse;
import at.mci.igp.raumlotse.dto.ConnectionPointAttachmentResponse;
import at.mci.igp.raumlotse.dto.MapNetworkResponse;
import at.mci.igp.raumlotse.dto.NetworkWideResponse;
import at.mci.igp.raumlotse.dto.PathIssuesResponse;
import at.mci.igp.raumlotse.dto.RoomAttachmentResponse;
import at.mci.igp.raumlotse.dto.ScaleResponse;
import at.mci.igp.raumlotse.dto.SegmentResponse;
import at.mci.igp.raumlotse.dto.WaypointResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds small path networks (1000x1000 px maps, relative coordinates) for routing unit tests. */
final class TestNetwork {

    static final int SIZE_PX = 1000;

    private static final class MapData {
        final boolean scaled;
        final List<WaypointResponse> waypoints = new ArrayList<>();
        final List<SegmentResponse> segments = new ArrayList<>();
        final List<RoomAttachmentResponse> rooms = new ArrayList<>();
        final List<ConnectionPointAttachmentResponse> points = new ArrayList<>();

        MapData(boolean scaled) {
            this.scaled = scaled;
        }
    }

    final Map<UUID, RoutingGraph.MapInfo> infos = new LinkedHashMap<>();
    private final Map<UUID, MapData> maps = new LinkedHashMap<>();
    private final List<ConnectionGroupResponse> groups = new ArrayList<>();

    UUID addMap(UUID building, String floorName, int position, boolean scaled) {
        UUID id = UUID.randomUUID();
        infos.put(id, new RoutingGraph.MapInfo(id, building, position, floorName));
        maps.put(id, new MapData(scaled));
        return id;
    }

    /** x and y are relative (0..1). */
    UUID wp(UUID map, double x, double y) {
        UUID id = UUID.randomUUID();
        maps.get(map).waypoints.add(new WaypointResponse(id, map, x, y));
        return id;
    }

    /** The meters are ignored (null) on maps without a scale. */
    UUID seg(UUID map, UUID a, UUID b, double meters) {
        UUID id = UUID.randomUUID();
        MapData data = maps.get(map);
        data.segments.add(new SegmentResponse(id, map, a, b, data.scaled ? meters : null));
        return id;
    }

    void roomAt(UUID room, UUID map, UUID waypoint) {
        maps.get(map).rooms.add(new RoomAttachmentResponse(room, map, waypoint));
    }

    /** One connection spanning the given (map, waypoint) pairs; a null waypoint means "not attached". */
    UUID connection(String name, ConnectionType type, UUID[]... mapAndWaypoint) {
        UUID connectionId = UUID.randomUUID();
        List<ConnectionGroupResponse.Point> points = new ArrayList<>();
        boolean fully = true;
        for (UUID[] pair : mapAndWaypoint) {
            UUID pointId = UUID.randomUUID();
            points.add(new ConnectionGroupResponse.Point(pointId, pair[0], pair[1]));
            if (pair[1] == null) {
                fully = false;
            } else {
                maps.get(pair[0]).points.add(new ConnectionPointAttachmentResponse(pointId, pair[0], pair[1]));
            }
        }
        groups.add(new ConnectionGroupResponse(connectionId, name, type.name(), fully, points));
        return connectionId;
    }

    NetworkWideResponse network() {
        List<MapNetworkResponse> result = new ArrayList<>();
        maps.forEach((id, data) -> {
            ScaleResponse scale = data.scaled ? new ScaleResponse(true, 10.0, SIZE_PX, SIZE_PX)
                    : new ScaleResponse(false, null, SIZE_PX, SIZE_PX);
            result.add(new MapNetworkResponse(id, scale, null, data.waypoints, data.segments, data.rooms, data.points,
                    new PathIssuesResponse(!data.scaled, 1, List.of(), List.of(), List.of(), List.of())));
        });
        return new NetworkWideResponse(result, groups);
    }
}
