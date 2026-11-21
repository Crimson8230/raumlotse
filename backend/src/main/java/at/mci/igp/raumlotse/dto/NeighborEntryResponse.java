package at.mci.igp.raumlotse.dto;

import java.util.UUID;

/**
 * One neighbor of a room. {@code roomId} is set for rooms, {@code connectionId} and {@code targetFloorName} for
 * stairs and elevators. {@code status} (AVAILABLE, RESERVED, OCCUPIED) is only given for rooms.
 */
public record NeighborEntryResponse(String direction, String kind, UUID roomId, UUID connectionId, String name,
        String targetFloorName, double distanceMeters, int walkingSeconds, String status) {
}
