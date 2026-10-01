package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.dto.RoomDeviceControlsResponse;
import at.mci.igp.raumlotse.dto.RoomDeviceResponse;
import at.mci.igp.raumlotse.service.RoomDeviceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomDeviceController.class)
@WithMockUser
@Import(SecurityConfig.class)
class RoomDeviceControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean RoomDeviceService service;

    @Test
    void getReturnsCapabilities() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.getControls(roomId)).thenReturn(new RoomDeviceControlsResponse(roomId, UUID.randomUUID(),
                List.of(new RoomDeviceResponse(RoomDeviceKind.LIGHTING, true, false, Instant.parse("2026-10-01T10:00:00Z")))));
        mockMvc.perform(get("/api/rooms/{roomId}/device-controls", roomId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.devices[0].kind").value("LIGHTING"));
    }

    @Test
    void postReturnsAcknowledgedState() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.setState(eq(roomId), eq(RoomDeviceKind.LIGHTING), any())).thenReturn(
                new RoomDeviceResponse(RoomDeviceKind.LIGHTING, true, true, Instant.parse("2026-10-01T10:00:00Z")));
        mockMvc.perform(post("/api/rooms/{roomId}/device-controls/LIGHTING", roomId).with(csrf())
                        .contentType("application/json").content("{\"state\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value(true));
    }
}
