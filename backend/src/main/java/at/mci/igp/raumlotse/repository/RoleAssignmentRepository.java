package at.mci.igp.raumlotse.repository;
import at.mci.igp.raumlotse.domain.Role;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class RoleAssignmentRepository {
    private final JdbcTemplate db;
    public RoleAssignmentRepository(JdbcTemplate db) { this.db=db; }
    public List<Role> roles(UUID id) {
        return db.queryForList("select role_code from role_assignment where user_id=?",String.class,id)
            .stream().map(Role::valueOf).sorted().toList();
    }
    public long otherAdmins(UUID id) {
        return db.queryForObject("select count(*) from role_assignment where role_code='ADMIN' and user_id<>?",Long.class,id);
    }
    public void replace(UUID id, Set<Role> roles) {
        db.update("delete from role_assignment where user_id=?",id);
        for (Role role: roles) db.update("insert into role_assignment(user_id,role_code) values (?,?)",id,role.name());
        db.update("update user_role_state set roles_version=roles_version+1 where user_id=?",id);
    }
}

