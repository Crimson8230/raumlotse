package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Floor-plan image metadata of one floor; the image bytes are stored in {@link FloorMapImage}. */
@Entity
@Table(name = "floor_map")
public class FloorMap {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false, updatable = false)
    private Floor floor;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "width_px", nullable = false)
    private int widthPx;

    @Column(name = "height_px", nullable = false)
    private int heightPx;

    @Column(name = "image_version", nullable = false)
    private long imageVersion = 1;

    @Version
    private Long version;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    protected FloorMap() {
    }

    public FloorMap(Floor floor, String contentType, int widthPx, int heightPx) {
        this.floor = floor;
        this.contentType = contentType;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    /** Replaces the image metadata and bumps the image version (cache key). */
    public void replaceImage(String contentType, int widthPx, int heightPx) {
        this.contentType = contentType;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.imageVersion++;
    }

    public UUID getId() {
        return id;
    }

    public Floor getFloor() {
        return floor;
    }

    public String getContentType() {
        return contentType;
    }

    public int getWidthPx() {
        return widthPx;
    }

    public int getHeightPx() {
        return heightPx;
    }

    public long getImageVersion() {
        return imageVersion;
    }

    public String getName() {
        return floor.getBuilding().getName() + " – " + floor.getName();
    }
}
