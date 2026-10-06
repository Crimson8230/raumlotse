package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.EquipmentTypeResponse;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.dto.ReservationSweepResponse;
import at.mci.igp.raumlotse.dto.ReservationUpdateRequest;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReservationController {

    private final ReservationService reservationService;
    private final UserRoleSafety roles;

    public ReservationController(ReservationService reservationService, UserRoleSafety roles) {
        this.reservationService = reservationService;
        this.roles = roles;
    }

    @PostMapping("/api/rooms/{roomId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse createReservation(
            @PathVariable UUID roomId,
            @Valid @RequestBody ReservationCreateRequest request,
            Authentication authentication) {
        AuthenticatedUser user = resolveUser(authentication);
        return reservationService.createReservation(roomId, request, user);
    }

    private AuthenticatedUser resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Anmeldung erforderlich.");
        }
        return user;
    }

    private Actor actor(Authentication authentication) {
        AuthenticatedUser user = resolveUser(authentication);
        return new Actor(user.userId(), roles.isAdmin(user.userId()));
    }

    @GetMapping("/api/rooms/{roomId}/available-equipment")
    public List<EquipmentTypeResponse> getAvailableEquipment(@PathVariable UUID roomId) {
        return reservationService.getAvailableEquipment(roomId);
    }

    @GetMapping("/api/rooms/{roomId}/reservations")
    public List<ReservationResponse> listRoomReservations(
            @PathVariable UUID roomId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Authentication authentication) {
        return reservationService.getReservationsForRoom(roomId, from, to, actor(authentication));
    }

    @GetMapping("/api/reservations/{reservationId}")
    public ReservationResponse getReservation(@PathVariable UUID reservationId, Authentication authentication) {
        return reservationService.getReservation(reservationId, actor(authentication));
    }

    @PatchMapping("/api/reservations/{reservationId}")
    public ReservationResponse updateReservationMetadata(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationUpdateRequest request,
            Authentication authentication) {
        return reservationService.updateReservationMetadata(reservationId, request, actor(authentication));
    }

    @PostMapping("/api/reservations/{reservationId}/activate")
    public ReservationResponse activateReservation(@PathVariable UUID reservationId, Authentication authentication) {
        return reservationService.activateReservation(reservationId, actor(authentication));
    }

    @PostMapping("/api/reservations/{reservationId}/complete")
    public ReservationResponse completeReservation(@PathVariable UUID reservationId, Authentication authentication) {
        return reservationService.completeReservation(reservationId, actor(authentication));
    }

    @PostMapping("/api/reservations/{reservationId}/expire")
    public ReservationResponse expireReservation(@PathVariable UUID reservationId, Authentication authentication) {
        return reservationService.expireReservation(reservationId, actor(authentication));
    }

    @PostMapping("/api/reservations/{reservationId}/cancel")
    public ReservationResponse cancelReservation(@PathVariable UUID reservationId, Authentication authentication) {
        return reservationService.cancelReservation(reservationId, actor(authentication));
    }

    @PostMapping("/api/reservations/expire-unattended")
    public ReservationSweepResponse expireUnattendedReservations() {
        return reservationService.sweepOverdueReservations();
    }

    @GetMapping("/api/reservations/my-upcoming")
    public List<ReservationResponse> getMyUpcomingReservations(Authentication authentication) {
        return reservationService.getMyUpcomingReservations(resolveUser(authentication).userId());
    }
}
