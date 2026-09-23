package at.mci.igp.raumlotse.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record UserRoleUpdateRequest(
    @NotNull @Size(min=1,max=5) List<@NotNull String> roles,
    @tools.jackson.databind.annotation.JsonDeserialize(using=RoleVersionDeserializer.class)
    @NotNull @Pattern(regexp="0|[1-9][0-9]{0,18}") String expectedVersion) {}
