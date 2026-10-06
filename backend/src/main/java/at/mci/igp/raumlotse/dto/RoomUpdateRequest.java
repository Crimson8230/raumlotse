package at.mci.igp.raumlotse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RoomUpdateRequest(
        @NotBlank(message = "Der Name darf nicht leer sein") String name,
        @NotNull(message = "Das Stockwerk muss angegeben werden") UUID floorId,
        @NotEmpty(message = "Mindestens eine Sitzordnung ist erforderlich") @Valid List<SeatingArrangementRequest> seatingArrangements,
        List<UUID> equipmentTypeIds,
        @NotNull(message = "Die Version muss angegeben werden") Long version,
        Boolean notBarrierFree) {

    /** Pre-008 shape; {@code notBarrierFree} omitted means unchanged. */
    public RoomUpdateRequest(String name, UUID floorId, List<SeatingArrangementRequest> seatingArrangements,
            List<UUID> equipmentTypeIds, Long version) {
        this(name, floorId, seatingArrangements, equipmentTypeIds, version, null);
    }

    public List<UUID> equipmentTypeIds() {
        return equipmentTypeIds == null ? List.of() : equipmentTypeIds;
    }
}
