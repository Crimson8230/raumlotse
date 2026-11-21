package at.mci.igp.raumlotse.dto;

import java.util.List;
import java.util.UUID;

public record PathIssuesResponse(boolean missingScale, int componentCount, List<List<UUID>> components,
        List<UUID> unattachedRoomIds, List<UUID> unattachedConnectionPointIds,
        List<OutsideAttachment> attachmentsOutsideMainComponent) {

    /** An attachment whose waypoint lies outside the largest network part; kind is ROOM or CONNECTION_POINT. */
    public record OutsideAttachment(String kind, UUID id) {
    }
}
