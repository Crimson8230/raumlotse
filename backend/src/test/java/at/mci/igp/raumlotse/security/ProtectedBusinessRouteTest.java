package at.mci.igp.raumlotse.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.controller.BuildingController;
import at.mci.igp.raumlotse.controller.EquipmentTypeController;
import at.mci.igp.raumlotse.controller.FloorController;
import at.mci.igp.raumlotse.controller.CsrfController;
import at.mci.igp.raumlotse.controller.HealthController;
import at.mci.igp.raumlotse.controller.RoomController;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.EquipmentTypeService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.RoomService;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({HealthController.class, CsrfController.class, RoomController.class, BuildingController.class,
        FloorController.class, EquipmentTypeController.class})
@Import(SecurityConfig.class)
class ProtectedBusinessRouteTest {
    @Autowired MockMvc mvc;
    @MockitoBean RoomService roomService;
    @MockitoBean BuildingService buildingService;
    @MockitoBean FloorService floorService;
    @MockitoBean EquipmentTypeService equipmentTypeService;

    record Route(HttpMethod method, String path) { }

    static Stream<Route> reads() {
        return Stream.of(new Route(HttpMethod.GET, "/api/rooms"),
                new Route(HttpMethod.GET, "/api/rooms/00000000-0000-0000-0000-000000000001"),
                new Route(HttpMethod.GET, "/api/buildings"),
                new Route(HttpMethod.GET, "/api/buildings/00000000-0000-0000-0000-000000000001/floors"),
                new Route(HttpMethod.GET, "/api/floors/00000000-0000-0000-0000-000000000001"),
                new Route(HttpMethod.GET, "/api/equipment-types"));
    }

    static Stream<Route> mutations() {
        String id = "00000000-0000-0000-0000-000000000001";
        return Stream.of(new Route(HttpMethod.POST, "/api/rooms"),
                new Route(HttpMethod.PUT, "/api/rooms/" + id),
                new Route(HttpMethod.POST, "/api/rooms/" + id + "/deactivate"),
                new Route(HttpMethod.POST, "/api/rooms/" + id + "/reactivate"),
                new Route(HttpMethod.DELETE, "/api/rooms/" + id),
                new Route(HttpMethod.POST, "/api/buildings"),
                new Route(HttpMethod.PUT, "/api/buildings/" + id),
                new Route(HttpMethod.POST, "/api/buildings/" + id + "/deactivate"),
                new Route(HttpMethod.POST, "/api/buildings/" + id + "/reactivate"),
                new Route(HttpMethod.DELETE, "/api/buildings/" + id),
                new Route(HttpMethod.POST, "/api/buildings/" + id + "/floors"),
                new Route(HttpMethod.PUT, "/api/floors/" + id),
                new Route(HttpMethod.POST, "/api/floors/" + id + "/deactivate"),
                new Route(HttpMethod.POST, "/api/floors/" + id + "/reactivate"),
                new Route(HttpMethod.DELETE, "/api/floors/" + id),
                new Route(HttpMethod.POST, "/api/equipment-types"),
                new Route(HttpMethod.PUT, "/api/equipment-types/" + id),
                new Route(HttpMethod.POST, "/api/equipment-types/" + id + "/deactivate"),
                new Route(HttpMethod.POST, "/api/equipment-types/" + id + "/reactivate"),
                new Route(HttpMethod.DELETE, "/api/equipment-types/" + id));
    }

    @ParameterizedTest
    @MethodSource("reads")
    void anonymousBusinessReadsNeverReachHandlers(Route route) throws Exception {
        mvc.perform(request(route.method(), route.path())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verifyNoInteractions(roomService, buildingService, floorService, equipmentTypeService);
    }

    @ParameterizedTest
    @MethodSource("mutations")
    void anonymousTokenlessBusinessWritesReturn401WithoutCallingServices(Route route) throws Exception {
        mvc.perform(request(route.method(), route.path()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
        verifyNoInteractions(roomService, buildingService, floorService, equipmentTypeService);
    }

    @ParameterizedTest
    @MethodSource("mutations")
    @WithMockUser
    void authenticatedMutationWithoutCsrfReturns403WithoutCallingServices(Route route) throws Exception {
        mvc.perform(request(route.method(), route.path()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        verifyNoInteractions(roomService, buildingService, floorService, equipmentTypeService);
    }

    @Test
    void healthRemainsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
