package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import java.util.UUID;

public record EquipmentTypeResponse(UUID id, String name, EntityStatus status) {

    public static EquipmentTypeResponse from(EquipmentType equipmentType) {
        return new EquipmentTypeResponse(equipmentType.getId(), equipmentType.getName(), equipmentType.getStatus());
    }
}
