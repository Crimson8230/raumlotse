package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.dto.Problem;
import at.mci.igp.raumlotse.domain.PermissionCode;
import at.mci.igp.raumlotse.exception.UserRoleException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Deny-by-default action authorization for the current role permissions.
 *
 * <p>Every protected API method and path has an explicit rule. Reservation ownership and device eligibility are
 * checked in their services after this filter. Runs after CSRF validation and before MVC deserializes user payloads.
 */
public class RoleAccessFilter extends OncePerRequestFilter {

    public record Rule(PermissionCode permission, boolean admin) {}
    private record Entry(String method, Pattern path, Rule rule) {
        boolean matches(String candidateMethod, String candidatePath) {
            return method.equals(candidateMethod) && path.matcher(candidatePath).matches();
        }
    }
    private static final Rule IDENTITY = new Rule(null, false);
    private static final Rule ADMIN = new Rule(null, true);
    private static final List<Entry> RULES = rules();

    private static List<Entry> rules() {
        var result = new ArrayList<Entry>();
        add(result, "GET", "/api/auth/(me|roles)", IDENTITY);
        add(result, "PUT", "/api/auth/admin-mode", IDENTITY);
        add(result, "GET", "/api/admin/users(?:/[^/]+/roles)?", ADMIN);
        add(result, "PUT", "/api/admin/users/[^/]+/roles", ADMIN);
        add(result, "GET", "/api/admin/roles(?:/[^/]+/permissions)?", ADMIN);
        add(result, "PUT", "/api/admin/roles/[^/]+/permissions", ADMIN);
        add(result, "GET", "/api/admin/statistics", PermissionCode.STATISTICS_READ);
        add(result, "GET", "/api/rooms(?:/search(?:/seating-arrangements)?|/[^/]+)?", PermissionCode.READ);
        add(result, "GET", "/api/buildings(?:/[^/]+/floors)?", PermissionCode.READ);
        add(result, "GET", "/api/equipment-types", PermissionCode.READ);
        add(result, "GET", "/api/maps(?:/[^/]+(?:/image)?)?", PermissionCode.READ);
        add(result, "GET", "/api/connections", PermissionCode.READ);
        add(result, "GET", "/api/rooms/[^/]+/(available-equipment|reservations)", PermissionCode.READ);
        add(result, "GET", "/api/reservations/(my-upcoming|[^/]+)", PermissionCode.READ);
        add(result, "POST", "/api/rooms/[^/]+/reservations", PermissionCode.RESERVE);
        add(result, "PATCH", "/api/reservations/[^/]+", IDENTITY);
        add(result, "POST", "/api/reservations/[^/]+/(activate|complete|expire|cancel)", IDENTITY);
        add(result, "GET", "/api/rooms/[^/]+/device-controls", PermissionCode.OWN_ACTIVE_DEVICE_CONTROL);
        add(result, "POST", "/api/rooms/[^/]+/device-controls/[^/]+", PermissionCode.OWN_ACTIVE_DEVICE_CONTROL);
        add(result, "POST", "/api/buildings", PermissionCode.BUILDING_MANAGE);
        add(result, "PUT", "/api/buildings/[^/]+", PermissionCode.BUILDING_MANAGE);
        add(result, "POST", "/api/buildings/[^/]+/(deactivate|reactivate)", PermissionCode.BUILDING_MANAGE);
        add(result, "DELETE", "/api/buildings/[^/]+", PermissionCode.BUILDING_MANAGE);
        add(result, "POST", "/api/buildings/[^/]+/floors", PermissionCode.FLOOR_MANAGE);
        add(result, "PUT", "/api/floors/[^/]+", PermissionCode.FLOOR_MANAGE);
        add(result, "POST", "/api/floors/[^/]+/(deactivate|reactivate)", PermissionCode.FLOOR_MANAGE);
        add(result, "DELETE", "/api/floors/[^/]+", PermissionCode.FLOOR_MANAGE);
        add(result, "POST", "/api/equipment-types", PermissionCode.EQUIPMENT_TYPE_MANAGE);
        add(result, "PUT", "/api/equipment-types/[^/]+", PermissionCode.EQUIPMENT_TYPE_MANAGE);
        add(result, "POST", "/api/equipment-types/[^/]+/(deactivate|reactivate)", PermissionCode.EQUIPMENT_TYPE_MANAGE);
        add(result, "DELETE", "/api/equipment-types/[^/]+", PermissionCode.EQUIPMENT_TYPE_MANAGE);
        add(result, "POST", "/api/rooms", PermissionCode.ROOM_MANAGE);
        add(result, "PUT", "/api/rooms/[^/]+", PermissionCode.ROOM_MANAGE);
        add(result, "POST", "/api/rooms/[^/]+/(deactivate|reactivate)", PermissionCode.ROOM_MANAGE);
        add(result, "DELETE", "/api/rooms/[^/]+", PermissionCode.ROOM_MANAGE);
        add(result, "PUT", "/api/floors/[^/]+/map", PermissionCode.MAP_MANAGE);
        add(result, "DELETE", "/api/maps/[^/]+", PermissionCode.MAP_MANAGE);
        add(result, "GET", "/api/maps/[^/]+/unplaced-rooms", PermissionCode.ROOM_PLACEMENT_MANAGE);
        add(result, "PUT", "/api/maps/[^/]+/placements/[^/]+", PermissionCode.ROOM_PLACEMENT_MANAGE);
        add(result, "DELETE", "/api/maps/[^/]+/placements/[^/]+", PermissionCode.ROOM_PLACEMENT_MANAGE);
        add(result, "POST", "/api/connections", PermissionCode.CONNECTION_MANAGE);
        add(result, "PUT", "/api/connections/[^/]+(?:/points/[^/]+)?", PermissionCode.CONNECTION_MANAGE);
        add(result, "DELETE", "/api/connections/[^/]+(?:/points/[^/]+)?", PermissionCode.CONNECTION_MANAGE);
        add(result, "POST", "/api/reservations/expire-unattended", PermissionCode.RESERVATION_MAINTENANCE);
        return List.copyOf(result);
    }
    private static void add(List<Entry> entries, String method, String path, PermissionCode permission) {
        add(entries, method, path, new Rule(permission, false));
    }
    private static void add(List<Entry> entries, String method, String path, Rule rule) {
        entries.add(new Entry(method, Pattern.compile(path), rule));
    }
    public static Rule classify(String method, String path) {
        if ("HEAD".equals(method) || "OPTIONS".equals(method)) method = "GET";
        for (Entry entry : RULES) if (entry.matches(method, path)) return entry.rule();
        return null;
    }

