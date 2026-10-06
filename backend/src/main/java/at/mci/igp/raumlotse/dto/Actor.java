package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/** The signed-in caller of a request together with the administrator decision made for it. */
public record Actor(UUID userId, boolean admin) {

    public static Actor from(Authentication authentication, UserRoleSafety roles) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UserRoleException(401, "AUTHENTICATION_REQUIRED", "Anmeldung erforderlich.");
        }
        return new Actor(user.userId(), roles.isAdmin(user.userId()));
    }
}
