package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "building")
public class Building {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private EntityStatus status = EntityStatus.ACTIVE;

    @Column(name = "has_elevator", nullable = false)
    private boolean hasElevator;

    protected Building() {
    }

    public Building(String name) {
        this.name = name;
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

    public EntityStatus getStatus() {
        return status;
    }

    public void setStatus(EntityStatus status) {
        this.status = status;
    }

    public boolean hasElevator() {
        return hasElevator;
    }

    public void setHasElevator(boolean hasElevator) {
        this.hasElevator = hasElevator;
    }
}
