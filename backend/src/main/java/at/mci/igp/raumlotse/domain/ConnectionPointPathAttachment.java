package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Links a connection point (stairs/elevator on a map) to a waypoint; at most one per point. */
@Entity
@Table(name = "connection_point_path_attachment")
public class ConnectionPointPathAttachment {

    @Id
    @Column(name = "connection_point_id")
    private UUID connectionPointId;

    @Column(name = "map_id", nullable = false)
    private UUID mapId;

    @Column(name = "waypoint_id", nullable = false)
    private UUID waypointId;

    protected ConnectionPointPathAttachment() {
    }

    public ConnectionPointPathAttachment(UUID connectionPointId, UUID mapId, UUID waypointId) {
        this.connectionPointId = connectionPointId;
        this.mapId = mapId;
        this.waypointId = waypointId;
    }

    public UUID getConnectionPointId() {
        return connectionPointId;
    }

    public UUID getMapId() {
        return mapId;
    }

    public UUID getWaypointId() {
        return waypointId;
    }
}
