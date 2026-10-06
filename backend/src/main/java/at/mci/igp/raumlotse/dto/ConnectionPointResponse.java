package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.ConnectionPoint;
import java.util.UUID;

public record ConnectionPointResponse(UUID mapId, double x, double y) {

    public static ConnectionPointResponse from(ConnectionPoint point) {
        return new ConnectionPointResponse(point.getMap().getId(), point.getX(), point.getY());
    }
}
