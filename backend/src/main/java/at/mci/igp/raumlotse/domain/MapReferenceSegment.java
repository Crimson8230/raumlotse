package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** The reference segment of a map with its real length in meters; the map scale is derived from it. */
@Entity
@Table(name = "map_reference_segment")
public class MapReferenceSegment {

    @Id
    @Column(name = "map_id")
    private UUID mapId;

    @Column(nullable = false)
    private double x1;

    @Column(nullable = false)
    private double y1;

    @Column(nullable = false)
    private double x2;

    @Column(nullable = false)
    private double y2;

    @Column(name = "length_m", nullable = false)
    private double lengthM;

    protected MapReferenceSegment() {
    }

    public MapReferenceSegment(UUID mapId, double x1, double y1, double x2, double y2, double lengthM) {
        this.mapId = mapId;
        update(x1, y1, x2, y2, lengthM);
    }

    public void update(double x1, double y1, double x2, double y2, double lengthM) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.lengthM = lengthM;
    }

    public UUID getMapId() {
        return mapId;
    }

    public double getX1() {
        return x1;
    }

    public double getY1() {
        return y1;
    }

    public double getX2() {
        return x2;
    }

    public double getY2() {
        return y2;
    }

    public double getLengthM() {
        return lengthM;
    }
}
