package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Links a placed room to the waypoint where it is entered; at most one per room. */
@Entity
@Table(name = "room_path_attachment")
public class RoomPathAttachment {

    @Id
    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "map_id", nullable = false)
    private UUID mapId;

    @Column(name = "waypoint_id", nullable = false)
    private UUID waypointId;

    protected RoomPathAttachment() {
    }

    public RoomPathAttachment(UUID roomId, UUID mapId, UUID waypointId) {
        this.roomId = roomId;
        this.mapId = mapId;
        this.waypointId = waypointId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getMapId() {
        return mapId;
    }

    public UUID getWaypointId() {
        return waypointId;
    }
}
