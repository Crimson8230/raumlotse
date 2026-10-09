package at.mci.igp.raumlotse.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.DeviceStatesResponse;
import at.mci.igp.raumlotse.dto.RoomStatusResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.service.RoomStatusService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
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

@WebMvcTest(RoomStatusController.class)
@Import(SecurityConfig.class)
class RoomStatusControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean RoomStatusService service;
    @MockitoBean UserRoleSafety roles;

    private static RequestPostProcessor user() {
        var principal = new AuthenticatedUser(UUID.randomUUID(), "Erika");
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void returnsStatusAndDeviceStatesWithoutPersonalData() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.status(eq(roomId), any())).thenReturn(new RoomStatusResponse(roomId, "OCCUPIED",
                new DeviceStatesResponse(true, true, "UNLOCKED"), null));

        String body = mockMvc.perform(get("/api/rooms/{roomId}/status", roomId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.status").value("OCCUPIED"))
                .andExpect(jsonPath("$.devices.lighting").value(true))
                .andExpect(jsonPath("$.devices.ventilation").value(true))
                .andExpect(jsonPath("$.devices.door").value("UNLOCKED"))
                .andExpect(jsonPath("$.lastPresenceAt").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("createdBy", "reservedFor", "note", "reservationId");
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("roomId", "status", "devices", "lastPresenceAt");
        assertThat(json.get("devices").propertyNames()).containsExactlyInAnyOrder("lighting", "ventilation", "door");
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/status", UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void unknownRoomIsNotFound() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.status(eq(roomId), any())).thenThrow(new NotFoundException("Raum nicht gefunden."));
        mockMvc.perform(get("/api/rooms/{roomId}/status", roomId).with(user())).andExpect(status().isNotFound());
    }
}
