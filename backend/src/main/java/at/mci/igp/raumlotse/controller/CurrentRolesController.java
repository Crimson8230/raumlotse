package at.mci.igp.raumlotse.controller;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.repository.RoleAssignmentRepository;
import at.mci.igp.raumlotse.service.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class CurrentRolesController {
    private final RoleAssignmentRepository assignments;
    private final UserRoleReadinessCheck readiness;
    public CurrentRolesController(RoleAssignmentRepository assignments,UserRoleReadinessCheck readiness) {
        this.assignments=assignments; this.readiness=readiness;
    }
    public record Membership(List<Role> roles,boolean ready) {}
    @GetMapping("/api/auth/roles") public ResponseEntity<Membership> roles(Authentication actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Membership(assignments.roles(RoleIdentityAdapter.actor(actor)),readiness.ready()));
    }
}

