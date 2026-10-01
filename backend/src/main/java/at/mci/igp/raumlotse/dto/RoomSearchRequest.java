package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.RoomSearchCriteria;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Query parameters of {@code GET /api/rooms/search}; all optional. {@code equipmentTypeId} is repeatable and
 * keeps the singular name of the query parameter.
 */
public record RoomSearchRequest(
        @Min(value = 1, message = "must be a whole number of at least 1") Integer minPersons,
        @Min(value = 1, message = "must be a whole number of at least 1") Integer maxPersons,
        UUID buildingId,
        @Size(max = 100, message = "must be at most 100 characters") String seatingArrangement,
        List<UUID> equipmentTypeId,
        Boolean barrierFree,
        Instant from,
        Instant to) {

    public RoomSearchCriteria toCriteria() {
        return new RoomSearchCriteria(minPersons, maxPersons, buildingId, seatingArrangement,
                equipmentTypeId == null ? Set.of() : Set.copyOf(equipmentTypeId), Boolean.TRUE.equals(barrierFree), from, to);
    }
}
