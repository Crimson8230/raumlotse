package at.mci.igp.raumlotse.controller;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.domain.PermissionCode;
import at.mci.igp.raumlotse.service.*;
import java.util.List;
import java.util.Arrays;
import java.util.UUID;
import at.mci.igp.raumlotse.service.AdminModeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class CurrentRolesController {
    private final EffectivePermissionService permissions;
    private final UserRoleReadinessCheck readiness;
    private final AdminModeService adminMode;
    public CurrentRolesController(EffectivePermissionService permissions,UserRoleReadinessCheck readiness,
            AdminModeService adminMode) {
        this.permissions=permissions; this.readiness=readiness; this.adminMode=adminMode;
    }
    public record Membership(List<Role> roles,boolean ready,boolean adminMode,
            List<PermissionCode> permissions,boolean canUseAdminMode) {}
    @GetMapping("/api/auth/roles") public ResponseEntity<Membership> roles(Authentication actor,HttpServletRequest request) {
        UUID userId=RoleIdentityAdapter.actor(actor);
        var snapshot=permissions.snapshot(userId);
        boolean mode=adminMode.effective(request.getSession(false),snapshot.canUseAdminMode());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Membership(snapshot.roles(),readiness.ready(),mode,
                    Arrays.stream(PermissionCode.values()).filter(snapshot.permissions()::contains).toList(),
                    snapshot.canUseAdminMode()));
    }
}

