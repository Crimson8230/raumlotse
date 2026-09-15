package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotBlank;

public record FloorRequest(@NotBlank(message = "name must not be blank") String name) {
}
