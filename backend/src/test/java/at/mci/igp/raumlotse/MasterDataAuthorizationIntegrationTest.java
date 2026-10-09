package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Master data and maintenance endpoints require their assigned role permissions. */
class MasterDataAuthorizationIntegrationTest extends MapIntegrationSupport {

    @Test
    void regularUserCannotChangeMasterDataAndNothingChanges() throws Exception {
        long buildings = count("building");
        long floors = count("floor");
        long rooms = count("room");

        mvc.perform(post("/api/buildings").with(actor(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Neu\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
        mvc.perform(put("/api/buildings/" + building).with(actor(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Umbenannt\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/buildings/" + building + "/deactivate").with(actor(user)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/floors/" + floorEg).with(actor(user)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/equipment-types").with(actor(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Beamer 2\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/rooms/" + roomEg + "/deactivate").with(actor(user)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/rooms/" + roomEg).with(actor(user)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reservations/expire-unattended").with(actor(user)).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(count("building")).isEqualTo(buildings);
        assertThat(count("floor")).isEqualTo(floors);
        assertThat(count("room")).isEqualTo(rooms);
        assertThat(db.queryForObject("select name from building where id = ?", String.class, building))
                .isEqualTo("Haus A");
        assertThat(db.queryForObject("select status from room where id = ?", String.class, roomEg))
                .isEqualTo("ACTIVE");
    }

    @Test
    void administratorChangesMasterDataWithoutAnyModeFlag() throws Exception {
        mvc.perform(post("/api/buildings").with(actor(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Haus B\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/reservations/expire-unattended").with(actor(admin)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void everySignedInUserCanStillReadMasterData() throws Exception {
        mvc.perform(get("/api/rooms").with(actor(user))).andExpect(status().isOk());
        mvc.perform(get("/api/buildings").with(actor(user))).andExpect(status().isOk());
        mvc.perform(get("/api/equipment-types").with(actor(user))).andExpect(status().isOk());
        mvc.perform(get("/api/maps").with(actor(user))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").with(actor(user))).andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestsAreStillRefused() throws Exception {
        mvc.perform(post("/api/buildings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rooms/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private long count(String table) {
        return db.queryForObject("select count(*) from " + table, Long.class);
    }

    @Test
    void validationMessagesAreGerman() throws Exception {
        mvc.perform(post("/api/rooms/" + roomEg + "/reservations").with(actor(user)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='startTime')].message").value(
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("darf nicht"))));
        mvc.perform(post("/api/buildings").with(actor(admin)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Der Name darf nicht leer sein"));
    }
}
