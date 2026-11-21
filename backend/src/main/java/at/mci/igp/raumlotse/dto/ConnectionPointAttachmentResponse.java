package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.ConnectionPointPathAttachment;
import java.util.UUID;

public record ConnectionPointAttachmentResponse(UUID connectionPointId, UUID mapId, UUID waypointId) {

    public static ConnectionPointAttachmentResponse from(ConnectionPointPathAttachment attachment) {
        return new ConnectionPointAttachmentResponse(attachment.getConnectionPointId(), attachment.getMapId(),
                attachment.getWaypointId());
    }
}
