package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotBlank;

public record EquipmentTypeRequest(@NotBlank(message = "Der Name darf nicht leer sein") String name, String code) {
    public EquipmentTypeRequest(String name) { this(name, null); }
}
