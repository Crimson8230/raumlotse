package at.mci.igp.raumlotse.domain;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.*;
@Entity @Table(name="role_assignment") @IdClass(RoleAssignment.Key.class)
public class RoleAssignment {
    @Id @Column(name="user_id") private UUID userId;
    @Id @Enumerated(EnumType.STRING) @Column(name="role_code") private Role roleCode;
    protected RoleAssignment() {}
    public static class Key implements Serializable {
        public UUID userId;
        public Role roleCode;
        public Key() {}
        @Override public boolean equals(Object other) {
            return other instanceof Key k && Objects.equals(userId,k.userId) && roleCode==k.roleCode;
        }
        @Override public int hashCode() { return Objects.hash(userId,roleCode); }
    }
}

