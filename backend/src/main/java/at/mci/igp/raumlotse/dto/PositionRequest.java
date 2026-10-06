package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Position as fractions of the map image width (x) and height (y); both within [0, 1]. */
public record PositionRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double x,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double y) {
}
