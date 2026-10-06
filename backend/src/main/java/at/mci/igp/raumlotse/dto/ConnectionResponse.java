package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.Connection;
import at.mci.igp.raumlotse.domain.ConnectionType;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ConnectionResponse(UUID id, String name, ConnectionType type, boolean incomplete,
        List<ConnectionPointResponse> points) {

    public static ConnectionResponse from(Connection connection) {
        return new ConnectionResponse(connection.getId(), connection.getName(), connection.getType(),
                connection.isIncomplete(),
                connection.getPoints().stream()
                        .map(ConnectionPointResponse::from)
                        .sorted(Comparator.comparing(p -> p.mapId().toString()))
                        .toList());
    }
}
