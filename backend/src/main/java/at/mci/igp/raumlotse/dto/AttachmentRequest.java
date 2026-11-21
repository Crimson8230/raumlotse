package at.mci.igp.raumlotse.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AttachmentRequest(@NotNull UUID waypointId) {
}
