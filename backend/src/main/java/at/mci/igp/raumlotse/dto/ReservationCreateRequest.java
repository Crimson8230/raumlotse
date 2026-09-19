package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationCreateRequest(
        @NotNull @Future Instant startTime,
        @NotNull Instant endTime,
        @NotNull UUID seatingArrangementId,
        @NotNull @Min(1) Integer expectedAttendees,
        List<UUID> additionalEquipmentTypeIds,
        @Size(max = 2000) String note,
        @NotBlank @Size(max = 255) String createdBy) {
}