    private final EffectivePermissionService permissions;
    private final ObjectMapper mapper;

    public RoleAccessFilter(EffectivePermissionService permissions, ObjectMapper mapper) {
        this.permissions = permissions;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathOf(request);
        if (!path.startsWith("/api/")) {
            return true;
        }
        return ("GET".equals(request.getMethod())
                && (path.equals("/api/health") || path.equals("/api/auth/csrf")))
                || ("POST".equals(request.getMethod()) && path.equals("/api/auth/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isAnonymous(authentication)) {
            // The security chain answers anonymous requests with its usual 401.
            chain.doFilter(request, response);
            return;
        }
        Rule rule = classify(request.getMethod(), pathOf(request));
        if (rule == null) {
            AccessDeniedLog.refusal(request.getMethod(), pathPattern(request), "unknown_action", 403,
                    RoleIdentityAdapter.actor(authentication));
            deny(response, 403, "PERMISSION_REQUIRED", "Sie dürfen diese Aktion nicht ausführen.");
            return;
        }
        try {
            var actor = RoleIdentityAdapter.actor(authentication);
            var snapshot = permissions.snapshot(actor, request);
            // Null is only possible for permissive mocks in isolated MVC tests.
            if (snapshot == null) {
                if (rule.admin()) permissions.requireAdmin(actor);
                else if (rule.permission() != null) permissions.require(actor, rule.permission());
            } else if (rule.admin() && !snapshot.admin()) {
                throw new UserRoleException(403, "ADMIN_REQUIRED", "Administratorrechte erforderlich.");
            } else if (rule.permission() != null && !snapshot.has(rule.permission())) {
                throw new UserRoleException(403, "PERMISSION_REQUIRED", "Sie dÃ¼rfen diese Aktion nicht ausfÃ¼hren.");
            }
        } catch (UserRoleException ex) {
            String category = ex.getStatus() == 503 ? "role_storage" : rule.admin() ? "admin" : "permission";
            AccessDeniedLog.refusal(request.getMethod(), pathPattern(request), category, ex.getStatus(),
                    RoleIdentityAdapter.actor(authentication));
            deny(response, ex.getStatus(), ex.getCode(), ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            AccessDeniedLog.refusal(request.getMethod(), pathPattern(request), "role_storage", 503,
                    RoleIdentityAdapter.actor(authentication));
            deny(response, 503, "ROLE_MANAGEMENT_UNAVAILABLE", "Die Rollenverwaltung ist vorübergehend nicht verfügbar.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAnonymous(Authentication authentication) {
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    private static String pathOf(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.isEmpty() ? request.getRequestURI() : path;
    }

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
