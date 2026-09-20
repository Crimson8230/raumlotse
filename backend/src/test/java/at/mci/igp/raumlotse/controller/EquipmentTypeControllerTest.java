package at.mci.igp.raumlotse.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import at.mci.igp.raumlotse.config.SecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.service.EquipmentTypeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EquipmentTypeController.class)
@WithMockUser
@Import(SecurityConfig.class)
class EquipmentTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EquipmentTypeService equipmentTypeService;

    @Test
    void createReturns201() throws Exception {
        EquipmentType created = new EquipmentType("Projector");
        when(equipmentTypeService.create("Projector")).thenReturn(created);

        mockMvc.perform(post("/api/equipment-types").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projector\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Projector"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createWithDuplicateNameReturns409() throws Exception {
        when(equipmentTypeService.create(eq("Projector")))
                .thenThrow(new ConflictException("An equipment type named 'Projector' already exists."));

        mockMvc.perform(post("/api/equipment-types").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Projector\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listReturns200() throws Exception {
        when(equipmentTypeService.list(any())).thenReturn(List.of(new EquipmentType("Whiteboard")));

        mockMvc.perform(get("/api/equipment-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void renameReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        EquipmentType renamed = new EquipmentType("Beamer");
        when(equipmentTypeService.rename(eq(id), eq("Beamer"))).thenReturn(renamed);

        mockMvc.perform(put("/api/equipment-types/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Beamer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beamer"));
    }

    @Test
    void deactivateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        EquipmentType deactivated = new EquipmentType("Projector");
        deactivated.setStatus(EntityStatus.DEACTIVATED);
        when(equipmentTypeService.deactivate(id)).thenReturn(deactivated);

        mockMvc.perform(post("/api/equipment-types/" + id + "/deactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
    }

    @Test
    void reactivateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        EquipmentType reactivated = new EquipmentType("Projector");
        when(equipmentTypeService.reactivate(id)).thenReturn(reactivated);

        mockMvc.perform(post("/api/equipment-types/" + id + "/reactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deleteBlockedWhileAssignedReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ConflictException("Equipment type is assigned to one or more rooms."))
                .when(equipmentTypeService).delete(id);

        mockMvc.perform(delete("/api/equipment-types/" + id).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/equipment-types/" + id).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
