package at.mci.igp.raumlotse.service;

import static at.mci.igp.raumlotse.domain.PermissionCode.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthorizationMatrixTest {
    @Test void managementAreasHaveSeparateRights() {
        assertThat(RoleAccessFilter.classify("POST", "/api/buildings").permission()).isEqualTo(BUILDING_MANAGE);
        assertThat(RoleAccessFilter.classify("POST", "/api/buildings/x/floors").permission()).isEqualTo(FLOOR_MANAGE);
        assertThat(RoleAccessFilter.classify("PUT", "/api/floors/x/map").permission()).isEqualTo(MAP_MANAGE);
        assertThat(RoleAccessFilter.classify("GET", "/api/maps/x/unplaced-rooms").permission()).isEqualTo(ROOM_PLACEMENT_MANAGE);
        assertThat(RoleAccessFilter.classify("GET", "/api/admin/statistics").permission()).isEqualTo(STATISTICS_READ);
        assertThat(RoleAccessFilter.classify("POST", "/api/reservations/expire-unattended").permission())
                .isEqualTo(RESERVATION_MAINTENANCE);
        assertThat(RoleAccessFilter.classify("PUT", "/api/admin/roles/ADMIN/permissions").admin()).isTrue();
        assertThat(RoleAccessFilter.classify("POST", "/api/unknown")).isNull();
    }
}
