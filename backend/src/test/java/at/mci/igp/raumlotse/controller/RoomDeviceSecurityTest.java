package at.mci.igp.raumlotse.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.service.RoomDeviceService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomDeviceController.class)
@Import(SecurityConfig.class)
class RoomDeviceSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean RoomDeviceService service;

    @Test
    void unauthenticatedRoomDeviceReadIsRejectedBeforeServiceInvocation() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/device-controls", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
