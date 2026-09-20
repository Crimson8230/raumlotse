package at.mci.igp.raumlotse.domain;
import jakarta.persistence.*;
@Entity @Table(name="role_mutation_guard")
public class RoleMutationGuard {
    @Id private Integer id;
    protected RoleMutationGuard() {}
    public Integer getId() { return id; }
}

