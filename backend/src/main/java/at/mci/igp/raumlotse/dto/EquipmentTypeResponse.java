package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import java.util.UUID;

public record EquipmentTypeResponse(UUID id, String name, String code, EntityStatus status) {
    public EquipmentTypeResponse(UUID id, String name, EntityStatus status) {
        this(id, name, name == null ? null : name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_"), status);
    }

    public static EquipmentTypeResponse from(EquipmentType equipmentType) {
        return new EquipmentTypeResponse(equipmentType.getId(), equipmentType.getName(), equipmentType.getCode(), equipmentType.getStatus());
    }
}
