package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReservationUpdateRequest(
        @Min(1) Integer expectedAttendees,
        @Size(max = 2000) String note,
        @Pattern(regexp = ".*\\S.*", message = "darf nicht leer sein")
        @Size(min = 1, max = 255) String reservedFor) {

    public ReservationUpdateRequest(Integer expectedAttendees, String note) {
        this(expectedAttendees, note, null);
    }
}
