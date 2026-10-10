package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.PermissionCode;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.repository.RolePermissionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class EffectivePermissionService {
    private static final String REQUEST_SNAPSHOT = EffectivePermissionService.class.getName() + ".snapshot";
    private record RequestSnapshot(UUID userId, Snapshot snapshot) {}
    public record Snapshot(List<Role> roles, Set<PermissionCode> permissions,
            boolean admin, boolean canUseAdminMode) {
        public boolean has(PermissionCode permission) {
            return permissions.contains(permission);
        }
    }

    private final RolePermissionRepository repository;

    public EffectivePermissionService(RolePermissionRepository repository) {
        this.repository = repository;
    }

    public Snapshot snapshot(UUID userId) {
        if (userId == null) {
            throw new UserRoleException(401, "AUTHENTICATION_REQUIRED", "Anmeldung erforderlich.");
        }
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            Object cached = servletAttributes.getRequest().getAttribute(REQUEST_SNAPSHOT);
            if (cached instanceof RequestSnapshot requestSnapshot && requestSnapshot.userId().equals(userId)) {
                return requestSnapshot.snapshot();
            }
        }
        return loadSnapshot(userId);
    }

    /** Loads and binds the authorization snapshot to this HTTP request for later controllers and services. */
    public Snapshot snapshot(UUID userId, HttpServletRequest request) {
        Snapshot current = loadSnapshot(userId);
        request.setAttribute(REQUEST_SNAPSHOT, new RequestSnapshot(userId, current));
        return current;
    }

    private Snapshot loadSnapshot(UUID userId) {
        if (userId == null) {
            throw new UserRoleException(401, "AUTHENTICATION_REQUIRED", "Anmeldung erforderlich.");
        }
        try {
            var current = repository.membership(userId);
            boolean admin = current.roles().contains(Role.ADMIN);
            boolean canUseAdminMode = admin || current.permissions().stream().anyMatch(PermissionCode::isManagement);
            return new Snapshot(current.roles(), current.permissions(), admin, canUseAdminMode);
        } catch (DataAccessException | IllegalArgumentException ex) {
            throw UserRoleException.unavailable();
        }
    }

    public void require(UUID userId, PermissionCode permission) {
        if (!snapshot(userId).has(permission)) {
            throw new UserRoleException(403, "PERMISSION_REQUIRED", "Sie dürfen diese Aktion nicht ausführen.");
        }
    }

    public void requireAdmin(UUID userId) {
        if (!snapshot(userId).admin()) {
            throw new UserRoleException(403, "ADMIN_REQUIRED", "Administratorrechte erforderlich.");
        }
    }

    /** Deliberately bypasses the request cache for lock-protected last-admin rechecks. */
    public void requireAdminFresh(UUID userId) {
        if (!loadSnapshot(userId).admin()) {
            throw new UserRoleException(403, "ADMIN_REQUIRED", "Administratorrechte erforderlich.");
        }
    }
}
