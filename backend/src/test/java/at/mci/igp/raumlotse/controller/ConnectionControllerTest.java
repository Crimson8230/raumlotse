package at.mci.igp.raumlotse.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.domain.Connection;
import at.mci.igp.raumlotse.domain.ConnectionType;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.ConnectionResponse;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.service.ConnectionService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ConnectionController.class)
@WithMockUser
@Import(SecurityConfig.class)
class ConnectionControllerTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private at.mci.igp.raumlotse.service.EffectivePermissionService effectivePermissions;
    @Autowired MockMvc mvc;
    @MockitoBean ConnectionService service;
    @MockitoBean UserRoleSafety roleSafety;

    final UUID connectionId = UUID.randomUUID();
    final UUID mapId = UUID.randomUUID();

    static RequestPostProcessor admin() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(UUID.randomUUID(), "Admin"), null, List.of()));
    }

    Connection connection() {
        var c = new Connection("Aufzug A", ConnectionType.ELEVATOR);
        ReflectionTestUtils.setField(c, "id", connectionId);
        return c;
    }

    @Test
    void listReturnsConnectionsWithPointsAndIncompleteFlag() throws Exception {
        when(service.list()).thenReturn(List.of(ConnectionResponse.from(connection())));
        mvc.perform(get("/api/connections").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Aufzug A"))
                .andExpect(jsonPath("$[0].type").value("ELEVATOR"))
                .andExpect(jsonPath("$[0].incomplete").value(true))
                .andExpect(jsonPath("$[0].points").isEmpty());
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create("Aufzug A", ConnectionType.ELEVATOR)).thenReturn(connection());
        mvc.perform(post("/api/connections").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aufzug A\",\"type\":\"ELEVATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(connectionId.toString()));
    }

    @Test
    void createValidatesNameAndType() throws Exception {
        for (String body : List.of("{\"name\":\"\",\"type\":\"STAIRS\"}", "{\"name\":\"" + "x".repeat(101)
                + "\",\"type\":\"STAIRS\"}", "{\"name\":\"A\",\"type\":\"RAMP\"}", "{\"name\":\"A\"}")) {
            mvc.perform(post("/api/connections").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void duplicateNameReturns409() throws Exception {
        when(service.create("Aufzug A", ConnectionType.ELEVATOR)).thenThrow(new ConflictException("exists"));
        mvc.perform(post("/api/connections").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aufzug A\",\"type\":\"ELEVATOR\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateAndDelete() throws Exception {
        when(service.update(connectionId, "Aufzug A", ConnectionType.ELEVATOR)).thenReturn(connection());
        mvc.perform(put("/api/connections/" + connectionId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Aufzug A\",\"type\":\"ELEVATOR\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/connections/" + connectionId).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        doThrow(new NotFoundException("missing")).when(service).delete(connectionId);
        mvc.perform(delete("/api/connections/" + connectionId).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void putPointCreatesWith201AndMovesWith200() throws Exception {
        var response = ConnectionResponse.from(connection());
        when(service.putPoint(connectionId, mapId, 0.5, 0.5))
                .thenReturn(new ConnectionService.PointResult(response, true));
        mvc.perform(put("/api/connections/" + connectionId + "/points/" + mapId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":0.5,\"y\":0.5}"))
                .andExpect(status().isCreated());
        when(service.putPoint(connectionId, mapId, 0.5, 0.5))
                .thenReturn(new ConnectionService.PointResult(response, false));
        mvc.perform(put("/api/connections/" + connectionId + "/points/" + mapId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":0.5,\"y\":0.5}"))
                .andExpect(status().isOk());
    }

    @Test
    void putPointRejectsOutOfRangeCoordinatesAndUnknownIds() throws Exception {
        mvc.perform(put("/api/connections/" + connectionId + "/points/" + mapId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":2,\"y\":0.5}"))
                .andExpect(status().isBadRequest());
        when(service.putPoint(connectionId, mapId, 0.1, 0.1)).thenThrow(new NotFoundException("missing"));
        mvc.perform(put("/api/connections/" + connectionId + "/points/" + mapId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"x\":0.1,\"y\":0.1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePoint() throws Exception {
        mvc.perform(delete("/api/connections/" + connectionId + "/points/" + mapId).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        doThrow(new NotFoundException("missing")).when(service).removePoint(connectionId, mapId);
        mvc.perform(delete("/api/connections/" + connectionId + "/points/" + mapId).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
