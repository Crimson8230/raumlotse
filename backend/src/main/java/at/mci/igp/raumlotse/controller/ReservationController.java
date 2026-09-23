package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.EquipmentTypeResponse;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.dto.ReservationSweepResponse;
import at.mci.igp.raumlotse.dto.ReservationUpdateRequest;
import at.mci.igp.raumlotse.service.ReservationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/api/rooms/{roomId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse createReservation(
            @PathVariable UUID roomId,
            @Valid @RequestBody ReservationCreateRequest request) {
        return reservationService.createReservation(roomId, request);
    }

    @GetMapping("/api/rooms/{roomId}/available-equipment")
    public List<EquipmentTypeResponse> getAvailableEquipment(@PathVariable UUID roomId) {
        return reservationService.getAvailableEquipment(roomId);
    }

    @GetMapping("/api/rooms/{roomId}/reservations")
    public List<ReservationResponse> listRoomReservations(
            @PathVariable UUID roomId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return reservationService.getReservationsForRoom(roomId, from, to);
    }

    @GetMapping("/api/reservations/{reservationId}")
    public ReservationResponse getReservation(@PathVariable UUID reservationId) {
        return reservationService.getReservation(reservationId);
    }

    @PatchMapping("/api/reservations/{reservationId}")
    public ReservationResponse updateReservationMetadata(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationUpdateRequest request) {
        return reservationService.updateReservationMetadata(reservationId, request);
    }

    @PostMapping("/api/reservations/{reservationId}/activate")
    public ReservationResponse activateReservation(@PathVariable UUID reservationId) {
        return reservationService.activateReservation(reservationId);
    }

    @PostMapping("/api/reservations/{reservationId}/complete")
    public ReservationResponse completeReservation(@PathVariable UUID reservationId) {
        return reservationService.completeReservation(reservationId);
    }

    @PostMapping("/api/reservations/{reservationId}/expire")
    public ReservationResponse expireReservation(@PathVariable UUID reservationId) {
        return reservationService.expireReservation(reservationId);
    }

    @PostMapping("/api/reservations/{reservationId}/cancel")
    public ReservationResponse cancelReservation(@PathVariable UUID reservationId) {
        return reservationService.cancelReservation(reservationId);
    }

    @PostMapping("/api/reservations/expire-unattended")
    public ReservationSweepResponse expireUnattendedReservations() {
        return reservationService.sweepOverdueReservations();
    }
}
