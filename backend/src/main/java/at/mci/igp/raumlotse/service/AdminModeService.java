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
        return effective(session, actor.admin());
    }

    public boolean effective(HttpSession session, boolean canUseAdminMode) {
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(ATTRIBUTE))) {
            return false;
        }
        if (!canUseAdminMode) {
            session.removeAttribute(ATTRIBUTE);
            return false;
        }
        return true;
    }

    public boolean set(HttpSession session, Actor actor, boolean enabled) {
        return set(session, actor.userId(), actor.admin(), enabled);
    }

    public boolean set(HttpSession session, java.util.UUID userId, boolean canUseAdminMode, boolean enabled) {
        if (enabled && !canUseAdminMode) {
            AccessDeniedLog.admin("PUT", "/api/auth/admin-mode", userId);
            throw new UserRoleException(403, "ADMIN_MODE_NOT_ALLOWED", "Der Administrationsmodus ist nicht verfügbar.");
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
