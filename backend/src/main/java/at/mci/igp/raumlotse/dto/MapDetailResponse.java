package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.FloorMap;
import java.util.List;
import java.util.UUID;

public record MapDetailResponse(UUID id, UUID floorId, String name, int widthPx, int heightPx, long imageVersion,
        long placedRoomCount, List<PlacementResponse> placements, List<ConnectionResponse> connections) {

    public static MapDetailResponse from(FloorMap map, List<PlacementResponse> placements,
            List<ConnectionResponse> connections) {
        return new MapDetailResponse(map.getId(), map.getFloor().getId(), map.getName(), map.getWidthPx(),
                map.getHeightPx(), map.getImageVersion(), placements.size(), placements, connections);
    }
}
