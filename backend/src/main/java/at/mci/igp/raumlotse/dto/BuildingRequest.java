package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code hasElevator} is optional (feature 008): on create null means false, on update null means unchanged. */
public record BuildingRequest(@NotBlank(message = "Der Name darf nicht leer sein") String name, Boolean hasElevator) {
}
