package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.PermissionCode;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.service.EffectivePermissionService;
import java.util.Arrays;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class EffectivePermissionIntegrationTest extends MapIntegrationSupport {
    @Autowired EffectivePermissionService permissions;

    @Test
    void allThirtyOneNonEmptyRoleCombinationsUseOnlyTheCurrentUnion() throws Exception {
        Role[] roles = Role.values();
        for (int mask = 1; mask < 1 << roles.length; mask++) {
            db.update("delete from role_assignment where user_id=?", user);
            var expected = EnumSet.noneOf(PermissionCode.class);
            var assigned = EnumSet.noneOf(Role.class);
            for (int bit = 0; bit < roles.length; bit++) {
                if ((mask & (1 << bit)) == 0) continue;
                Role role = roles[bit];
                assigned.add(role);
                db.update("insert into role_assignment(user_id,role_code) values (?,?)", user, role.name());
                PermissionTestData.INITIAL.get(role.name()).stream().map(PermissionCode::valueOf)
                        .forEach(expected::add);
            }
            var actual = permissions.snapshot(user);
            assertThat(actual.roles()).containsExactlyInAnyOrderElementsOf(assigned);
            assertThat(actual.permissions()).containsExactlyInAnyOrderElementsOf(expected);
            assertThat(actual.admin()).isEqualTo(assigned.contains(Role.ADMIN));
            assertThat(actual.canUseAdminMode()).isEqualTo(assigned.contains(Role.ADMIN)
                    || expected.stream().anyMatch(PermissionCode::isManagement));
        }
    }

    @Test
    void currentRolesAddsPermissionsAndRevocationAppliesOnTheNextRequest() throws Exception {
        db.update("delete from role_assignment where user_id=?", user);
        db.update("insert into role_assignment(user_id,role_code) values (?, 'VIEWER')", user);
        long assignmentVersion = db.queryForObject(
                "select roles_version from user_role_state where user_id=?", Long.class, user);
        mvc.perform(get("/api/auth/roles").with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"))
                .andExpect(jsonPath("$.permissions[0]").value("READ"))
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andExpect(jsonPath("$.canUseAdminMode").value(false));
        db.update("delete from role_permission where role_code='VIEWER' and permission_code='READ'");
        try {
            mvc.perform(get("/api/rooms").with(actor(user)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
            mvc.perform(get("/api/auth/roles").with(actor(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.permissions.length()").value(0));
            assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?", Long.class, user))
                    .isEqualTo(assignmentVersion);
        } finally {
            db.update("insert into role_permission(role_code,permission_code) values ('VIEWER','READ')");
        }
    }
}
