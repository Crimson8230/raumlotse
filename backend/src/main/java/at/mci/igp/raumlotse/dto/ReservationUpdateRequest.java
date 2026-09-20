package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ReservationUpdateRequest(
        @Min(1) Integer expectedAttendees,
        @Size(max = 2000) String note) {
}
