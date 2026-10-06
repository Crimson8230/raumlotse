package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "connection_point")
public class ConnectionPoint {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false, updatable = false)
    private Connection connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "map_id", nullable = false, updatable = false)
    private FloorMap map;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    protected ConnectionPoint() {
    }

    public ConnectionPoint(Connection connection, FloorMap map, double x, double y) {
        this.connection = connection;
        this.map = map;
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

    public Connection getConnection() {
        return connection;
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
