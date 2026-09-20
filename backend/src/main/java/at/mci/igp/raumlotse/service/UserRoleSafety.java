package at.mci.igp.raumlotse.service;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.dto.UserRoleUpdateRequest;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.repository.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class UserRoleSafety {
    private final RoleAssignmentRepository assignments;
    private final RoleMutationGuardRepository guard;
    private final UserRoleReadinessCheck readiness;
    private final JdbcTemplate db;
    public UserRoleSafety(RoleAssignmentRepository assignments,RoleMutationGuardRepository guard,
            UserRoleReadinessCheck readiness,JdbcTemplate db) {
        this.assignments=assignments; this.guard=guard; this.readiness=readiness; this.db=db;
    }
    public void requireAdmin(UUID actor) {
        if(actor==null) throw new UserRoleException(401,"AUTHENTICATION_REQUIRED","Authentication is required.");
        if(!assignments.roles(actor).contains(Role.ADMIN))
            throw new UserRoleException(403,"ADMIN_REQUIRED","Administrator access is required.");
    }
    public void requireReady() { if(!readiness.ready()) throw UserRoleException.unavailable(); }
    public void lock() {
        db.queryForObject("select set_config('lock_timeout','5s',true)",String.class);
        if(guard.lockGuard()==null) throw UserRoleException.unavailable();
    }
    public Set<Role> selection(UserRoleUpdateRequest request) {
        if(request==null || request.roles()==null || request.roles().isEmpty() || request.roles().size()>5)
            throw invalidSelection();
        EnumSet<Role> result=EnumSet.noneOf(Role.class);
        for(String value:request.roles()) {
            try { if(value==null || !result.add(Role.valueOf(value))) throw invalidSelection(); }
            catch(IllegalArgumentException ex) { throw invalidSelection(); }
        }
        return result;
    }
    public long expectedVersion(UserRoleUpdateRequest request) {
        try {
            String value=request.expectedVersion();
            if(value==null || !value.matches("0|[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch(NumberFormatException ex) {
            throw new UserRoleException(400,"INVALID_REQUEST","A valid expected version is required.");
        }
    }
    private UserRoleException invalidSelection() {
        return new UserRoleException(400,"INVALID_ROLE_SELECTION","Select between one and five distinct supported roles.");
    }
}

