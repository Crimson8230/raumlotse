package at.mci.igp.raumlotse.dto;

import java.util.List;
import java.util.UUID;

/** {@code available=false} carries a {@code reason}; entries are then empty and no estimate is made. */
public record NeighborsResponse(UUID roomId, boolean available, String reason, List<NeighborEntryResponse> entries) {
}
