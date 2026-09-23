package at.mci.igp.raumlotse.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.controller.CsrfController;
import at.mci.igp.raumlotse.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({HealthController.class, CsrfController.class})
@Import(SecurityConfig.class)
class SecurityPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousBusinessReadAndWriteAreDenied() throws Exception {
        mockMvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/rooms")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void authenticatedUnsafeRequestRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/rooms")).andExpect(status().isForbidden());
    }

    @Test
    void publicAuthRoutesAreReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login")).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rooms", "/api/rooms/00000000-0000-0000-0000-000000000001",
            "/api/buildings", "/api/buildings/00000000-0000-0000-0000-000000000001/floors",
            "/api/floors/00000000-0000-0000-0000-000000000001", "/api/equipment-types"})
    void everyBusinessCollectionIsDeniedAnonymously(String route) throws Exception {
        mockMvc.perform(get(route)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/rooms", "/api/rooms/00000000-0000-0000-0000-000000000001/deactivate",
            "/api/buildings", "/api/buildings/00000000-0000-0000-0000-000000000001/deactivate",
            "/api/buildings/00000000-0000-0000-0000-000000000001/floors",
            "/api/floors/00000000-0000-0000-0000-000000000001",
            "/api/floors/00000000-0000-0000-0000-000000000001/deactivate",
            "/api/equipment-types", "/api/equipment-types/00000000-0000-0000-0000-000000000001/deactivate"})
    void tokenlessBusinessWritesAreDeniedBeforeControllerExecution(String route) throws Exception {
        mockMvc.perform(post(route)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }
}
