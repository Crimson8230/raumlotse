package at.mci.igp.raumlotse.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.service.RoleAccessFilter;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Feature 013 (FR-016): every mapped endpoint must be classified here. An endpoint without an entry fails the
 * completeness test, so a new endpoint can never silently become writable by regular users.
 */
class AuthorizationMatrixTest {

    enum Access { PUBLIC, USER_READ, USER_ACTION, ADMIN }

    static final Map<String, Access> MATRIX = new LinkedHashMap<>();

    static void add(Access access, String... routes) {
        for (String route : routes) {
            MATRIX.put(route, access);
        }
    }

    static {
        add(Access.PUBLIC, "GET /api/health", "GET /api/auth/csrf", "POST /api/auth/login");
        add(Access.USER_READ, "GET /api/auth/me", "GET /api/auth/roles");
        add(Access.USER_ACTION, "PUT /api/auth/admin-mode");
        add(Access.USER_READ,
                "GET /api/rooms", "GET /api/rooms/{roomId}", "GET /api/rooms/search",
                "GET /api/rooms/search/seating-arrangements", "GET /api/buildings",
                "GET /api/buildings/{buildingId}/floors", "GET /api/equipment-types",
                "GET /api/rooms/{roomId}/available-equipment", "GET /api/rooms/{roomId}/reservations",
                "GET /api/reservations/{reservationId}", "GET /api/reservations/my-upcoming",
                "GET /api/rooms/{roomId}/device-controls", "GET /api/maps", "GET /api/maps/{mapId}",
                "GET /api/maps/{mapId}/image", "GET /api/maps/{mapId}/unplaced-rooms", "GET /api/connections",
                "GET /api/rooms/{roomId}/check-in", "GET /api/rooms/{roomId}/status");
        add(Access.USER_ACTION,
                "POST /api/rooms/{roomId}/reservations", "PATCH /api/reservations/{reservationId}",
                "POST /api/reservations/{reservationId}/activate", "POST /api/reservations/{reservationId}/complete",
                "POST /api/reservations/{reservationId}/expire", "POST /api/reservations/{reservationId}/cancel",
                "POST /api/rooms/{roomId}/device-controls/{kind}", "POST /api/rooms/{roomId}/check-in");
        add(Access.ADMIN,
                "POST /api/rooms", "PUT /api/rooms/{roomId}", "DELETE /api/rooms/{roomId}",
                "POST /api/rooms/{roomId}/deactivate", "POST /api/rooms/{roomId}/reactivate",
                "POST /api/buildings", "PUT /api/buildings/{buildingId}", "DELETE /api/buildings/{buildingId}",
                "POST /api/buildings/{buildingId}/deactivate", "POST /api/buildings/{buildingId}/reactivate",
                "POST /api/buildings/{buildingId}/floors", "PUT /api/floors/{floorId}",
                "DELETE /api/floors/{floorId}", "POST /api/floors/{floorId}/deactivate",
                "POST /api/floors/{floorId}/reactivate", "POST /api/equipment-types",
                "PUT /api/equipment-types/{equipmentTypeId}", "DELETE /api/equipment-types/{equipmentTypeId}",
                "POST /api/equipment-types/{equipmentTypeId}/deactivate",
                "POST /api/equipment-types/{equipmentTypeId}/reactivate",
                "POST /api/reservations/expire-unattended",
                "DELETE /api/maps/{mapId}", "PUT /api/maps/{mapId}/placements/{roomId}",
                "DELETE /api/maps/{mapId}/placements/{roomId}", "PUT /api/floors/{floorId}/map",
                "POST /api/connections", "PUT /api/connections/{connectionId}",
                "DELETE /api/connections/{connectionId}", "PUT /api/connections/{connectionId}/points/{mapId}",
                "DELETE /api/connections/{connectionId}/points/{mapId}",
                "GET /api/admin/users", "GET /api/admin/users/{id}/roles", "PUT /api/admin/users/{id}/roles",
                "GET /api/admin/statistics", "POST /api/admin/rooms/{roomId}/presence-events",
                "GET /api/admin/check-in-settings", "PUT /api/admin/check-in-settings");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyMappedEndpointIsClassified() throws Exception {
        Set<String> mapped = new TreeSet<>(mappedRoutes());
        Set<String> classified = new TreeSet<>(MATRIX.keySet());

        Set<String> unclassified = new TreeSet<>(mapped);
        unclassified.removeAll(classified);
        Set<String> stale = new TreeSet<>(classified);
        stale.removeAll(mapped);

        assertThat(unclassified).as("mapped endpoints missing from the authorization matrix").isEmpty();
        assertThat(stale).as("matrix entries without a mapped endpoint").isEmpty();
    }

    static List<String> routes(Access access) {
        return MATRIX.entrySet().stream().filter(e -> e.getValue() == access).map(Map.Entry::getKey).toList();
    }

    static List<String> adminRoutes() {
        return routes(Access.ADMIN);
    }

    static List<String> userRoutes() {
        List<String> all = new ArrayList<>(routes(Access.USER_READ));
        all.addAll(routes(Access.USER_ACTION));
        return all;
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void nonAdminIsRefusedOnAdminRoutes(String route) throws Exception {
        UserRoleSafety safety = mock(UserRoleSafety.class);
        doThrow(new UserRoleException(403, "ADMIN_REQUIRED", "Administratorrechte erforderlich."))
                .when(safety).requireAdmin(any());

        var result = run(route, safety, true);

        assertThat(result.response().getStatus()).isEqualTo(403);
        assertThat(result.response().getContentAsString()).contains("ADMIN_REQUIRED");
        assertThat(result.chainInvoked()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void adminPassesOnAdminRoutes(String route) throws Exception {
        UserRoleSafety safety = mock(UserRoleSafety.class);

        var result = run(route, safety, true);

        assertThat(result.chainInvoked()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("userRoutes")
    void userRoutesNeverNeedTheAdminCheck(String route) throws Exception {
        UserRoleSafety safety = mock(UserRoleSafety.class);

        var result = run(route, safety, true);

        assertThat(result.chainInvoked()).isTrue();
        verifyNoInteractions(safety);
    }

    @Test
    void unknownWriteUnderApiIsDeniedByDefault() throws Exception {
        UserRoleSafety safety = mock(UserRoleSafety.class);
        doThrow(new UserRoleException(403, "ADMIN_REQUIRED", "Administratorrechte erforderlich."))
                .when(safety).requireAdmin(any());

        var result = run("POST /api/some-future-endpoint", safety, true);

        assertThat(result.response().getStatus()).isEqualTo(403);
        assertThat(result.chainInvoked()).isFalse();
    }

    @Test
    void anonymousRequestsOnNonAdminRoutesAreLeftToTheSecurityChain() throws Exception {
        UserRoleSafety safety = mock(UserRoleSafety.class);

        var result = run("POST /api/rooms", safety, false);

        assertThat(result.chainInvoked()).isTrue();
        verifyNoInteractions(safety);
    }

    @Test
    void anonymousRequestsOnAdminUserRoutesAreRefusedWith401() throws Exception {
        UserRoleSafety safety = mock(UserRoleSafety.class);

        var result = run("GET /api/admin/users", safety, false);

        assertThat(result.response().getStatus()).isEqualTo(401);
        assertThat(result.chainInvoked()).isFalse();
    }

    @Test
    void unavailableRoleLookupAnswers503() throws Exception {
        UserRoleSafety safety = mock(UserRoleSafety.class);
        doThrow(new IllegalStateException("db down")).when(safety).requireAdmin(any());

        var result = run("POST /api/rooms", safety, true);

        assertThat(result.response().getStatus()).isEqualTo(503);
        assertThat(result.chainInvoked()).isFalse();
    }

    record Outcome(MockHttpServletResponse response, boolean chainInvoked) { }

    @SuppressWarnings("unchecked")
    private Outcome run(String route, UserRoleSafety safety, boolean signedIn) throws Exception {
        String[] parts = route.split(" ", 2);
        String path = parts[1].replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        var request = new MockHttpServletRequest(parts[0], path);
        request.setServletPath(path);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        if (signedIn) {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new AuthenticatedUser(UUID.randomUUID(), "Tester"), null, List.of()));
        }
        ObjectProvider<UserRoleSafety> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(safety);

        new RoleAccessFilter(provider, JsonMapper.builder().build()).doFilter(request, response, chain);
        return new Outcome(response, chain.getRequest() != null);
    }

    private static List<String> mappedRoutes() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> routes = new ArrayList<>();
        for (var definition : scanner.findCandidateComponents("at.mci.igp.raumlotse.controller")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            String prefix = firstPath(AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class));
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String path = prefix + firstPath(mapping);
                for (RequestMethod verb : mapping.method()) {
                    routes.add(verb.name() + " " + path);
                }
            }
        }
        return routes;
    }

    private static String firstPath(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) {
            return "";
        }
        return mapping.path()[0];
    }
}
