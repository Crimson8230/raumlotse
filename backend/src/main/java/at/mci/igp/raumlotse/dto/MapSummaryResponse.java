package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.FloorMap;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapSummaryResponse(UUID id, UUID floorId, String name, int widthPx, int heightPx, long imageVersion,
        long placedRoomCount, Boolean aspectRatioChanged) {

    public static MapSummaryResponse from(FloorMap map, long placedRoomCount) {
        return from(map, placedRoomCount, null);
    }

    public static MapSummaryResponse from(FloorMap map, long placedRoomCount, Boolean aspectRatioChanged) {
        return new MapSummaryResponse(map.getId(), map.getFloor().getId(), map.getName(), map.getWidthPx(),
                map.getHeightPx(), map.getImageVersion(), placedRoomCount, aspectRatioChanged);
    }
}
