package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RolePermissionsUpdateRequest(
        @NotNull @Size(max = 14) List<@NotNull String> permissions,
        @tools.jackson.databind.annotation.JsonDeserialize(using = RoleVersionDeserializer.class)
        @NotNull @Pattern(regexp = "0|[1-9][0-9]{0,18}") String expectedVersion) {
}
