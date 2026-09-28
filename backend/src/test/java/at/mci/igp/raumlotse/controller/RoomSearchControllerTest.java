package at.mci.igp.raumlotse.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.domain.RoomSearchCriteria;
import at.mci.igp.raumlotse.service.RoomSearchService;
import at.mci.igp.raumlotse.service.RoomService;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// RoomController is loaded as well to prove that /api/rooms/search is not captured by GET /api/rooms/{roomId}.
@WebMvcTest({RoomSearchController.class, RoomController.class})
@Import(SecurityConfig.class)
class RoomSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomSearchService roomSearchService;

    @MockitoBean
    private RoomService roomService;

    @Test
    void searchWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/rooms/search"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @WithMockUser
    void searchBindsPersonRangeAndBuilding() throws Exception {
        UUID buildingId = UUID.randomUUID();
        when(roomSearchService.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/rooms/search")
                        .param("minPersons", "20")
                        .param("maxPersons", "50")
                        .param("buildingId", buildingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(0)));

        ArgumentCaptor<RoomSearchCriteria> captor = ArgumentCaptor.forClass(RoomSearchCriteria.class);
        verify(roomSearchService).search(captor.capture());
        assertThat(captor.getValue().minPersons()).isEqualTo(20);
        assertThat(captor.getValue().maxPersons()).isEqualTo(50);
        assertThat(captor.getValue().buildingId()).isEqualTo(buildingId);
    }

    @Test
    @WithMockUser
    void searchIsNotRoutedToRoomDetail() throws Exception {
        when(roomSearchService.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/rooms/search")).andExpect(status().isOk());

        verify(roomService, never()).get(any());
    }

    @Test
    @WithMockUser
    void nonPositivePersonCountReturns400() throws Exception {
        mockMvc.perform(get("/api/rooms/search").param("minPersons", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("minPersons"));
        verify(roomSearchService, never()).search(any());
    }

    @Test
    @WithMockUser
    void nonNumericPersonCountReturns400WithNeutralMessage() throws Exception {
        mockMvc.perform(get("/api/rooms/search").param("minPersons", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("minPersons"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid format"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Failed to convert"))));
    }

    @Test
    @WithMockUser
    void malformedBuildingIdReturns400() throws Exception {
        mockMvc.perform(get("/api/rooms/search").param("buildingId", "xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser
    void serviceValidationErrorReturns400() throws Exception {
        when(roomSearchService.search(any()))
                .thenThrow(new IllegalArgumentException("minPersons must not be greater than maxPersons."));

        mockMvc.perform(get("/api/rooms/search").param("minPersons", "30").param("maxPersons", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("minPersons must not be greater than maxPersons."));
    }

    @Test
    @WithMockUser
    void seatingArrangementLongerThan100CharactersReturns400() throws Exception {
        mockMvc.perform(get("/api/rooms/search").param("seatingArrangement", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("seatingArrangement"));
        verify(roomSearchService, never()).search(any());
    }

    @Test
    @WithMockUser
    void bindsSeatingArrangementAndRepeatedEquipmentTypeIds() throws Exception {
        UUID projector = UUID.randomUUID();
        UUID whiteboard = UUID.randomUUID();
        when(roomSearchService.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/rooms/search")
                        .param("seatingArrangement", "U-Shape")
                        .param("equipmentTypeId", projector.toString())
                        .param("equipmentTypeId", whiteboard.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<RoomSearchCriteria> captor = ArgumentCaptor.forClass(RoomSearchCriteria.class);
        verify(roomSearchService).search(captor.capture());
        assertThat(captor.getValue().seatingArrangement()).isEqualTo("U-Shape");
        assertThat(captor.getValue().equipmentTypeIds()).containsExactlyInAnyOrder(projector, whiteboard);
    }

    @Test
    @WithMockUser
    void listsSeatingArrangementNames() throws Exception {
        when(roomSearchService.listSeatingArrangementNames()).thenReturn(List.of("Theater", "U-Shape"));

        mockMvc.perform(get("/api/rooms/search/seating-arrangements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Theater"))
                .andExpect(jsonPath("$[1]").value("U-Shape"));
    }

    @Test
    void seatingArrangementNamesWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/rooms/search/seating-arrangements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void bindsTheTimeWindowAsInstants() throws Exception {
        when(roomSearchService.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/rooms/search")
                        .param("from", "2026-10-05T09:00:00Z")
                        .param("to", "2026-10-05T10:00:00Z"))
                .andExpect(status().isOk());

        ArgumentCaptor<RoomSearchCriteria> captor = ArgumentCaptor.forClass(RoomSearchCriteria.class);
        verify(roomSearchService).search(captor.capture());
        assertThat(captor.getValue().from()).isEqualTo(java.time.Instant.parse("2026-10-05T09:00:00Z"));
        assertThat(captor.getValue().to()).isEqualTo(java.time.Instant.parse("2026-10-05T10:00:00Z"));
    }

    @Test
    @WithMockUser
    void malformedInstantReturns400() throws Exception {
        mockMvc.perform(get("/api/rooms/search").param("from", "not-a-date").param("to", "2026-10-05T10:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].message").value("has an invalid format"));
        verify(roomSearchService, never()).search(any());
    }
}
