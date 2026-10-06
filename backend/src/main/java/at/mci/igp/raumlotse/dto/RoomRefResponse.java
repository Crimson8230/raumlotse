package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Room;
import java.util.UUID;

public record RoomRefResponse(UUID id, String name, EntityStatus status) {

    public static RoomRefResponse from(Room room) {
        return new RoomRefResponse(room.getId(), room.getName(), room.getStatus());
    }
}
