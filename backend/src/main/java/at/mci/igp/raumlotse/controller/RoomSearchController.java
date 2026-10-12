package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.RoomResponse;
import at.mci.igp.raumlotse.dto.RoomSearchRequest;
import at.mci.igp.raumlotse.service.RoomSearchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only room search (feature 008); covered by the default "authenticated" rule in SecurityConfig. */
@RestController
public class RoomSearchController {

    private final RoomSearchService roomSearchService;

    public RoomSearchController(RoomSearchService roomSearchService) {
        this.roomSearchService = roomSearchService;
    }

    @GetMapping("/api/rooms/search")
    public List<RoomResponse> search(@Valid @ModelAttribute RoomSearchRequest request, Authentication authentication) {
        return roomSearchService.search(request.toCriteria(), resolveUser(authentication).userId());
    }

    @GetMapping("/api/rooms/search/seating-arrangements")
    public List<String> seatingArrangementNames() {
        return roomSearchService.listSeatingArrangementNames();
    }

    private static AuthenticatedUser resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Anmeldung erforderlich.");
        }
        return user;
    }
}
