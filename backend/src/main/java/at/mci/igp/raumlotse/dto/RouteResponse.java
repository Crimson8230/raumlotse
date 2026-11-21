package at.mci.igp.raumlotse.dto;

import java.util.List;
import java.util.UUID;

/** Route between two rooms: distance in meters, estimated walking time and the ordered steps. */
public record RouteResponse(UUID fromRoomId, UUID toRoomId, boolean accessibleOnly, double distanceMeters,
        int walkingSeconds, List<RouteStepResponse> steps) {
}
