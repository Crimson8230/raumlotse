package at.mci.igp.raumlotse.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import at.mci.igp.raumlotse.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import at.mci.igp.raumlotse.security.WithMockAdmin;
import at.mci.igp.raumlotse.service.UserRoleSafety;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.service.BuildingService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BuildingController.class)
@WithMockAdmin
@Import(SecurityConfig.class)
class BuildingControllerTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private at.mci.igp.raumlotse.service.EffectivePermissionService effectivePermissions;
    // Writes pass through RoleAccessFilter, which asks this (accepting) mock for the administrator check.
    @org.springframework.test.context.bean.override.mockito.MockitoBean UserRoleSafety roleSafety;


    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BuildingService buildingService;

    @Test
    void createReturns201() throws Exception {
        when(buildingService.create(eq("Main"), org.mockito.ArgumentMatchers.isNull())).thenReturn(new Building("Main"));

        mockMvc.perform(post("/api/buildings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Main"));
    }

    @Test
    void createWithDuplicateNameReturns409() throws Exception {
        when(buildingService.create(eq("Main"), org.mockito.ArgumentMatchers.isNull()))
                .thenThrow(new ConflictException("Ein Gebäude mit dem Namen 'Main' existiert bereits."));

        mockMvc.perform(post("/api/buildings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void renameReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(buildingService.update(eq(id), eq("Main Building"), org.mockito.ArgumentMatchers.isNull())).thenReturn(new Building("Main Building"));

        mockMvc.perform(put("/api/buildings/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main Building\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main Building"));
    }

    @Test
    void deactivateCascadesAndReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        Building deactivated = new Building("Main");
        deactivated.setStatus(EntityStatus.DEACTIVATED);
        when(buildingService.deactivate(id)).thenReturn(deactivated);

        mockMvc.perform(post("/api/buildings/" + id + "/deactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
    }

    @Test
    void reactivateDoesNotCascadeAndReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(buildingService.reactivate(id)).thenReturn(new Building("Main"));

        mockMvc.perform(post("/api/buildings/" + id + "/reactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deleteBlockedWhileItHasFloorsReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.doThrow(new ConflictException("Gebäude 'Main' still has one or more floors."))
                .when(buildingService).delete(id);

        mockMvc.perform(delete("/api/buildings/" + id).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/buildings/" + id).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void createWithElevatorPassesTheFlag() throws Exception {
        Building withElevator = new Building("Haus 1");
        withElevator.setHasElevator(true);
        when(buildingService.create(eq("Haus 1"), eq(true))).thenReturn(withElevator);

        mockMvc.perform(post("/api/buildings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Haus 1\",\"hasElevator\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasElevator").value(true));
    }

    @Test
    void createWithoutElevatorFlagReturnsFalse() throws Exception {
        when(buildingService.create(eq("Haus 1"), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new Building("Haus 1"));

        mockMvc.perform(post("/api/buildings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Haus 1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasElevator").value(false));
    }

    @Test
    void updateWithoutElevatorFlagPassesNullMeaningUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        when(buildingService.update(eq(id), eq("Haus 1b"), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new Building("Haus 1b"));

        mockMvc.perform(put("/api/buildings/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Haus 1b\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateWithElevatorFlagPassesTheValue() throws Exception {
        UUID id = UUID.randomUUID();
        when(buildingService.update(eq(id), eq("Haus 1"), eq(false))).thenReturn(new Building("Haus 1"));

        mockMvc.perform(put("/api/buildings/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Haus 1\",\"hasElevator\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasElevator").value(false));
    }
}
