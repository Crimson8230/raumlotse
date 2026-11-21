package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SegmentRequest(@NotNull UUID fromWaypointId, @NotNull UUID toWaypointId) {
}
