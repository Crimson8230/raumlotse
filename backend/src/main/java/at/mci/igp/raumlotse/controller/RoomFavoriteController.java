package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.FavoriteRoomResponse;
import at.mci.igp.raumlotse.service.RoomFavoriteService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Favorites of the signed-in user (feature 017); the user always comes from the session, never the request. */
@RestController
@RequestMapping("/api/me/favorite-rooms")
public class RoomFavoriteController {

    private final RoomFavoriteService favoriteService;

    public RoomFavoriteController(RoomFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public List<FavoriteRoomResponse> list(Authentication authentication) {
        return favoriteService.list(resolveUser(authentication).userId());
    }

    @PutMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mark(@PathVariable UUID roomId, Authentication authentication) {
        favoriteService.mark(resolveUser(authentication).userId(), roomId);
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmark(@PathVariable UUID roomId, Authentication authentication) {
        favoriteService.unmark(resolveUser(authentication).userId(), roomId);
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
