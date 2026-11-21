package at.mci.igp.raumlotse.dto;

import java.util.List;
import java.util.UUID;

public record ConnectionGroupResponse(UUID connectionId, String name, String type, boolean fullyAttached,
        List<Point> points) {

    /** {@code waypointId} is null while the point is not attached to the network. */
    public record Point(UUID connectionPointId, UUID mapId, UUID waypointId) {
    }
}
