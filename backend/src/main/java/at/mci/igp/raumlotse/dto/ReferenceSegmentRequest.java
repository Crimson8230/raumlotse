package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ReferenceSegmentRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double x1,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double y1,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double x2,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double y2,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("100000.0") Double lengthMeters) {
}
