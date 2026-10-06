package at.mci.igp.raumlotse.controller;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.repository.RoleAssignmentRepository;
import at.mci.igp.raumlotse.service.*;
import java.util.List;
import java.util.UUID;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.service.AdminModeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class CurrentRolesController {
    private final RoleAssignmentRepository assignments;
    private final UserRoleReadinessCheck readiness;
    private final AdminModeService adminMode;
    public CurrentRolesController(RoleAssignmentRepository assignments,UserRoleReadinessCheck readiness,
            AdminModeService adminMode) {
        this.assignments=assignments; this.readiness=readiness; this.adminMode=adminMode;
    }
    public record Membership(List<Role> roles,boolean ready,boolean adminMode) {}
    @GetMapping("/api/auth/roles") public ResponseEntity<Membership> roles(Authentication actor,HttpServletRequest request) {
        UUID userId=RoleIdentityAdapter.actor(actor);
        List<Role> roles=assignments.roles(userId);
        boolean mode=adminMode.effective(request.getSession(false),new Actor(userId,roles.contains(Role.ADMIN)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Membership(roles,readiness.ready(),mode));
    }
}

