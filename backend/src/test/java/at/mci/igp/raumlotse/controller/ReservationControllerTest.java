package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.dto.EquipmentTypeResponse;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.dto.ReservationSweepResponse;
import at.mci.igp.raumlotse.dto.SeatingArrangementResponse;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.service.ReservationService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(ReservationController.class)
@WithMockUser
@Import(SecurityConfig.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    void createReservation_authenticatedUser_assignsCreatedByAndReservedFor() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID layoutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        var actor = authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, "Max Mustermann"), null, List.of()));

        ReservationResponse response = new ReservationResponse(
                reservationId,
                roomId,
                "Room 101",
                start,
                end,
                ReservationStatus.RESERVED,
                new SeatingArrangementResponse(layoutId, "Theater", 40),
                25,
                List.of(),
                "Planning",
                userId.toString(),
                "Projektgruppe Web",
                Instant.now());

        when(reservationService.createReservation(eq(roomId), any(), eq(userId.toString()))).thenReturn(response);

        mockMvc.perform(post("/api/rooms/{roomId}/reservations", roomId).with(csrf()).with(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "seatingArrangementId": "%s",
                                  "expectedAttendees": 25,
                                  "reservedFor": "Projektgruppe Web"
                                }
                                """.formatted(start, end, layoutId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.createdBy").value(userId.toString()))
                .andExpect(jsonPath("$.reservedFor").value("Projektgruppe Web"));
    }

    @Test
    void createReservation_blankReservedFor_returns400() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID layoutId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        var actor = authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, "Max Mustermann"), null, List.of()));

        mockMvc.perform(post("/api/rooms/{roomId}/reservations", roomId).with(csrf()).with(actor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "seatingArrangementId": "%s",
                                  "expectedAttendees": 25,
                                  "reservedFor": "   "
                                }
                                """.formatted(start, end, layoutId)))
                .andExpect(status().isCreated());
    }

    @Test
    void createReservation_unauthenticated_returns401() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID layoutId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/rooms/{roomId}/reservations", roomId).with(csrf()).with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "seatingArrangementId": "%s",
                                  "expectedAttendees": 25,
                                  "reservedFor": "Projektgruppe Web"
                                }
                                """.formatted(start, end, layoutId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReservation_returns201() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID layoutId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        ReservationResponse response = new ReservationResponse(
                reservationId,
                roomId,
                "Room 101",
                start,
                end,
                ReservationStatus.RESERVED,
                new SeatingArrangementResponse(layoutId, "Theater", 40),
                25,
                List.of(),
                "Quarterly Planning",
                "Jane Doe",
                Instant.now());

        when(reservationService.createReservation(eq(roomId), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/rooms/{roomId}/reservations", roomId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "seatingArrangementId": "%s",
                                  "expectedAttendees": 25,
                                  "additionalEquipmentTypeIds": [],
                                  "note": "Quarterly Planning",
                                  "reservedFor": "Jane Doe"
                                }
                                """.formatted(start, end, layoutId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reservationId.toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.roomName").value("Room 101"))
                .andExpect(jsonPath("$.expectedAttendees").value(25))
                .andExpect(jsonPath("$.createdBy").value("Jane Doe"))
                .andExpect(jsonPath("$.reservedFor").value("Jane Doe"));
    }

    @Test
    void createReservation_zeroAttendees_returns400() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID layoutId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/rooms/{roomId}/reservations", roomId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "seatingArrangementId": "%s",
                                  "expectedAttendees": 0,
                                  "reservedFor": "Jane Doe"
                                }
                                """.formatted(start, end, layoutId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReservation_conflict_returns409() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID layoutId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(reservationService.createReservation(eq(roomId), any(), any()))
                .thenThrow(new ConflictException("Scheduling conflict: The room is already reserved during this time."));

        mockMvc.perform(post("/api/rooms/{roomId}/reservations", roomId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "seatingArrangementId": "%s",
                                  "expectedAttendees": 25,
                                  "reservedFor": "Jane Doe"
                                }
                                """.formatted(start, end, layoutId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Scheduling conflict: The room is already reserved during this time."));
    }

    @Test
    void createReservation_roomNotFound_returns404() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID layoutId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(reservationService.createReservation(eq(roomId), any(), any()))
                .thenThrow(new NotFoundException("Room " + roomId + " not found."));

        mockMvc.perform(post("/api/rooms/{roomId}/reservations", roomId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "seatingArrangementId": "%s",
                                  "expectedAttendees": 25,
                                  "reservedFor": "Jane Doe"
                                }
                                """.formatted(start, end, layoutId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAvailableEquipment_returns200() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID eqId = UUID.randomUUID();
        EquipmentTypeResponse eq = new EquipmentTypeResponse(eqId, "Projector", EntityStatus.ACTIVE);

        when(reservationService.getAvailableEquipment(roomId)).thenReturn(List.of(eq));

        mockMvc.perform(get("/api/rooms/{roomId}/available-equipment", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(eqId.toString()))
                .andExpect(jsonPath("$[0].name").value("Projector"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void createReservation_deactivatedEquipment_returns400() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID layoutId = UUID.randomUUID();
        UUID eqId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        when(reservationService.createReservation(eq(roomId), any(), any()))
                .thenThrow(new IllegalArgumentException("Equipment type 'Old Cam' is deactivated and cannot be reserved."));

        mockMvc.perform(post("/api/rooms/{roomId}/reservations", roomId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "seatingArrangementId": "%s",
                                  "expectedAttendees": 25,
                                  "additionalEquipmentTypeIds": ["%s"],
                                  "reservedFor": "Jane Doe"
                                }
                                """.formatted(start, end, layoutId, eqId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Equipment type 'Old Cam' is deactivated and cannot be reserved."));
    }

    @Test
    void listRoomReservations_returns200() throws Exception {
        UUID roomId = UUID.randomUUID();
        UUID resId = UUID.randomUUID();
        ReservationResponse res = new ReservationResponse(
                resId, roomId, "Room 101",
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                ReservationStatus.RESERVED,
                new SeatingArrangementResponse(UUID.randomUUID(), "Theater", 40),
                20, List.of(), null, "Alice", Instant.now());

        when(reservationService.getReservationsForRoom(eq(roomId), any(), any())).thenReturn(List.of(res));

        mockMvc.perform(get("/api/rooms/{roomId}/reservations", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(resId.toString()))
                .andExpect(jsonPath("$[0].createdBy").value("Alice"));
    }

    @Test
    void getReservation_returns200() throws Exception {
        UUID resId = UUID.randomUUID();
        ReservationResponse res = new ReservationResponse(
                resId, UUID.randomUUID(), "Room 101",
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                ReservationStatus.RESERVED,
                new SeatingArrangementResponse(UUID.randomUUID(), "Theater", 40),
                20, List.of(), null, "Alice", Instant.now());

        when(reservationService.getReservation(resId)).thenReturn(res);

        mockMvc.perform(get("/api/reservations/{reservationId}", resId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resId.toString()));
    }

    @Test
    void updateReservationMetadata_returns200() throws Exception {
        UUID resId = UUID.randomUUID();
        ReservationResponse res = new ReservationResponse(
                resId, UUID.randomUUID(), "Room 101",
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                ReservationStatus.RESERVED,
                new SeatingArrangementResponse(UUID.randomUUID(), "Theater", 40),
                25, List.of(), "Updated note", "Alice", Instant.now());

        when(reservationService.updateReservationMetadata(eq(resId), any())).thenReturn(res);

        mockMvc.perform(patch("/api/reservations/{reservationId}", resId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedAttendees": 25,
                                  "note": "Updated note"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedAttendees").value(25))
                .andExpect(jsonPath("$.note").value("Updated note"));
    }

    @Test
    void activateReservation_returns200() throws Exception {
        UUID resId = UUID.randomUUID();
        ReservationResponse res = new ReservationResponse(
                resId, UUID.randomUUID(), "Room 101",
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                ReservationStatus.ACTIVE,
                new SeatingArrangementResponse(UUID.randomUUID(), "Theater", 40),
                20, List.of(), null, "Alice", Instant.now());

        when(reservationService.activateReservation(resId)).thenReturn(res);

        mockMvc.perform(post("/api/reservations/{reservationId}/activate", resId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void completeReservation_returns200() throws Exception {
        UUID resId = UUID.randomUUID();
        ReservationResponse res = new ReservationResponse(
                resId, UUID.randomUUID(), "Room 101",
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(),
                ReservationStatus.COMPLETED,
                new SeatingArrangementResponse(UUID.randomUUID(), "Theater", 40),
                20, List.of(), null, "Alice", Instant.now());

        when(reservationService.completeReservation(resId)).thenReturn(res);

        mockMvc.perform(post("/api/reservations/{reservationId}/complete", resId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void expireReservation_returns200() throws Exception {
        UUID resId = UUID.randomUUID();
        ReservationResponse res = new ReservationResponse(
                resId, UUID.randomUUID(), "Room 101",
                Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS),
                ReservationStatus.EXPIRED,
                new SeatingArrangementResponse(UUID.randomUUID(), "Theater", 40),
                20, List.of(), null, "Alice", Instant.now());

        when(reservationService.expireReservation(resId)).thenReturn(res);

        mockMvc.perform(post("/api/reservations/{reservationId}/expire", resId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void cancelReservation_returns200() throws Exception {
        UUID resId = UUID.randomUUID();
        ReservationResponse res = new ReservationResponse(
                resId, UUID.randomUUID(), "Room 101",
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                ReservationStatus.CANCELLED,
                new SeatingArrangementResponse(UUID.randomUUID(), "Theater", 40),
                20, List.of(), null, "Alice", Instant.now());

        when(reservationService.cancelReservation(resId)).thenReturn(res);

        mockMvc.perform(post("/api/reservations/{reservationId}/cancel", resId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelReservation_terminalState_returns409() throws Exception {
        UUID resId = UUID.randomUUID();

        when(reservationService.cancelReservation(resId))
                .thenThrow(new ConflictException("Reservation is in terminal state CANCELLED and cannot be cancelled."));

        mockMvc.perform(post("/api/reservations/{reservationId}/cancel", resId).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Reservation is in terminal state CANCELLED and cannot be cancelled."));
    }

    @Test
    void expireUnattendedReservations_returns200() throws Exception {
        when(reservationService.sweepOverdueReservations()).thenReturn(new ReservationSweepResponse(2, 3));

        mockMvc.perform(post("/api/reservations/expire-unattended").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredCount").value(2))
                .andExpect(jsonPath("$.completedCount").value(3));
    }

    @Test
    void activateReservation_terminalState_returns409() throws Exception {
        UUID resId = UUID.randomUUID();

        when(reservationService.activateReservation(resId))
                .thenThrow(new ConflictException("Reservation cannot be activated from status: EXPIRED"));

        mockMvc.perform(post("/api/reservations/{reservationId}/activate", resId).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Reservation cannot be activated from status: EXPIRED"));
    }

    @Test
    void updateReservationMetadata_terminalState_returns409() throws Exception {
        UUID resId = UUID.randomUUID();

        when(reservationService.updateReservationMetadata(eq(resId), any()))
                .thenThrow(new ConflictException("Only reservations in RESERVED status can be edited."));

        mockMvc.perform(patch("/api/reservations/{reservationId}", resId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "note": "Updated note"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only reservations in RESERVED status can be edited."));
    }

    @Test
    void getMyUpcomingReservations_authenticated_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var actor = authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, "Jane Doe"), null, List.of()));

        UUID roomId = UUID.randomUUID();
        UUID resId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        ReservationResponse response = new ReservationResponse(
                resId,
                roomId,
                "Room 101",
                start,
                end,
                ReservationStatus.RESERVED,
                new SeatingArrangementResponse(UUID.randomUUID(), "Theater", 40),
                10,
                List.of(),
                "Sync",
                userId.toString(),
                "Jane Doe",
                Instant.now());

        when(reservationService.getMyUpcomingReservations(userId.toString())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/reservations/my-upcoming").with(actor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(resId.toString()))
                .andExpect(jsonPath("$[0].roomName").value("Room 101"))
                .andExpect(jsonPath("$[0].createdBy").value(userId.toString()))
                .andExpect(jsonPath("$[0].reservedFor").value("Jane Doe"));
    }

    @Test
    void getMyUpcomingReservations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/reservations/my-upcoming").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateReservationMetadata_updateReservedFor_returns200() throws Exception {
        UUID resId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        ReservationResponse response = new ReservationResponse(
                resId,
                roomId,
                "Room 101",
                start,
                end,
                ReservationStatus.RESERVED,
                new SeatingArrangementResponse(UUID.randomUUID(), "Theater", 40),
                25,
                List.of(),
                "Updated note",
                "user-1",
                "Neues Projektteam",
                Instant.now());

        when(reservationService.updateReservationMetadata(eq(resId), any())).thenReturn(response);

        mockMvc.perform(patch("/api/reservations/{reservationId}", resId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservedFor": "Neues Projektteam"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resId.toString()))
                .andExpect(jsonPath("$.reservedFor").value("Neues Projektteam"));
    }

    @Test
    void updateReservationMetadata_blankReservedFor_returns400() throws Exception {
        UUID resId = UUID.randomUUID();

        mockMvc.perform(patch("/api/reservations/{reservationId}", resId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservedFor": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
