package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.RoomPathAttachment;
import java.util.UUID;

public record RoomAttachmentResponse(UUID roomId, UUID mapId, UUID waypointId) {

    public static RoomAttachmentResponse from(RoomPathAttachment attachment) {
        return new RoomAttachmentResponse(attachment.getRoomId(), attachment.getMapId(), attachment.getWaypointId());
    }
}
