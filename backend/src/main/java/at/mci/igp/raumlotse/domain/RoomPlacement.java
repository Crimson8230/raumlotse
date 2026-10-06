package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** Position of one room on a map, as fractions (0..1) of the image width and height. */
@Entity
@Table(name = "room_placement")
public class RoomPlacement {

    @Id
    @Column(name = "room_id")
    private UUID roomId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", nullable = false)
    private FloorMap map;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    protected RoomPlacement() {
    }

    public RoomPlacement(Room room, FloorMap map, double x, double y) {
        this.room = room;
        this.map = map;
        this.x = x;
        this.y = y;
    }

    public void moveTo(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public Room getRoom() {
        return room;
    }

    public FloorMap getMap() {
        return map;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
