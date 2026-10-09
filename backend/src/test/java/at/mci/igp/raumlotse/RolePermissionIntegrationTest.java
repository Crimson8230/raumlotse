package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RolePermissionIntegrationTest extends MapIntegrationSupport {
    private String path = "/api/admin/roles/UNIVERSITY_STAFF/permissions";

    private String update(String version, String permissions) {
        return "{\"expectedVersion\":\"" + version + "\",\"permissions\":" + permissions + "}";
    }

    @Test
    void adminCanReplaceACompleteSelectionAndNewRightWorksImmediately() throws Exception {
        mvc.perform(get("/api/admin/roles").with(actor(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(5));
        String initial = mvc.perform(get(path).with(actor(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availablePermissions.length()").value(14))
                .andReturn().getResponse().getContentAsString();
        String version = JsonPath.read(initial, "$.version");
        mvc.perform(post("/api/buildings").with(actor(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Before\"}"))
                .andExpect(status().isForbidden());
        String body = mvc.perform(put(path).with(actor(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(version, "[\"READ\",\"BUILDING_MANAGE\"]")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String next = JsonPath.read(body, "$.version");
        assertThat(Long.parseLong(next)).isEqualTo(Long.parseLong(version) + 1);
        db.update("delete from role_assignment where user_id=?", user);
        db.update("insert into role_assignment(user_id,role_code) values (?, 'UNIVERSITY_STAFF')", user);
        mvc.perform(post("/api/buildings").with(actor(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Allowed\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put(path).with(actor(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(version, "[\"READ\",\"BUILDING_MANAGE\"]")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_ROLE_PERMISSIONS"));
        mvc.perform(put(path).with(actor(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update(next, "[\"READ\",\"BUILDING_MANAGE\"]")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(next));
    }

    @Test
    void invalidSelectionsAndNonAdminAccessNeverChangeStoredRights() throws Exception {
        String before = mvc.perform(get(path).with(actor(admin))).andReturn().getResponse().getContentAsString();
        String version = JsonPath.read(before, "$.version");
        mvc.perform(get(path).with(actor(user))).andExpect(status().isForbidden());
        mvc.perform(put(path).with(actor(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(update(version, "[]"))).andExpect(status().isForbidden());
        for (String selection : new String[] {"[\"BUILDING_MANAGE\"]", "[\"READ\",\"READ\"]",
                "[\"READ\",\"UNKNOWN\"]", "[\"READ\",\"ROLE_MANAGEMENT\"]"}) {
            mvc.perform(put(path).with(actor(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(update(version, selection)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/roles/NOT_A_ROLE/permissions").with(actor(admin)))
                .andExpect(status().isNotFound());
        String after = mvc.perform(get(path).with(actor(admin))).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(after, "$.version")).isEqualTo(version);
        assertThat(JsonPath.<java.util.List<String>>read(after, "$.permissions"))
                .containsExactlyInAnyOrderElementsOf(JsonPath.read(before, "$.permissions"));
        mvc.perform(put(path).with(actor(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(update(version, "[]")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions.length()").value(0));
    }
}
