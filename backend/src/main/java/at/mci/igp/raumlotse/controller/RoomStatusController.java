package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.RoomStatusResponse;
import at.mci.igp.raumlotse.service.RoomStatusService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only room status for the room display and future polling devices (feature 014). */
@RestController
public class RoomStatusController {
    private final RoomStatusService service;
    private final UserRoleSafety roles;

    public RoomStatusController(RoomStatusService service, UserRoleSafety roles) {
        this.service = service;
        this.roles = roles;
    }

    @GetMapping("/api/rooms/{roomId}/status")
    public RoomStatusResponse status(@PathVariable UUID roomId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Anmeldung erforderlich.");
        }
        return service.status(roomId, new Actor(user.userId(), roles.isAdmin(user.userId())));
    }
}
