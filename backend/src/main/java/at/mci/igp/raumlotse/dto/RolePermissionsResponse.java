package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.PermissionCode;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.repository.RolePermissionRepository;
import java.util.Arrays;
import java.util.List;

public record RolePermissionsResponse(RoleOption role, List<PermissionCode> permissions,
        String version, List<PermissionOption> availablePermissions, boolean protectedRoleManagement) {
    public record RoleOption(Role code, String label) {
        public static RoleOption of(Role role) {
            return new RoleOption(role, role.getLabel());
        }
    }

    public record PermissionOption(PermissionCode code, String label, String description) {
    }

    public static RolePermissionsResponse of(RolePermissionRepository.RoleSnapshot snapshot) {
        return new RolePermissionsResponse(RoleOption.of(snapshot.role()),
                Arrays.stream(PermissionCode.values()).filter(snapshot.permissions()::contains).toList(),
                Long.toString(snapshot.version()),
                Arrays.stream(PermissionCode.values())
                        .map(p -> new PermissionOption(p, p.getLabel(), p.getDescription())).toList(),
                snapshot.role() == Role.ADMIN);
    }
}
