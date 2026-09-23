package at.mci.igp.raumlotse.domain;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="user_role_state")
public class UserRoleState {
    @Id @Column(name="user_id") private UUID userId;
    @Column(name="roles_version", nullable=false) private long rolesVersion;
    protected UserRoleState() {}
    public UUID getUserId() { return userId; }
    public long getRolesVersion() { return rolesVersion; }
}

