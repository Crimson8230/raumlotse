package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code specialised}: omitted on create means general; omitted on update keeps the current value (feature 017). */
public record EquipmentTypeRequest(@NotBlank(message = "Der Name darf nicht leer sein") String name, String code,
        Boolean specialised) {
    public EquipmentTypeRequest(String name) { this(name, null, null); }
    public EquipmentTypeRequest(String name, String code) { this(name, code, null); }
}
