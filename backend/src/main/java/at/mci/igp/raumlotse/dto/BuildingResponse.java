package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EntityStatus;
import java.util.UUID;

public record BuildingResponse(UUID id, String name, EntityStatus status, boolean hasElevator) {

    public static BuildingResponse from(Building building) {
        return new BuildingResponse(building.getId(), building.getName(), building.getStatus(), building.hasElevator());
    }
}
