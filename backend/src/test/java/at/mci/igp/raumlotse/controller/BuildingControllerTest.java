package at.mci.igp.raumlotse.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import at.mci.igp.raumlotse.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

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
@WithMockUser
@Import(SecurityConfig.class)
class BuildingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BuildingService buildingService;

    @Test
    void createReturns201() throws Exception {
        when(buildingService.create("Main")).thenReturn(new Building("Main"));

        mockMvc.perform(post("/api/buildings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Main"));
    }

    @Test
    void createWithDuplicateNameReturns409() throws Exception {
        when(buildingService.create(eq("Main")))
                .thenThrow(new ConflictException("A building named 'Main' already exists."));

        mockMvc.perform(post("/api/buildings").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Main\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void renameReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(buildingService.rename(eq(id), eq("Main Building"))).thenReturn(new Building("Main Building"));

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
        Mockito.doThrow(new ConflictException("Building 'Main' still has one or more floors."))
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
}
