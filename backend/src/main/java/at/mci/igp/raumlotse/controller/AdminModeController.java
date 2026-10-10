package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.service.AdminModeService;
import at.mci.igp.raumlotse.service.EffectivePermissionService;
import at.mci.igp.raumlotse.service.RoleIdentityAdapter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminModeController {

    public record AdminModeRequest(Boolean enabled) {
    }

    public record AdminModeResponse(boolean adminMode) {
    }

    private final AdminModeService adminMode;
    private final EffectivePermissionService permissions;

    public AdminModeController(AdminModeService adminMode, EffectivePermissionService permissions) {
        this.adminMode = adminMode;
        this.permissions = permissions;
    }

    @PutMapping("/api/auth/admin-mode")
    public ResponseEntity<AdminModeResponse> set(@RequestBody AdminModeRequest request, Authentication authentication,
            HttpServletRequest http) {
        var userId = RoleIdentityAdapter.actor(authentication);
        if (request == null || request.enabled() == null) {
            throw new UserRoleException(400, "INVALID_REQUEST", "Ein gültiger Wert für „enabled“ ist erforderlich.");
        }
        boolean result = adminMode.set(http.getSession(), userId,
                permissions.snapshot(userId).canUseAdminMode(), request.enabled());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AdminModeResponse(result));
    }
}
