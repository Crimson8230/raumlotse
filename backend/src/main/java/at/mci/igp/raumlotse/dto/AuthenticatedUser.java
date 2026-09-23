package at.mci.igp.raumlotse.dto;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String displayName) {
}
