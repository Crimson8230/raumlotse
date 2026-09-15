package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Floor;
import java.util.UUID;

public record FloorResponse(UUID id, UUID buildingId, String name, EntityStatus status) {

    public static FloorResponse from(Floor floor) {
        return new FloorResponse(floor.getId(), floor.getBuilding().getId(), floor.getName(), floor.getStatus());
    }
}
