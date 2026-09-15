package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.service.FloorService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FloorController.class)
class FloorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FloorService floorService;

    private Floor floorOf(Building building, String name) {
        return new Floor(building, name);
    }

    @Test
    void createUnderBuildingReturns201() throws Exception {
        UUID buildingId = UUID.randomUUID();
        when(floorService.create(eq(buildingId), eq("1"))).thenReturn(floorOf(new Building("Main"), "1"));

        mockMvc.perform(post("/api/buildings/" + buildingId + "/floors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("1"));
    }

    @Test
    void createUnderDeactivatedBuildingReturns400() throws Exception {
        UUID buildingId = UUID.randomUUID();
        when(floorService.create(eq(buildingId), eq("1")))
                .thenThrow(new IllegalArgumentException(
                        "Building 'Main' is not active; a floor can only be created under an active building."));

        mockMvc.perform(post("/api/buildings/" + buildingId + "/floors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listForUnknownBuildingReturns404() throws Exception {
        UUID buildingId = UUID.randomUUID();
        when(floorService.listByBuilding(eq(buildingId), any()))
                .thenThrow(new at.mci.igp.raumlotse.exception.NotFoundException("Building " + buildingId + " not found."));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/buildings/" + buildingId + "/floors"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithDuplicateNameInBuildingReturns409() throws Exception {
        UUID buildingId = UUID.randomUUID();
        when(floorService.create(eq(buildingId), eq("1")))
                .thenThrow(new ConflictException("A floor named '1' already exists in this building."));

        mockMvc.perform(post("/api/buildings/" + buildingId + "/floors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void renameReturns200() throws Exception {
        UUID floorId = UUID.randomUUID();
        when(floorService.rename(eq(floorId), eq("Ground"))).thenReturn(floorOf(new Building("Main"), "Ground"));

        mockMvc.perform(put("/api/floors/" + floorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ground\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ground"));
    }

    @Test
    void deactivateReturns200() throws Exception {
        UUID floorId = UUID.randomUUID();
        when(floorService.deactivate(floorId)).thenReturn(floorOf(new Building("Main"), "1"));

        mockMvc.perform(post("/api/floors/" + floorId + "/deactivate"))
                .andExpect(status().isOk());
    }

    @Test
    void reactivateBlockedWhileBuildingDeactivatedReturns409() throws Exception {
        UUID floorId = UUID.randomUUID();
        when(floorService.reactivate(floorId))
                .thenThrow(new ConflictException("Building is still deactivated; reactivate it first."));

        mockMvc.perform(post("/api/floors/" + floorId + "/reactivate"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteBlockedWhileReferencedByRoomReturns409() throws Exception {
        UUID floorId = UUID.randomUUID();
        Mockito.doThrow(new ConflictException("Floor is referenced by one or more rooms."))
                .when(floorService).delete(floorId);

        mockMvc.perform(delete("/api/floors/" + floorId))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID floorId = UUID.randomUUID();

        mockMvc.perform(delete("/api/floors/" + floorId))
                .andExpect(status().isNoContent());
    }
}
