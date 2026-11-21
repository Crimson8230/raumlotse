package at.mci.igp.raumlotse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** Results are computed per request: an edit through the path-network API shows up in the next request. */
class RoutingFreshnessIntegrationTest extends RoutingIntegrationSupport {

    UUID a;
    UUID b;
    UUID wpA;
    UUID wpB;
    UUID segment;

    @BeforeEach
    void twoRooms() throws Exception {
        wpA = waypoint(mapEg, 0.2, 0.5);
        wpB = waypoint(mapEg, 0.5, 0.5);
        segment(mapEg, wpA, wpB);
        a = roomEg;
        b = room("B", floorEg);
        roomAt(mapEg, a, wpA, 0.2, 0.5);
        roomAt(mapEg, b, wpB, 0.5, 0.5);
        String network = mvc.perform(get("/api/maps/" + mapEg + "/path-network").with(actor(user)))
                .andReturn().getResponse().getContentAsString();
        segment = UUID.fromString(JsonPath.read(network, "$.segments[0].id"));
    }

    ResultActions neighbors() throws Exception {
        return mvc.perform(get("/api/rooms/" + a + "/neighbors").with(actor(user)));
    }

    @Test
    void movingAWaypointChangesTheNextDistance() throws Exception {
        neighbors().andExpect(jsonPath("$.entries[0].distanceMeters").value(6.0));
        send(put("/api/maps/" + mapEg + "/waypoints/" + wpB), "{\"x\":0.7,\"y\":0.5}");
        neighbors().andExpect(jsonPath("$.entries[0].distanceMeters").value(10.0));
        mvc.perform(get("/api/routes?fromRoomId=" + a + "&toRoomId=" + b).with(actor(user)))
                .andExpect(jsonPath("$.distanceMeters").value(10.0));
    }

    @Test
    void deletingASegmentDisconnectsTheRooms() throws Exception {
        neighbors().andExpect(jsonPath("$.available").value(true));
        send(delete("/api/maps/" + mapEg + "/segments/" + segment), null);
        neighbors().andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.reason").value("NO_NEIGHBORS"));
        mvc.perform(get("/api/routes?fromRoomId=" + a + "&toRoomId=" + b).with(actor(user)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_CONNECTED"));
    }

    @Test
    void changingTheScaleChangesAllDistancesAndRemovingItDisablesThem() throws Exception {
        send(put("/api/maps/" + mapEg + "/reference-segment"),
                "{\"x1\":0.0,\"y1\":0.5,\"x2\":1.0,\"y2\":0.5,\"lengthMeters\":40}");
        neighbors().andExpect(jsonPath("$.entries[0].distanceMeters").value(12.0));
        send(delete("/api/maps/" + mapEg + "/reference-segment"), null);
        neighbors().andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.reason").value("SCALE_MISSING"));
    }

    @Test
    void removingTheAttachmentTakesTheRoomOutOfTheResults() throws Exception {
        send(delete("/api/maps/" + mapEg + "/room-attachments/" + b), null);
        neighbors().andExpect(jsonPath("$.available").value(false));
    }
}
