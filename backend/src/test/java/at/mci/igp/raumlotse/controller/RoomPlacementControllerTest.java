package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.PlacementResponse;
import at.mci.igp.raumlotse.dto.RoomRefResponse;
import at.mci.igp.raumlotse.exception.MapRequestException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.service.RoomPlacementService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(RoomPlacementController.class)
@WithMockUser
@Import(SecurityConfig.class)
class RoomPlacementControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RoomPlacementService service;
    @MockitoBean UserRoleSafety roleSafety;

    final UUID mapId = UUID.randomUUID();
    final UUID roomId = UUID.randomUUID();

    static RequestPostProcessor admin() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(UUID.randomUUID(), "Admin"), null, List.of()));
    }

    PlacementResponse placement() {
        return new PlacementResponse(new RoomRefResponse(roomId, "R1", EntityStatus.ACTIVE), 0.25, 0.5);
    }

    @Test
    void putCreatesPlacementWith201() throws Exception {
        when(service.place(mapId, roomId, 0.25, 0.5))
                .thenReturn(new RoomPlacementService.PlaceResult(placement(), true));
        mvc.perform(put("/api/maps/" + mapId + "/placements/" + roomId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":0.25,\"y\":0.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room.name").value("R1"))
                .andExpect(jsonPath("$.x").value(0.25));
    }

    @Test
    void putMovesPlacementWith200() throws Exception {
        when(service.place(mapId, roomId, 0.25, 0.5))
                .thenReturn(new RoomPlacementService.PlaceResult(placement(), false));
        mvc.perform(put("/api/maps/" + mapId + "/placements/" + roomId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":0.25,\"y\":0.5}"))
                .andExpect(status().isOk());
    }

    @Test
    void coordinatesOutsideRangeOrMissingReturn400() throws Exception {
        for (String body : List.of("{\"x\":1.2,\"y\":0.5}", "{\"x\":0.5,\"y\":-0.1}", "{\"x\":0.5}", "{}")) {
            mvc.perform(put("/api/maps/" + mapId + "/placements/" + roomId).with(admin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(service, never()).place(eq(mapId), eq(roomId), anyDouble(), anyDouble());
    }

    @Test
    void unknownMapOrRoomReturns404() throws Exception {
        when(service.place(mapId, roomId, 0.1, 0.1)).thenThrow(new NotFoundException("Room nicht gefunden."));
        mvc.perform(put("/api/maps/" + mapId + "/placements/" + roomId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":0.1,\"y\":0.1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void roomOfOtherFloorReturns422() throws Exception {
        when(service.place(mapId, roomId, 0.1, 0.1)).thenThrow(new MapRequestException(
                HttpStatus.UNPROCESSABLE_CONTENT, "ROOM_FLOOR_MISMATCH", "The room is on a different floor."));
        mvc.perform(put("/api/maps/" + mapId + "/placements/" + roomId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":0.1,\"y\":0.1}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ROOM_FLOOR_MISMATCH"));
    }

    @Test
    void deleteReturns204AndUnknownReturns404() throws Exception {
        mvc.perform(delete("/api/maps/" + mapId + "/placements/" + roomId).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        doThrow(new NotFoundException("No placement.")).when(service).remove(mapId, roomId);
        mvc.perform(delete("/api/maps/" + mapId + "/placements/" + roomId).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
