package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Connection;
import at.mci.igp.raumlotse.domain.ConnectionPoint;
import at.mci.igp.raumlotse.domain.ConnectionType;
import at.mci.igp.raumlotse.dto.ConnectionResponse;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ConnectionRepository;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ConnectionService {

    public record PointResult(ConnectionResponse connection, boolean created) {
    }

    private final ConnectionRepository connections;
    private final FloorMapRepository maps;

    public ConnectionService(ConnectionRepository connections, FloorMapRepository maps) {
        this.connections = connections;
        this.maps = maps;
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list() {
        return connections.findAllWithPoints().stream().map(ConnectionResponse::from).toList();
    }

    public Connection create(String name, ConnectionType type) {
        String trimmed = name.strip();
        if (connections.existsByNameIgnoreCase(trimmed)) {
            throw new ConflictException("A connection named '" + trimmed + "' already exists.");
        }
        return connections.save(new Connection(trimmed, type));
    }

    public Connection update(UUID id, String name, ConnectionType type) {
        Connection connection = findOrThrow(id);
        String trimmed = name.strip();
        if (connections.existsByNameIgnoreCaseAndIdNot(trimmed, id)) {
            throw new ConflictException("A connection named '" + trimmed + "' already exists.");
        }
        connection.setName(trimmed);
        connection.setType(type);
        return connection;
    }

    public void delete(UUID id) {
        connections.delete(findOrThrow(id));
    }

    /** Adds the connection's point on the map, or moves it when the connection already has one there. */
    public PointResult putPoint(UUID connectionId, UUID mapId, double x, double y) {
        requireFraction(x, "x");
        requireFraction(y, "y");
        Connection connection = findOrThrow(connectionId);
        var map = maps.findById(mapId).orElseThrow(() -> new NotFoundException("Map " + mapId + " not found."));
        var existing = connection.getPoints().stream()
                .filter(point -> point.getMap().getId().equals(mapId)).findFirst();
        boolean created = existing.isEmpty();
        if (created) {
            connection.getPoints().add(new ConnectionPoint(connection, map, x, y));
        } else {
            existing.get().moveTo(x, y);
        }
        return new PointResult(ConnectionResponse.from(connections.save(connection)), created);
    }

    public void removePoint(UUID connectionId, UUID mapId) {
        Connection connection = findOrThrow(connectionId);
        boolean removed = connection.getPoints().removeIf(point -> point.getMap().getId().equals(mapId));
        if (!removed) {
            throw new NotFoundException("Connection " + connectionId + " has no point on map " + mapId + ".");
        }
    }

    private Connection findOrThrow(UUID id) {
        return connections.findById(id).orElseThrow(() -> new NotFoundException("Connection " + id + " not found."));
    }

    private static void requireFraction(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Position " + name + " must be between 0 and 1.");
        }
    }
}
