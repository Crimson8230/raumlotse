package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Undirected walkway between two waypoints of one map; {@code aId} sorts before {@code bId} (canonical pair). */
@Entity
@Table(name = "path_segment")
public class PathSegment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "map_id", nullable = false, updatable = false)
    private UUID mapId;

    @Column(name = "a_id", nullable = false, updatable = false)
    private UUID aId;

    @Column(name = "b_id", nullable = false, updatable = false)
    private UUID bId;

    protected PathSegment() {
    }

    public PathSegment(UUID mapId, UUID aId, UUID bId) {
        this.mapId = mapId;
        this.aId = aId;
        this.bId = bId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMapId() {
        return mapId;
    }

    public UUID getAId() {
        return aId;
    }

    public UUID getBId() {
        return bId;
    }
}
