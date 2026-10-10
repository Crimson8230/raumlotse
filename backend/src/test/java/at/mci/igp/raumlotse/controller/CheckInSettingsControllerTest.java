package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.CheckInSettingsResponse;
import at.mci.igp.raumlotse.dto.CheckInSettingsUpdateRequest;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.service.CheckInSettingsService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
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

@WebMvcTest(CheckInSettingsController.class)
@Import(SecurityConfig.class)
class CheckInSettingsControllerTest {
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final Instant UPDATED = Instant.parse("2026-10-10T08:00:00Z");

    @Autowired MockMvc mockMvc;
    @MockitoBean CheckInSettingsService service;
    @MockitoBean UserRoleSafety roles;

    private static RequestPostProcessor signedIn() {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(ADMIN_ID, "Admin"), null, List.of()));
    }

    @Test
    void administratorsReadTheSettings() throws Exception {
        when(service.read()).thenReturn(new CheckInSettingsResponse(10, 5, UPDATED));

        mockMvc.perform(get("/api/admin/check-in-settings").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earlyCheckInMinutes").value(10))
                .andExpect(jsonPath("$.gracePeriodMinutes").value(5))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-10T08:00:00Z"));
    }

    @Test
    void administratorsChangeTheSettings() throws Exception {
        when(service.update(eq(new CheckInSettingsUpdateRequest(0, 15)), eq(ADMIN_ID)))
                .thenReturn(new CheckInSettingsResponse(0, 15, UPDATED));

        mockMvc.perform(put("/api/admin/check-in-settings").with(signedIn()).with(csrf())
                        .contentType("application/json").content("{\"earlyCheckInMinutes\":0,\"gracePeriodMinutes\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earlyCheckInMinutes").value(0))
                .andExpect(jsonPath("$.gracePeriodMinutes").value(15));
    }

    @Test
    void valuesOutsideTheRangesAreRejected() throws Exception {
        for (String body : List.of("{\"earlyCheckInMinutes\":61,\"gracePeriodMinutes\":5}",
                "{\"earlyCheckInMinutes\":-1,\"gracePeriodMinutes\":5}",
                "{\"earlyCheckInMinutes\":10,\"gracePeriodMinutes\":0}",
                "{\"earlyCheckInMinutes\":10,\"gracePeriodMinutes\":31}",
                "{\"earlyCheckInMinutes\":10}")) {
            mockMvc.perform(put("/api/admin/check-in-settings").with(signedIn()).with(csrf())
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void regularUsersAndAnonymousCallersAreRejected() throws Exception {
        mockMvc.perform(get("/api/admin/check-in-settings")).andExpect(status().isUnauthorized());
        doThrow(new UserRoleException(403, "ADMIN_REQUIRED", "Administratorrechte erforderlich."))
                .when(roles).requireAdmin(any());
        mockMvc.perform(put("/api/admin/check-in-settings").with(signedIn()).with(csrf())
                        .contentType("application/json").content("{\"earlyCheckInMinutes\":0,\"gracePeriodMinutes\":15}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
