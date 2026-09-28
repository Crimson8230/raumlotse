package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code groundFloor} is optional (feature 008): on create null means false, on update null means unchanged. */
public record FloorRequest(@NotBlank(message = "name must not be blank") String name, Boolean groundFloor) {
}
