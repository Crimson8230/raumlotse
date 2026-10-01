package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "room_device_state")
public class RoomDeviceState {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private RoomDeviceKind kind;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(nullable = false)
    private boolean state;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomDeviceState() { }
    public RoomDeviceState(UUID roomId, RoomDeviceKind kind) { this.roomId = roomId; this.kind = kind; this.updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public RoomDeviceKind getKind() { return kind; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isState() { return state; }
    public void setState(boolean state) { this.state = state; }
    public Instant getUpdatedAt() { return updatedAt; }
}
