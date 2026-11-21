package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.PathWaypoint;
import java.util.UUID;

public record WaypointResponse(UUID id, UUID mapId, double x, double y) {

    public static WaypointResponse from(PathWaypoint waypoint) {
        return new WaypointResponse(waypoint.getId(), waypoint.getMapId(), waypoint.getX(), waypoint.getY());
    }
}
