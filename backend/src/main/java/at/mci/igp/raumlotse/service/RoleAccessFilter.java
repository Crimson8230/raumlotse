package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.dto.Problem;
import at.mci.igp.raumlotse.exception.UserRoleException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Deny-by-default administrator gate (feature 013, FR-016).
 *
 * <p>Every request under {@code /api} that is not a safe read needs the ADMIN role, and so does everything under
 * {@code /api/admin}. The only exceptions are the explicit user actions below; reservation ownership and device
 * eligibility are enforced by the services for those. Runs after CSRF validation and before MVC deserializes any
 * user payload.
 */
public class RoleAccessFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private record UserAction(String method, Pattern path) {
        static UserAction of(String method, String regex) {
            return new UserAction(method, Pattern.compile(regex));
        }

        boolean matches(String requestMethod, String requestPath) {
            return method.equals(requestMethod) && path.matcher(requestPath).matches();
        }
    }

    private static final List<UserAction> USER_ACTIONS = List.of(
            UserAction.of("POST", "/api/auth/login"),
            UserAction.of("PUT", "/api/auth/admin-mode"),
            UserAction.of("POST", "/api/rooms/[^/]+/reservations"),
            UserAction.of("PATCH", "/api/reservations/[^/]+"),
            UserAction.of("POST", "/api/reservations/[^/]+/(activate|complete|expire|cancel)"),
            UserAction.of("POST", "/api/rooms/[^/]+/device-controls/[^/]+"));

    private final ObjectProvider<UserRoleSafety> safety;
    private final ObjectMapper mapper;

    public RoleAccessFilter(ObjectProvider<UserRoleSafety> safety, ObjectMapper mapper) {
        this.safety = safety;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathOf(request);
        if (!path.startsWith("/api/")) {
            return true;
        }
        if (isAdminArea(path)) {
            return false;
        }
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        return USER_ACTIONS.stream().anyMatch(action -> action.matches(request.getMethod(), path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAdminArea(pathOf(request)) && isAnonymous(authentication)) {
            // The security chain answers anonymous requests with its usual 401.
            chain.doFilter(request, response);
            return;
        }
        try {
            var actor = RoleIdentityAdapter.actor(authentication);
            var checks = safety.getIfAvailable();
            if (checks == null) {
                throw UserRoleException.unavailable();
            }
            try {
                checks.requireAdmin(actor);
            } catch (UserRoleException ex) {
                if (ex.getStatus() == 403) {
                    AccessDeniedLog.admin(request.getMethod(), pathPattern(request), actor);
                }
                throw ex;
            }
        } catch (UserRoleException ex) {
            deny(response, ex.getStatus(), ex.getCode(), ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            deny(response, 503, "ROLE_MANAGEMENT_UNAVAILABLE", "Die Rollenverwaltung ist vorübergehend nicht verfügbar.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAdminArea(String path) {
        return path.equals("/api/admin") || path.startsWith("/api/admin/");
    }

    private static boolean isAnonymous(Authentication authentication) {
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    private static String pathOf(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.isEmpty() ? request.getRequestURI() : path;
    }

    /** Route without identifiers, so the log never carries ids of other users' data. */
    private static String pathPattern(HttpServletRequest request) {
        return pathOf(request).replaceAll(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "{id}");
    }

    private void deny(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(),
                Problem.of(status, HttpStatus.valueOf(status).getReasonPhrase(), message, code));
    }
}
