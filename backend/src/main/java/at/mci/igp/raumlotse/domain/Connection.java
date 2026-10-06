package at.mci.igp.raumlotse.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A named stairs or elevator passage spanning two or more maps via its {@link ConnectionPoint}s. */
@Entity
@Table(name = "connection")
public class Connection {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private ConnectionType type;

    @Version
    private Long version;

    @OneToMany(mappedBy = "connection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConnectionPoint> points = new ArrayList<>();

    protected Connection() {
    }

    public Connection(String name, ConnectionType type) {
        this.name = name;
        this.type = type;
    }

    public boolean isIncomplete() {
        return points.size() < 2;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConnectionType getType() {
        return type;
    }

    public void setType(ConnectionType type) {
        this.type = type;
    }

    public List<ConnectionPoint> getPoints() {
        return points;
    }
}
