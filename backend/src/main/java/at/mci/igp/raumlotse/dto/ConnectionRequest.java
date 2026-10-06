package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.ConnectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConnectionRequest(@NotBlank @Size(max = 100) String name, @NotNull ConnectionType type) {
}
