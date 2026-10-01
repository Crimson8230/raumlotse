package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code hasElevator} is optional (feature 008): on create null means false, on update null means unchanged. */
public record BuildingRequest(@NotBlank(message = "name must not be blank") String name, Boolean hasElevator) {
}
