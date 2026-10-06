package at.mci.igp.raumlotse;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class MapAuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate db;

    UUID admin;
    UUID user;
    final UUID any = UUID.randomUUID();

    @BeforeEach
    void fixtures() {
        db.update("delete from role_assignment");
        db.update("delete from user_role_state");
        db.update("delete from user_account");
        admin = account("admin@example.test", "ADMIN");
        user = account("user@example.test", "STUDENT");
    }

    UUID account(String email, String role) {
        UUID id = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
                id, email, "Fixture", "{pbkdf2-sha256-600000-v1}test-fixture-not-a-real-password");
        db.update("insert into user_role_state(user_id) values (?)", id);
        db.update("insert into role_assignment(user_id,role_code) values (?,?)", id, role);
        return id;
    }

    RequestPostProcessor actor(UUID id) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(id, "Fixture"), null, List.of()));
    }

    interface Call {
        org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?> build();
    }

    List<Call> writes() {
        return List.of(
                () -> MockMvcRequestBuilders.multipart(org.springframework.http.HttpMethod.PUT, "/api/floors/" + any + "/map")
                        .file(new MockMultipartFile("image", "a.png", "image/png", new byte[] {1})),
                () -> delete("/api/maps/" + any),
                () -> put("/api/maps/" + any + "/placements/" + any).contentType("application/json")
                        .content("{\"x\":0.5,\"y\":0.5}"),
                () -> delete("/api/maps/" + any + "/placements/" + any),
                () -> post("/api/connections").contentType("application/json")
                        .content("{\"name\":\"A\",\"type\":\"STAIRS\"}"),
                () -> put("/api/connections/" + any).contentType("application/json")
                        .content("{\"name\":\"A\",\"type\":\"STAIRS\"}"),
                () -> delete("/api/connections/" + any),
                () -> put("/api/connections/" + any + "/points/" + any).contentType("application/json")
                        .content("{\"x\":0.5,\"y\":0.5}"),
                () -> delete("/api/connections/" + any + "/points/" + any));
    }

    @Test
    void unauthenticatedWritesAreDeniedWith401() throws Exception {
        for (Call call : writes()) {
            mvc.perform(call.build().with(csrf())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void nonAdminWritesAreDeniedWith403() throws Exception {
        for (Call call : writes()) {
            mvc.perform(call.build().with(actor(user)).with(csrf()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
        }
    }

    @Test
    void adminPassesTheRoleGate() throws Exception {
        for (Call call : writes()) {
            int status = mvc.perform(call.build().with(actor(admin)).with(csrf())).andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
        }
    }

    @Test
    void readsAreOpenToAnySignedInUserButNotAnonymous() throws Exception {
        mvc.perform(get("/api/maps")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/connections")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/maps").with(actor(user))).andExpect(status().isOk());
        mvc.perform(get("/api/connections").with(actor(user))).andExpect(status().isOk());
    }
}
