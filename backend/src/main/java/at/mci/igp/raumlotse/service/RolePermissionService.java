package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.PermissionCode;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.dto.RolePermissionsResponse;
import at.mci.igp.raumlotse.dto.RolePermissionsUpdateRequest;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.repository.RolePermissionRepository;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolePermissionService {
    private final RolePermissionRepository repository;
    private final EffectivePermissionService effective;

    public RolePermissionService(RolePermissionRepository repository, EffectivePermissionService effective) {
        this.repository = repository;
        this.effective = effective;
    }

    public List<RolePermissionsResponse.RoleOption> roles(UUID actor) {
        effective.requireAdmin(actor);
        return Arrays.stream(Role.values()).map(RolePermissionsResponse.RoleOption::of).toList();
    }

    @Transactional(readOnly = true)
    public RolePermissionsResponse read(UUID actor, String roleCode) {
        effective.requireAdmin(actor);
        return RolePermissionsResponse.of(repository.snapshot(role(roleCode)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RolePermissionsResponse replace(UUID actor, String roleCode, RolePermissionsUpdateRequest request) {
        effective.requireAdmin(actor);
        Role role = role(roleCode);
        long version = repository.lock(role);
        effective.requireAdminFresh(actor);
        long expected = expectedVersion(request);
        if (version != expected) {
            throw new UserRoleException(409, "STALE_ROLE_PERMISSIONS",
                    "Die Rollenrechte wurden geändert. Bitte laden Sie den aktuellen Stand neu.");
        }
        Set<PermissionCode> selection = selection(request);
        var current = repository.snapshot(role);
        if (!current.permissions().equals(selection)) {
            if (version == Long.MAX_VALUE) throw UserRoleException.unavailable();
            repository.replace(role, selection);
        }
        return RolePermissionsResponse.of(repository.snapshot(role));
    }

    private Role role(String code) {
        try {
            return Role.valueOf(code);
        } catch (IllegalArgumentException ex) {
            throw new UserRoleException(404, "ROLE_NOT_FOUND", "Die Rolle wurde nicht gefunden.");
        }
    }

    private long expectedVersion(RolePermissionsUpdateRequest request) {
        try {
            String value = request.expectedVersion();
            if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NullPointerException | NumberFormatException ex) {
            throw new UserRoleException(400, "INVALID_REQUEST", "Eine gültige erwartete Version ist erforderlich.");
        }
    }

    private Set<PermissionCode> selection(RolePermissionsUpdateRequest request) {
        if (request.permissions() == null || request.permissions().size() > PermissionCode.values().length)
            throw invalidSelection();
        var result = EnumSet.noneOf(PermissionCode.class);
        for (String code : request.permissions()) {
            try {
                if (code == null || !result.add(PermissionCode.valueOf(code))) throw invalidSelection();
            } catch (IllegalArgumentException ex) {
                throw invalidSelection();
            }
        }
        if (!result.isEmpty() && !result.contains(PermissionCode.READ))
            throw new UserRoleException(400, "INVALID_PERMISSION_SELECTION",
                    "Für jedes weitere Recht muss Lesen in derselben Rolle aktiviert sein.");
        return result;
    }

    private UserRoleException invalidSelection() {
        return new UserRoleException(400, "INVALID_PERMISSION_SELECTION", "Die Rechteauswahl ist ungültig.");
    }
}
