package at.mci.igp.raumlotse.dto;

import java.util.List;
import java.util.UUID;

public record MapNetworkResponse(UUID mapId, ScaleResponse scale, ReferenceSegmentResponse referenceSegment,
        List<WaypointResponse> waypoints, List<SegmentResponse> segments,
        List<RoomAttachmentResponse> roomAttachments,
        List<ConnectionPointAttachmentResponse> connectionPointAttachments, PathIssuesResponse issues) {
}
