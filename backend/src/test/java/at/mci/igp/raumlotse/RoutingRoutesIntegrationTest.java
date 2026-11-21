package at.mci.igp.raumlotse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/** Route endpoint: ground floor corridor y = 0.5, a staircase at x = 0.6 and an elevator at x = 0.9, both to the 1. OG. */
class RoutingRoutesIntegrationTest extends RoutingIntegrationSupport {

    UUID roomA;
    UUID roomB;
    UUID roomUp;
    UUID wpStairsEg;
    UUID wpLiftEg;
    UUID wpA;
    UUID wpB;

    @BeforeEach
    void network() throws Exception {
        wpA = waypoint(mapEg, 0.1, 0.5);
        wpB = waypoint(mapEg, 0.5, 0.5);
        wpStairsEg = waypoint(mapEg, 0.6, 0.5);
        wpLiftEg = waypoint(mapEg, 0.9, 0.5);
        segment(mapEg, wpA, wpB);
        segment(mapEg, wpB, wpStairsEg);
        segment(mapEg, wpStairsEg, wpLiftEg);
        UUID upStairs = waypoint(mapOg, 0.6, 0.5);
        UUID upLift = waypoint(mapOg, 0.9, 0.5);
        UUID upRoomWp = waypoint(mapOg, 0.7, 0.5);
        segment(mapOg, upStairs, upRoomWp);
        segment(mapOg, upRoomWp, upLift);
        UUID stairs = connection("Treppe A", "STAIRS");
        UUID lift = connection("Aufzug 1", "ELEVATOR");
        attachPoint(mapEg, connectionPoint(stairs, mapEg, 0.6, 0.5), wpStairsEg);
        attachPoint(mapOg, connectionPoint(stairs, mapOg, 0.6, 0.5), upStairs);
        attachPoint(mapEg, connectionPoint(lift, mapEg, 0.9, 0.5), wpLiftEg);
        attachPoint(mapOg, connectionPoint(lift, mapOg, 0.9, 0.5), upLift);
        roomA = roomEg;
        roomB = room("B", floorEg);
        roomUp = roomOg;
        roomAt(mapEg, roomA, wpA, 0.1, 0.5);
        roomAt(mapEg, roomB, wpB, 0.5, 0.5);
        roomAt(mapOg, roomUp, upRoomWp, 0.7, 0.5);
    }

    String url(UUID from, UUID to, boolean accessible) {
        return "/api/routes?fromRoomId=" + from + "&toRoomId=" + to + "&accessibleOnly=" + accessible;
    }

    @Test
    void sameFloorRoute() throws Exception {
        mvc.perform(get(url(roomA, roomB, false)).with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceMeters").value(8.0))
                .andExpect(jsonPath("$.walkingSeconds").value(7))
                .andExpect(jsonPath("$.accessibleOnly").value(false))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].floorName").value("EG"));
    }

    @Test
    void crossFloorRoutePrefersStairsAndAccessibleOnlyUsesTheElevator() throws Exception {
        mvc.perform(get(url(roomA, roomUp, false)).with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[?(@.floorChange)].floorChange.type").value("STAIRS"))
                .andExpect(jsonPath("$.steps[?(@.floorChange)].floorChange.connectionName").value("Treppe A"))
                .andExpect(jsonPath("$.steps[?(@.floorChange)].floorChange.fromFloorName").value("EG"))
                .andExpect(jsonPath("$.steps[?(@.floorChange)].floorChange.toFloorName").value("1. OG"));
        mvc.perform(get(url(roomA, roomUp, true)).with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessibleOnly").value(true))
                .andExpect(jsonPath("$.steps[?(@.floorChange)].floorChange.type").value("ELEVATOR"));
    }

    @Test
    void accessibleOnlyWithoutElevatorIs409NoAccessibleRoute() throws Exception {
        db.update("delete from connection where name = 'Aufzug 1'");
        mvc.perform(get(url(roomA, roomUp, true)).with(actor(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ACCESSIBLE_ROUTE"));
        mvc.perform(get(url(roomA, roomUp, false)).with(actor(user))).andExpect(status().isOk());
    }

    @Test
    void unattachedRoomIs409() throws Exception {
        UUID loose = room("Loose", floorEg);
        place(mapEg, loose, 0.3, 0.2);
        mvc.perform(get(url(roomA, loose, false)).with(actor(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_ATTACHED"));
    }

    @Test
    void mapWithoutScaleIs409() throws Exception {
        send(delete("/api/maps/" + mapOg + "/reference-segment"), null);
        mvc.perform(get(url(roomA, roomUp, false)).with(actor(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCALE_MISSING"));
    }

    @Test
    void separateNetworkPartIs409NotConnected() throws Exception {
        UUID island = waypoint(mapEg, 0.95, 0.9);
        UUID lonely = room("Lonely", floorEg);
        roomAt(mapEg, lonely, island, 0.95, 0.9);
        mvc.perform(get(url(roomA, lonely, false)).with(actor(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_CONNECTED"));
    }

    @Test
    void unknownRoomIs404AndBadParametersAre400() throws Exception {
        mvc.perform(get(url(roomA, UUID.randomUUID(), false)).with(actor(user))).andExpect(status().isNotFound());
        mvc.perform(get("/api/routes?fromRoomId=" + roomA).with(actor(user))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/routes?fromRoomId=nope&toRoomId=" + roomB).with(actor(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousIs401AndThereAreNoWriteMethods() throws Exception {
        mvc.perform(get(url(roomA, roomB, false))).andExpect(status().isUnauthorized());
        for (var builder : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                post("/api/routes"), put("/api/routes"), delete("/api/routes")}) {
            mvc.perform(builder.with(actor(admin)).with(SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().is4xxClientError());
        }
    }
}
