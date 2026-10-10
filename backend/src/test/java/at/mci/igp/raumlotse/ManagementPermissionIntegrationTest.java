package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import at.mci.igp.raumlotse.service.AdminModeService;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Exercises every configurable management gate through valid business requests in both UI modes. */
@Transactional
class ManagementPermissionIntegrationTest extends MapIntegrationSupport {
    static Stream<Arguments> managementActions() {
        return Stream.of(
                Arguments.of("BUILDING_MANAGE", "building"),
                Arguments.of("FLOOR_MANAGE", "floor"),
                Arguments.of("EQUIPMENT_TYPE_MANAGE", "equipment_type"),
                Arguments.of("ROOM_MANAGE", "room"),
                Arguments.of("MAP_MANAGE", "map"),
                Arguments.of("ROOM_PLACEMENT_MANAGE", "room_placement"),
                Arguments.of("CONNECTION_MANAGE", "connection"),
                Arguments.of("STATISTICS_READ", "statistics"),
                Arguments.of("RESERVATION_MAINTENANCE", "maintenance"));
    }

    @ParameterizedTest
    @MethodSource("managementActions")
    void eachManagementRightDeniesWithoutMutationAndExecutesValidActionWithModeOffAndOn(
            String permission, String action) throws Exception {
        for (boolean adminMode : new boolean[] {false, true}) {
            int deniedStatus = performAction(action, adminMode, true);
            assertThat(deniedStatus).as(permission + " denied with admin mode=" + adminMode).isEqualTo(403);
        }

        db.update("insert into role_permission(role_code,permission_code) values ('STUDENT',?)", permission);
        try {
            for (boolean adminMode : new boolean[] {false, true}) {
                int allowedStatus = performAction(action, adminMode, false);
                assertThat(allowedStatus).as(permission + " valid action with admin mode=" + adminMode)
                        .isBetween(200, 299);
            }
        } finally {
            db.update("delete from role_permission where role_code='STUDENT' and permission_code=?", permission);
        }
    }

    private int performAction(String action, boolean adminMode, boolean expectDenied) throws Exception {
        UUID mapId = null;
        if (action.equals("map") || action.equals("room_placement")) mapId = uploadMap(floorEg);
        String table = switch (action) {
            case "building" -> "building";
            case "floor" -> "floor";
            case "equipment_type" -> "equipment_type";
            case "room" -> "room";
            case "map" -> "floor_map";
            case "room_placement" -> "room_placement";
            case "connection" -> "connection";
            case "statistics", "maintenance" -> null;
            default -> throw new IllegalArgumentException(action);
        };
        Long before = table == null ? null : db.queryForObject("select count(*) from " + table, Long.class);
        HttpMethod method;
        String path;
        String body = "{}";
        switch (action) {
            case "building" -> { method = HttpMethod.POST; path = "/api/buildings"; body = "{\"name\":\"Granted building " + UUID.randomUUID() + "\"}"; }
            case "floor" -> { method = HttpMethod.POST; path = "/api/buildings/" + building + "/floors"; body = "{\"name\":\"Granted floor " + UUID.randomUUID() + "\",\"groundFloor\":false}"; }
            case "equipment_type" -> { method = HttpMethod.POST; path = "/api/equipment-types"; body = "{\"name\":\"Granted equipment " + UUID.randomUUID() + "\"}"; }
            case "room" -> { method = HttpMethod.POST; path = "/api/rooms"; body = "{\"name\":\"Granted room " + UUID.randomUUID() + "\",\"floorId\":\"" + floorEg + "\",\"seatingArrangements\":[{\"name\":\"Theater\",\"maxCapacity\":20}]}"; }
            case "map" -> { method = HttpMethod.DELETE; path = "/api/maps/" + mapId; }
            case "room_placement" -> { method = HttpMethod.PUT; path = "/api/maps/" + mapId + "/placements/" + roomEg; body = "{\"x\":0.4,\"y\":0.6}"; }
            case "connection" -> { method = HttpMethod.POST; path = "/api/connections"; body = "{\"name\":\"Granted connection " + UUID.randomUUID() + "\",\"type\":\"STAIRS\"}"; }
            case "statistics" -> { method = HttpMethod.GET; path = "/api/admin/statistics?from=2026-01-01&to=2026-12-31"; }
            case "maintenance" -> { method = HttpMethod.POST; path = "/api/reservations/expire-unattended"; }
            default -> throw new IllegalArgumentException(action);
        }
        MockHttpServletRequestBuilder request = request(method, path).with(actor(user)).with(csrf())
                .sessionAttr(AdminModeService.ATTRIBUTE, adminMode);
        if (method != HttpMethod.GET && !action.equals("map")) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        var result = mvc.perform(request).andReturn();
        int statusCode = result.getResponse().getStatus();
        if (expectDenied) {
            assertThat(statusCode).isEqualTo(403);
            assertThat(result.getResponse().getContentAsString()).contains("PERMISSION_REQUIRED");
            if (table != null) assertThat(db.queryForObject("select count(*) from " + table, Long.class)).isEqualTo(before);
        } else {
            assertThat(statusCode).isBetween(200, 299);
            if (statusCode != 204) {
                assertThat(result.getResponse().getContentAsString())
                        .as(action + " must return its business result")
                        .isNotBlank();
            }
        }
        return statusCode;
    }
}
