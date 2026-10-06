package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "floor_map_image")
public class FloorMapImage {

    @Id
    @Column(name = "map_id")
    private UUID mapId;

    @Column(nullable = false)
    private byte[] image;

    protected FloorMapImage() {
    }

    public FloorMapImage(UUID mapId, byte[] image) {
        this.mapId = mapId;
        this.image = image;
    }

    public UUID getMapId() {
        return mapId;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }
}
