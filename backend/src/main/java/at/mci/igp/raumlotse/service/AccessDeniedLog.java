package at.mci.igp.raumlotse.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Greppable refusal log (feature 013, FR-026): category, method, route pattern and user id only. */
public final class AccessDeniedLog {

    private static final Logger log = LoggerFactory.getLogger(AccessDeniedLog.class);

    private AccessDeniedLog() {
    }

    public static void admin(String method, String route, UUID userId) {
        log.warn("access_denied category=admin method={} route={} userId={}", method, route, userId);
    }

    public static void ownership(String action, UUID userId) {
        log.warn("access_denied category=ownership action={} userId={}", action, userId);
    }
}
