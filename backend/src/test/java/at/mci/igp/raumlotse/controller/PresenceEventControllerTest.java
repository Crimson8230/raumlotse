package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.PresenceEventResponse;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.service.PresenceService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import at.mci.igp.raumlotse.service.EffectivePermissionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PresenceEventController.class)
@Import(SecurityConfig.class)
class PresenceEventControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean PresenceService service;
    @MockitoBean UserRoleSafety roles;
    @MockitoBean EffectivePermissionService permissions;

    private static RequestPostProcessor signedIn() {
        var principal = new AuthenticatedUser(UUID.randomUUID(), "Erika");
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void administratorSimulatesMotion() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.recordMotion(roomId)).thenReturn(new PresenceEventResponse(true, Instant.parse("2026-10-09T08:00:00Z")));

        mockMvc.perform(post("/api/admin/rooms/{roomId}/presence-events", roomId).with(signedIn()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true))
                .andExpect(jsonPath("$.lastPresenceAt").value("2026-10-09T08:00:00Z"));
    }

    @Test
    void regularUsersCannotSubmitMotionEvents() throws Exception {
        doThrow(new UserRoleException(403, "ADMIN_REQUIRED", "Administratorrechte erforderlich."))
                .when(permissions).requireAdmin(any());

        mockMvc.perform(post("/api/admin/rooms/{roomId}/presence-events", UUID.randomUUID()).with(signedIn()).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void anonymousCallersAreRejected() throws Exception {
        mockMvc.perform(post("/api/admin/rooms/{roomId}/presence-events", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
