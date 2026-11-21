package at.mci.igp.raumlotse.dto;

import java.util.UUID;

/** {@code floorChange} is set on the step that is reached through stairs or an elevator. */
public record RouteStepResponse(UUID mapId, String floorName, UUID waypointId, double x, double y,
        FloorChange floorChange) {

    public record FloorChange(UUID connectionId, String connectionName, String type, String fromFloorName,
            String toFloorName) {
    }
}
