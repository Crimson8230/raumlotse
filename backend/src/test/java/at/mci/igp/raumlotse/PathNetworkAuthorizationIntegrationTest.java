package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/** Every path-network write needs MAP_MANAGE, every read needs READ, anonymous requests are denied. */
class PathNetworkAuthorizationIntegrationTest extends MapIntegrationSupport {

    final UUID any = UUID.randomUUID();
    static final String POS = "{\"x\":0.5,\"y\":0.5}";
    static final String REF = "{\"x1\":0.1,\"y1\":0.1,\"x2\":0.9,\"y2\":0.1,\"lengthMeters\":10}";
    static final String SEG = "{\"fromWaypointId\":\"" + UUID.randomUUID() + "\",\"toWaypointId\":\"" + UUID.randomUUID() + "\"}";
    static final String ATT = "{\"waypointId\":\"" + UUID.randomUUID() + "\"}";

    interface Call {
        AbstractMockHttpServletRequestBuilder<?> build();
    }

    List<Call> writes() {
        String m = "/api/maps/" + any;
        return List.of(
                () -> post(m + "/waypoints").contentType("application/json").content(POS),
                () -> put(m + "/waypoints/" + any).contentType("application/json").content(POS),
                () -> delete(m + "/waypoints/" + any),
                () -> post(m + "/segments").contentType("application/json").content(SEG),
                () -> delete(m + "/segments/" + any),
                () -> post(m + "/segments/" + any + "/split").contentType("application/json").content(POS),
                () -> put(m + "/reference-segment").contentType("application/json").content(REF),
                () -> delete(m + "/reference-segment"),
                () -> put(m + "/room-attachments/" + any).contentType("application/json").content(ATT),
                () -> delete(m + "/room-attachments/" + any),
                () -> put(m + "/connection-point-attachments/" + any).contentType("application/json").content(ATT),
                () -> delete(m + "/connection-point-attachments/" + any));
    }

    @Test
    void unauthenticatedWritesAreDeniedWith401() throws Exception {
        for (Call call : writes()) {
            mvc.perform(call.build().with(csrf())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void usersWithoutMapManageGet403() throws Exception {
        for (Call call : writes()) {
            mvc.perform(call.build().with(actor(user)).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
        }
    }

    @Test
    void adminPassesThePermissionGate() throws Exception {
        for (Call call : writes()) {
            int status = mvc.perform(call.build().with(actor(admin)).with(csrf())).andReturn().getResponse().getStatus();
            assertThat(status).isNotIn(401, 403);
        }
    }

    @Test
    void roleGrantedMapManageCanWrite() throws Exception {
        db.update("insert into role_permission(role_code, permission_code) values ('STUDENT', 'MAP_MANAGE')");
        try {
            for (Call call : writes()) {
                int status = mvc.perform(call.build().with(actor(user)).with(csrf())).andReturn().getResponse().getStatus();
                assertThat(status).isNotIn(401, 403);
            }
        } finally {
            db.update("delete from role_permission where role_code='STUDENT' and permission_code='MAP_MANAGE'");
        }
    }

    @Test
    void readsNeedASignedInSessionWithReadPermission() throws Exception {
        UUID map = uploadMap(floorEg);
        mvc.perform(get("/api/path-network")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/maps/" + map + "/path-network")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/path-network").with(actor(user))).andExpect(status().isOk());
        mvc.perform(get("/api/maps/" + map + "/path-network").with(actor(user))).andExpect(status().isOk());
        mvc.perform(get("/api/maps/" + any + "/path-network").with(actor(user))).andExpect(status().isNotFound());
    }
}
