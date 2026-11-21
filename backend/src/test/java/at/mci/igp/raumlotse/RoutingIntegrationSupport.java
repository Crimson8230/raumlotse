package at.mci.igp.raumlotse;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Draws path networks through the feature-023 API. Maps are 200 x 100 px; every map gets a horizontal reference
 * segment over its full width of 20 m, so 1 relative x unit is 20 m and 1 relative y unit is 10 m.
 */
abstract class RoutingIntegrationSupport extends MapIntegrationSupport {

    protected UUID mapEg;
    protected UUID mapOg;

    @BeforeEach
    void routingFixtures() throws Exception {
        db.update("update floor set position = 0, ground_floor = true where id = ?", floorEg);
        db.update("update floor set position = 1, ground_floor = false where id = ?", floorOg);
        mapEg = uploadMap(floorEg);
        mapOg = uploadMap(floorOg);
        reference(mapEg);
        reference(mapOg);
    }

    protected void send(MockHttpServletRequestBuilder builder, String body) throws Exception {
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mvc.perform(builder.with(actor(admin)).with(csrf())).andExpect(status().is2xxSuccessful());
    }

    protected void reference(UUID map) throws Exception {
        send(put("/api/maps/" + map + "/reference-segment"),
                "{\"x1\":0.0,\"y1\":0.5,\"x2\":1.0,\"y2\":0.5,\"lengthMeters\":20}");
    }

    protected UUID waypoint(UUID map, double x, double y) throws Exception {
        String body = mvc.perform(post("/api/maps/" + map + "/waypoints").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":" + x + ",\"y\":" + y + "}").with(actor(admin)).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    protected void segment(UUID map, UUID a, UUID b) throws Exception {
        send(post("/api/maps/" + map + "/segments"),
                "{\"fromWaypointId\":\"" + a + "\",\"toWaypointId\":\"" + b + "\"}");
    }

    protected void place(UUID map, UUID room, double x, double y) throws Exception {
        send(put("/api/maps/" + map + "/placements/" + room), "{\"x\":" + x + ",\"y\":" + y + "}");
    }

    protected void attach(UUID map, UUID room, UUID waypoint) throws Exception {
        send(put("/api/maps/" + map + "/room-attachments/" + room), "{\"waypointId\":\"" + waypoint + "\"}");
    }

    /** Places the room just north of the waypoint (marker 0.2 above) and attaches it there. */
    protected void roomAt(UUID map, UUID room, UUID waypoint, double x, double y) throws Exception {
        place(map, room, x, y - 0.2);
        attach(map, room, waypoint);
    }

    protected UUID connection(String name, String type) {
        UUID id = UUID.randomUUID();
        db.update("insert into connection(id,name,type) values (?,?,?)", id, name, type);
        return id;
    }

    protected UUID connectionPoint(UUID connection, UUID map, double x, double y) {
        UUID id = UUID.randomUUID();
        db.update("insert into connection_point(id,connection_id,map_id,x,y) values (?,?,?,?,?)",
                id, connection, map, x, y);
        return id;
    }

    protected void attachPoint(UUID map, UUID point, UUID waypoint) throws Exception {
        send(put("/api/maps/" + map + "/connection-point-attachments/" + point),
                "{\"waypointId\":\"" + waypoint + "\"}");
    }
}
