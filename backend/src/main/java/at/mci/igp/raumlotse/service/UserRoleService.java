package at.mci.igp.raumlotse.service;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.dto.*;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class UserRoleService {
    private final UserRoleSafety safety;
    private final UserRoleStateRepository states;
    private final RoleAssignmentRepository assignments;
    private final RoleIdentityAdapter identity;
    public UserRoleService(UserRoleSafety safety,UserRoleStateRepository states,RoleAssignmentRepository assignments,RoleIdentityAdapter identity) {
        this.safety=safety; this.states=states; this.assignments=assignments; this.identity=identity;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public UserListResponse list(UUID actor,String query,int page,int size) {
        safety.requireAdmin(actor); safety.requireReady(); return identity.list(query,page,size);
    }
    @Transactional(readOnly=true)
    public UserRolesResponse read(UUID actor,String id) {
        safety.requireAdmin(actor); safety.requireReady(); return states.snapshot(identity.target(id));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public UserRolesResponse replace(UUID actor,String id,UserRoleUpdateRequest request) {
        safety.lock();
        safety.requireAdmin(actor);
        safety.requireReady();
        UUID target=identity.target(id);
        var current=states.snapshot(target);
        var selection=safety.selection(request);
        long version=safety.expectedVersion(request);
        if(!current.version().equals(Long.toString(version)))
            throw new UserRoleException(409,"STALE_ROLES","The roles changed. Review the latest roles before saving again.");
        if(current.roles().contains(Role.ADMIN) && !selection.contains(Role.ADMIN) && assignments.otherAdmins(target)==0)
            throw new UserRoleException(409,"LAST_ADMIN_REQUIRED","At least one administrator must remain.");
        if(!new HashSet<>(current.roles()).equals(selection)) {
            if(version==Long.MAX_VALUE) throw UserRoleException.unavailable();
            assignments.replace(target,selection);
        }
        return states.snapshot(target);
    }
}

