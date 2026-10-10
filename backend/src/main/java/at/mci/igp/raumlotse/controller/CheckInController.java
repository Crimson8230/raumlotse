package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.CheckInPreviewResponse;
import at.mci.igp.raumlotse.dto.CheckInRequest;
import at.mci.igp.raumlotse.dto.CheckInResultResponse;
import at.mci.igp.raumlotse.service.CheckInService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** On-site check-in by QR code or NFC link (feature 014). */
@RestController
public class CheckInController {
    private final CheckInService service;
    private final UserRoleSafety roles;

    public CheckInController(CheckInService service, UserRoleSafety roles) {
        this.service = service;
        this.roles = roles;
    }

    @GetMapping("/api/rooms/{roomId}/check-in")
    public CheckInPreviewResponse preview(@PathVariable UUID roomId, Authentication authentication) {
        return service.preview(roomId, actor(authentication));
    }

    @PostMapping("/api/rooms/{roomId}/check-in")
    public CheckInResultResponse checkIn(@PathVariable UUID roomId, @Valid @RequestBody CheckInRequest request,
            Authentication authentication) {
        Actor actor = actor(authentication);
        try {
            return service.checkIn(roomId, request.method(), actor);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException concurrentUpdate) {
            // Another check-in or the expiry sweep committed first (the booking's version, or on the room's first
            // check-in its device rows). The conflict only surfaces at commit, so a fresh transaction re-reads the
            // booking and answers "already in use" or "expired" instead of a generic conflict (FR-011, FR-004).
            return service.checkIn(roomId, request.method(), actor);
        }
    }

    private Actor actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Anmeldung erforderlich.");
        }
        return new Actor(user.userId(), roles.isAdmin(user.userId()));
    }
}
