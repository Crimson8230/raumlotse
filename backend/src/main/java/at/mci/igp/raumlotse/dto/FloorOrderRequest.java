package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** All floors of a building, ground floor first, top floor last. */
public record FloorOrderRequest(@NotNull(message = "floorIds fehlt") List<UUID> floorIds) {
}
