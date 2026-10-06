package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.RoomPlacement;

public record PlacementResponse(RoomRefResponse room, double x, double y) {

    public static PlacementResponse from(RoomPlacement placement) {
        return new PlacementResponse(RoomRefResponse.from(placement.getRoom()), placement.getX(), placement.getY());
    }
}
