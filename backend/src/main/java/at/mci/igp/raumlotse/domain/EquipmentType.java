package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "equipment_type")
public class EquipmentType {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private String name;

    @Column(unique = true, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    private EntityStatus status = EntityStatus.ACTIVE;

    protected EquipmentType() {
    }

    public EquipmentType(String name) {
        this.name = name;
        this.code = canonicalCode(name);
    }

    public EquipmentType(String name, String code) { this.name = name; this.code = code; }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    private static String canonicalCode(String name) {
        return name == null ? null : name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

    public EntityStatus getStatus() {
        return status;
    }

    public void setStatus(EntityStatus status) {
        this.status = status;
    }
}
