package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Room;
import java.util.List;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String name,
        BuildingResponse building,
        FloorResponse floor,
        EntityStatus status,
        long version,
        List<SeatingArrangementResponse> seatingArrangements,
        List<UUID> equipmentTypeIds) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                BuildingResponse.from(room.getFloor().getBuilding()),
                FloorResponse.from(room.getFloor()),
                room.getStatus(),
                room.getVersion() == null ? 0 : room.getVersion(),
                room.getSeatingArrangements().stream().map(SeatingArrangementResponse::from).toList(),
                room.getEquipmentTypes().stream().map(at.mci.igp.raumlotse.domain.EquipmentType::getId).toList());
    }
}
