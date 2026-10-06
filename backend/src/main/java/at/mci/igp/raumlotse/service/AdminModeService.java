package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.exception.UserRoleException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * Administration mode (feature 013): a per-session flag that decides whether the interface shows administration
 * options. It never grants rights: the administrator role does (A6). The flag can only be set by administrators and
 * counts as off as soon as the user is no longer one.
 */
@Component
public class AdminModeService {

    public static final String ATTRIBUTE = "raumlotse.adminMode";

    /** The mode as the client should see it: the flag, and only while the actor is an administrator. */
    public boolean effective(HttpSession session, Actor actor) {
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(ATTRIBUTE))) {
            return false;
        }
        if (!actor.admin()) {
            session.removeAttribute(ATTRIBUTE);
            return false;
        }
        return true;
    }

    public boolean set(HttpSession session, Actor actor, boolean enabled) {
        if (!actor.admin()) {
            AccessDeniedLog.admin("PUT", "/api/auth/admin-mode", actor.userId());
            throw new UserRoleException(403, "ADMIN_REQUIRED", "Administratorrechte erforderlich.");
        }
        if (enabled) {
            session.setAttribute(ATTRIBUTE, Boolean.TRUE);
        } else {
            session.removeAttribute(ATTRIBUTE);
        }
        return enabled;
    }

    public void clear(HttpSession session) {
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }
}
