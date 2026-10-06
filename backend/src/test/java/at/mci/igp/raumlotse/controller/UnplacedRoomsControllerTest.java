package at.mci.igp.raumlotse.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.dto.RoomRefResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.service.RoomPlacementService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomPlacementController.class)
@WithMockUser
@Import(SecurityConfig.class)
class UnplacedRoomsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RoomPlacementService service;
    @MockitoBean UserRoleSafety roleSafety;

    final UUID mapId = UUID.randomUUID();

    @Test
    void listsUnplacedRoomsForAnySignedInUser() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.unplacedRooms(mapId)).thenReturn(List.of(new RoomRefResponse(roomId, "R1", EntityStatus.ACTIVE)));
        mvc.perform(get("/api/maps/" + mapId + "/unplaced-rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(roomId.toString()))
                .andExpect(jsonPath("$[0].name").value("R1"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void unknownMapReturns404() throws Exception {
        when(service.unplacedRooms(mapId)).thenThrow(new NotFoundException("Map not found."));
        mvc.perform(get("/api/maps/" + mapId + "/unplaced-rooms")).andExpect(status().isNotFound());
    }
}
