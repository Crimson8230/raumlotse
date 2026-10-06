package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SeatingArrangementRequest(
        @NotBlank(message = "Der Name darf nicht leer sein") String name,
        @Min(value = 1, message = "Die Kapazität muss größer als 0 sein") int maxCapacity) {
}
