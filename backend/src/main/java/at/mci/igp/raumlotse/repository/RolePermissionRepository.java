package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.PermissionCode;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.exception.UserRoleException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RolePermissionRepository {
    public record Membership(List<Role> roles, Set<PermissionCode> permissions) {
    }

    public record RoleSnapshot(Role role, long version, Set<PermissionCode> permissions) {
    }

    private final JdbcTemplate db;

    public RolePermissionRepository(JdbcTemplate db) {
        this.db = db;
    }

    /** One statement provides a coherent view of assigned roles and their current rights. */
    public Membership membership(UUID userId) {
        return db.query("""
                with expected(role_code) as (
                    values ('ADMIN'),('UNIVERSITY_STAFF'),('STUDENT'),('LECTURER'),('VIEWER')
                ), configuration as (
                    select (select count(*) from role_permission_state)=5
                        and not exists (select 1 from expected e left join role_permission_state s
                            on s.role_code=e.role_code where s.role_code is null)
                        and not exists (select 1 from role_permission p left join role_permission_state s
                            on s.role_code=p.role_code where s.role_code is null)
                        and not exists (select 1 from role_permission_state s where
                            s.permissions_version < 0 or
                            (exists (select 1 from role_permission p where p.role_code=s.role_code)
                                and not exists (select 1 from role_permission p where p.role_code=s.role_code
                                    and p.permission_code='READ')))
                        as valid
                )
                select a.role_code, s.role_code as configured_role, p.permission_code, c.valid as configuration_valid
                from configuration c
                left join role_assignment a on a.user_id=?
                left join role_permission_state s on s.role_code=a.role_code
                left join role_permission p on p.role_code=a.role_code
                order by a.role_code,p.permission_code
                """, rs -> {
            var roles = EnumSet.noneOf(Role.class);
            var permissions = EnumSet.noneOf(PermissionCode.class);
            var byRole = new EnumMap<Role, EnumSet<PermissionCode>>(Role.class);
            while (rs.next()) {
                if (!rs.getBoolean("configuration_valid")) {
                    throw UserRoleException.unavailable();
                }
                if (rs.getString("role_code") == null) continue;
                if (rs.getString("configured_role") == null) {
                    throw UserRoleException.unavailable();
                }
                Role role = Role.valueOf(rs.getString("role_code"));
                roles.add(role);
                var selected = byRole.computeIfAbsent(role, ignored -> EnumSet.noneOf(PermissionCode.class));
                String permission = rs.getString("permission_code");
                if (permission != null) {
                    PermissionCode code = PermissionCode.valueOf(permission);
                    permissions.add(code);
                    selected.add(code);
                }
            }
            if (roles.isEmpty()) {
                throw UserRoleException.unavailable();
            }
            for (var selected : byRole.values()) {
                if (!selected.isEmpty() && !selected.contains(PermissionCode.READ))
                    throw UserRoleException.unavailable();
            }
            return new Membership(List.copyOf(roles), Set.copyOf(permissions));
        }, userId);
    }

    public RoleSnapshot snapshot(Role role) {
        return db.query("""
                select s.permissions_version,p.permission_code
                from role_permission_state s
                left join role_permission p on p.role_code=s.role_code
                where s.role_code=? order by p.permission_code
                """, rs -> {
            Long version = null;
            var permissions = EnumSet.noneOf(PermissionCode.class);
            while (rs.next()) {
                version = rs.getLong("permissions_version");
                String permission = rs.getString("permission_code");
                if (permission != null) {
                    permissions.add(PermissionCode.valueOf(permission));
                }
            }
            if (version == null) {
                throw UserRoleException.unavailable();
            }
            return new RoleSnapshot(role, version, Set.copyOf(permissions));
        }, role.name());
    }

    public long lock(Role role) {
        Long version = db.queryForObject(
                "select permissions_version from role_permission_state where role_code=? for update",
                Long.class, role.name());
        if (version == null) {
            throw UserRoleException.unavailable();
        }
        return version;
    }

    public void replace(Role role, Set<PermissionCode> permissions) {
        db.update("delete from role_permission where role_code=?", role.name());
        for (PermissionCode permission : PermissionCode.values()) {
            if (permissions.contains(permission)) {
                db.update("insert into role_permission(role_code,permission_code) values (?,?)",
                        role.name(), permission.name());
            }
        }
        db.update("update role_permission_state set permissions_version=permissions_version+1 where role_code=?",
                role.name());
    }
}
