package at.mci.igp.raumlotse.dto;
import at.mci.igp.raumlotse.domain.Role;
import java.util.*;
public record UserRolesResponse(UserSummary user, List<Role> roles, String version, List<RoleOption> availableRoles) {
    public record RoleOption(Role code, String label) {}
    public static UserRolesResponse of(UserSummary user, List<Role> roles, long version) {
        return new UserRolesResponse(user, roles, Long.toString(version),
            Arrays.stream(Role.values()).map(r->new RoleOption(r,r.getLabel())).toList());
    }
}

