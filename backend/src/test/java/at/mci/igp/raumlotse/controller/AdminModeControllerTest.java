package at.mci.igp.raumlotse.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.repository.RoleAssignmentRepository;
import at.mci.igp.raumlotse.security.WithMockAdmin;
import at.mci.igp.raumlotse.service.AdminModeService;
import at.mci.igp.raumlotse.service.UserRoleReadinessCheck;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminModeController.class, CurrentRolesController.class})
@WithMockAdmin
@Import({SecurityConfig.class, AdminModeService.class})
class AdminModeControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UserRoleSafety roleSafety;
    @MockitoBean RoleAssignmentRepository assignments;
    @MockitoBean UserRoleReadinessCheck readiness;

    private void asAdmin(boolean admin) {
        when(roleSafety.isAdmin(org.mockito.ArgumentMatchers.any())).thenReturn(admin);
        when(assignments.roles(org.mockito.ArgumentMatchers.any()))
                .thenReturn(admin ? List.of(Role.ADMIN) : List.of(Role.VIEWER));
        when(readiness.ready()).thenReturn(true);
    }

    @Test
    void newSessionReportsModeOff() throws Exception {
        asAdmin(true);

        mvc.perform(get("/api/auth/roles").session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminMode").value(false))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void administratorSwitchesTheModeOnAndItIsVisibleInTheRolesLookup() throws Exception {
        asAdmin(true);
        var session = new MockHttpSession();

        mvc.perform(put("/api/auth/admin-mode").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.adminMode").value(true));
        mvc.perform(get("/api/auth/roles").session(session))
                .andExpect(jsonPath("$.adminMode").value(true));
        mvc.perform(put("/api/auth/admin-mode").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(jsonPath("$.adminMode").value(false));
    }

    @Test
    void regularUserIsRefusedAndTheModeStaysOff() throws Exception {
        asAdmin(false);
        var session = new MockHttpSession();

        mvc.perform(put("/api/auth/admin-mode").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
        mvc.perform(get("/api/auth/roles").session(session))
                .andExpect(jsonPath("$.adminMode").value(false));
    }

    @Test
    void revokedAdministratorSeesTheModeEnded() throws Exception {
        asAdmin(true);
        var session = new MockHttpSession();
        mvc.perform(put("/api/auth/admin-mode").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"));

        asAdmin(false);

        mvc.perform(get("/api/auth/roles").session(session))
                .andExpect(jsonPath("$.adminMode").value(false));
        asAdmin(true);
        mvc.perform(get("/api/auth/roles").session(session))
                .andExpect(jsonPath("$.adminMode").value(false));
    }

    @Test
    void missingOrInvalidBodyIsABadRequest() throws Exception {
        asAdmin(true);

        mvc.perform(put("/api/auth/admin-mode").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(put("/api/auth/admin-mode").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":\"yes\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingTheModeRequiresCsrfAndSignIn() throws Exception {
        asAdmin(true);

        mvc.perform(put("/api/auth/admin-mode")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
