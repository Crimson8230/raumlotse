package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A point of the walkway network on one map, as fractions (0..1) of the image width and height. */
@Entity
@Table(name = "path_waypoint")
public class PathWaypoint {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "map_id", nullable = false, updatable = false)
    private UUID mapId;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    protected PathWaypoint() {
    }

    public PathWaypoint(UUID mapId, double x, double y) {
        this.mapId = mapId;
        this.x = x;
        this.y = y;
    }

    public void moveTo(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMapId() {
        return mapId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
