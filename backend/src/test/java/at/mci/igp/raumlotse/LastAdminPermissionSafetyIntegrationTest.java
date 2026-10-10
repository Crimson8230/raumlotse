package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.PermissionCode;
import com.jayway.jsonpath.JsonPath;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

/** The fixed role-management authority does not depend on configurable rights. */
@Transactional
class LastAdminPermissionSafetyIntegrationTest extends MapIntegrationSupport {
    @Test
    void adminWithNoConfigurableRightsStillManagesRolesButCannotDismissLastAdmin() throws Exception {
        db.update("delete from role_permission where role_code='ADMIN'");
        mvc.perform(get("/api/admin/users").with(actor(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/roles").with(actor(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/rooms").with(actor(admin)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));

        String before = mvc.perform(get("/api/admin/users/" + admin + "/roles").with(actor(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String version = JsonPath.read(before, "$.version");
        mvc.perform(put("/api/admin/users/" + admin + "/roles").with(actor(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"VIEWER\"],\"expectedVersion\":\"" + version + "\"}"))
                .andExpect(status().isConflict());
        assertThat(db.queryForList("select role_code from role_assignment where user_id=?", String.class, admin))
                .containsExactly("ADMIN");
    }

    @Test
    void everyConfigurableRightStillCannotGrantRoleManagementToNonAdmin() throws Exception {
        for (PermissionCode permission : PermissionCode.values()) {
            db.update("insert into role_permission(role_code,permission_code) values ('STUDENT',?) on conflict do nothing",
                    permission.name());
        }
        mvc.perform(get("/api/admin/users").with(actor(user)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
        mvc.perform(get("/api/admin/roles").with(actor(user)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
        mvc.perform(put("/api/admin/roles/STUDENT/permissions").with(actor(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[],\"expectedVersion\":\"0\"}"))
                .andExpect(status().isForbidden());
        String all = Arrays.toString(PermissionCode.values());
        assertThat(all).doesNotContain("ROLE_MANAGEMENT");
    }
}
