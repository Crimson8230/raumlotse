package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Room;
import java.util.UUID;

/** One favorite of the current user; {@code available} is false once room, floor or building is deactivated. */
public record FavoriteRoomResponse(UUID roomId, String roomName, String buildingName, String floorName,
        boolean available) {

    public static FavoriteRoomResponse from(Room room) {
        boolean available = room.getStatus() == EntityStatus.ACTIVE && room.getFloor().isSelectable();
        return new FavoriteRoomResponse(room.getId(), room.getName(), room.getFloor().getBuilding().getName(),
                room.getFloor().getName(), available);
    }
}
