package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.domain.CheckInMethod;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.CheckInPreviewResponse;
import at.mci.igp.raumlotse.dto.CheckInPreviewResponse.Outcome;
import at.mci.igp.raumlotse.dto.CheckInResultResponse;
import at.mci.igp.raumlotse.exception.CheckInRejectedException;
import at.mci.igp.raumlotse.service.CheckInService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CheckInController.class)
@Import(SecurityConfig.class)
class CheckInControllerTest {
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired MockMvc mockMvc;
    @MockitoBean CheckInService service;
    @MockitoBean UserRoleSafety roles;

    private static RequestPostProcessor user() {
        var principal = new AuthenticatedUser(USER_ID, "Erika");
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void previewReturnsTheContractShape() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        when(service.preview(roomId, new Actor(USER_ID, false))).thenReturn(new CheckInPreviewResponse(roomId,
                "Seminarraum 1", Outcome.READY,
                new CheckInPreviewResponse.Booking(reservationId, Instant.parse("2026-10-09T08:00:00Z"),
                        Instant.parse("2026-10-09T09:00:00Z"), "Projektgruppe"), null, null));

        mockMvc.perform(get("/api/rooms/{roomId}/check-in", roomId).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("Seminarraum 1"))
                .andExpect(jsonPath("$.outcome").value("READY"))
                .andExpect(jsonPath("$.reservation.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.reservation.reservedFor").value("Projektgruppe"))
                .andExpect(jsonPath("$.checkInOpensAt").doesNotExist());
    }

    @Test
    void checkInReturnsTheResult() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        when(service.checkIn(eq(roomId), eq(CheckInMethod.QR), any())).thenReturn(new CheckInResultResponse(
                reservationId, "ACTIVE", false, CheckInMethod.QR, Instant.parse("2026-10-09T08:01:00Z"), null,
                List.of()));

        mockMvc.perform(post("/api/rooms/{roomId}/check-in", roomId).with(user()).with(csrf())
                        .contentType("application/json").content("{\"method\":\"QR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.alreadyActive").value(false))
                .andExpect(jsonPath("$.checkInMethod").value("QR"))
                .andExpect(jsonPath("$.failedDevices").isEmpty());
    }

    @Test
    void nfcCheckInIsAccepted() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.checkIn(eq(roomId), eq(CheckInMethod.NFC), any())).thenReturn(new CheckInResultResponse(
                UUID.randomUUID(), "ACTIVE", false, CheckInMethod.NFC, Instant.parse("2026-10-09T08:01:00Z"), null,
                List.of()));

        mockMvc.perform(post("/api/rooms/{roomId}/check-in", roomId).with(user()).with(csrf())
                        .contentType("application/json").content("{\"method\":\"NFC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInMethod").value("NFC"));
    }

    @Test
    void aCheckInThatLostARaceIsRetriedAndAnswersAlreadyActive() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.checkIn(eq(roomId), eq(CheckInMethod.QR), any()))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("Reservation", UUID.randomUUID()))
                .thenReturn(new CheckInResultResponse(UUID.randomUUID(), "ACTIVE", true, CheckInMethod.NFC,
                        Instant.parse("2026-10-09T08:00:30Z"), null, List.of()));

        mockMvc.perform(post("/api/rooms/{roomId}/check-in", roomId).with(user()).with(csrf())
                        .contentType("application/json").content("{\"method\":\"QR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyActive").value(true));
    }

    @Test
    void aCheckInThatLostARaceAgainstTheExpirySweepExplainsTheExpiry() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.checkIn(eq(roomId), eq(CheckInMethod.QR), any()))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("Reservation", UUID.randomUUID()))
                .thenThrow(new CheckInRejectedException(Outcome.EXPIRED, "Die Buchung ist abgelaufen."));

        mockMvc.perform(post("/api/rooms/{roomId}/check-in", roomId).with(user()).with(csrf())
                        .contentType("application/json").content("{\"method\":\"QR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXPIRED"));
    }

    @Test
    void missingUnknownOrManualMethodIsRejected() throws Exception {
        UUID roomId = UUID.randomUUID();
        for (String body : List.of("{}", "{\"method\":\"BADGE\"}", "{\"method\":\"MANUAL\"}")) {
            mockMvc.perform(post("/api/rooms/{roomId}/check-in", roomId).with(user()).with(csrf())
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void anonymousCallersAreRejectedBeforeTheService() throws Exception {
        UUID roomId = UUID.randomUUID();
        mockMvc.perform(get("/api/rooms/{roomId}/check-in", roomId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/rooms/{roomId}/check-in", roomId).with(csrf())
                        .contentType("application/json").content("{\"method\":\"QR\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(value = Outcome.class, names = {"TOO_EARLY", "EXPIRED", "NO_MATCH"})
    void rejectionsAreConflictsCarryingTheReasonAsCode(Outcome reason) throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.checkIn(eq(roomId), eq(CheckInMethod.NFC), any()))
                .thenThrow(new CheckInRejectedException(reason, "Nicht möglich."));

        mockMvc.perform(post("/api/rooms/{roomId}/check-in", roomId).with(user()).with(csrf())
                        .contentType("application/json").content("{\"method\":\"NFC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(reason.name()))
                .andExpect(jsonPath("$.detail").value("Nicht möglich."));
    }

    /** Field sets of contracts/presence-checkin-api.yaml: nothing more, in particular no personal data. */
    @Test
    void responsesCarryExactlyTheContractFields() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(service.preview(eq(roomId), any())).thenReturn(new CheckInPreviewResponse(roomId, "Seminarraum 1",
                Outcome.TOO_EARLY, new CheckInPreviewResponse.Booking(UUID.randomUUID(),
                        Instant.parse("2026-10-09T08:00:00Z"), Instant.parse("2026-10-09T09:00:00Z"), "Projektgruppe"),
                Instant.parse("2026-10-09T08:00:00Z"), "Der Check-in ist ab 10:00 Uhr möglich."));
        when(service.checkIn(eq(roomId), eq(CheckInMethod.QR), any())).thenReturn(new CheckInResultResponse(
                UUID.randomUUID(), "ACTIVE", false, CheckInMethod.QR, Instant.parse("2026-10-09T08:01:00Z"),
                new at.mci.igp.raumlotse.dto.DeviceStatesResponse(true, true, "UNLOCKED"), List.of()));

        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var preview = mapper.readTree(mockMvc.perform(get("/api/rooms/{roomId}/check-in", roomId).with(user()))
                .andReturn().getResponse().getContentAsString());
        var result = mapper.readTree(mockMvc.perform(post("/api/rooms/{roomId}/check-in", roomId).with(user()).with(csrf())
                        .contentType("application/json").content("{\"method\":\"QR\"}"))
                .andReturn().getResponse().getContentAsString());

        org.assertj.core.api.Assertions.assertThat(preview.propertyNames())
                .containsExactlyInAnyOrder("roomId", "roomName", "outcome", "reservation", "checkInOpensAt", "detail");
        org.assertj.core.api.Assertions.assertThat(preview.get("reservation").propertyNames())
                .containsExactlyInAnyOrder("id", "startTime", "endTime", "reservedFor");
        org.assertj.core.api.Assertions.assertThat(result.propertyNames()).containsExactlyInAnyOrder("reservationId",
                "status", "alreadyActive", "checkInMethod", "checkedInAt", "devices", "failedDevices");
        org.assertj.core.api.Assertions.assertThat(result.get("devices").propertyNames())
                .containsExactlyInAnyOrder("lighting", "ventilation", "door");
    }
}
