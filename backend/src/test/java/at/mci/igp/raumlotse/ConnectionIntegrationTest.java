package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionIntegrationTest extends MapIntegrationSupport {

    UUID createConnection(String name, String type) throws Exception {
        String body = mvc.perform(post("/api/connections").with(actor(admin)).with(csrf())
                        .contentType("application/json").content("{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    void point(UUID connection, UUID map, double x, double y, int expectedStatus) throws Exception {
        mvc.perform(put("/api/connections/" + connection + "/points/" + map).with(actor(admin)).with(csrf())
                        .contentType("application/json").content("{\"x\":" + x + ",\"y\":" + y + "}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void elevatorSpanningTwoFloorsIsVisibleFromBothMapsAndRoutableByAnyUser() throws Exception {
        UUID mapEg = uploadMap(floorEg);
        UUID mapOg = uploadMap(floorOg);
        UUID elevator = createConnection("Aufzug A", "ELEVATOR");

        point(elevator, mapEg, 0.2, 0.3, 201);
        mvc.perform(get("/api/connections").with(actor(user)))
                .andExpect(jsonPath("$[0].incomplete").value(true));
        point(elevator, mapOg, 0.6, 0.7, 201);

        for (UUID map : new UUID[] { mapEg, mapOg }) {
            mvc.perform(get("/api/maps/" + map).with(actor(user)))
                    .andExpect(jsonPath("$.connections.length()").value(1))
                    .andExpect(jsonPath("$.connections[0].name").value("Aufzug A"))
                    .andExpect(jsonPath("$.connections[0].incomplete").value(false))
                    .andExpect(jsonPath("$.connections[0].points.length()").value(2));
        }
        point(elevator, mapEg, 0.9, 0.9, 200);
        assertThat(db.queryForObject("select count(*) from connection_point", Long.class)).isEqualTo(2);
    }

    @Test
    void removingAPointOrDeletingAMapLeavesTheRemainingPointsAndFlagsIncomplete() throws Exception {
        UUID mapEg = uploadMap(floorEg);
        UUID mapOg = uploadMap(floorOg);
        UUID elevator = createConnection("Aufzug A", "ELEVATOR");
        point(elevator, mapEg, 0.1, 0.1, 201);
        point(elevator, mapOg, 0.1, 0.1, 201);

        mvc.perform(delete("/api/maps/" + mapOg).with(actor(admin)).with(csrf())).andExpect(status().isNoContent());

        assertThat(db.queryForObject("select count(*) from connection_point", Long.class)).isEqualTo(1);
        mvc.perform(get("/api/connections").with(actor(user)))
                .andExpect(jsonPath("$[0].incomplete").value(true))
                .andExpect(jsonPath("$[0].points.length()").value(1));

        mvc.perform(delete("/api/connections/" + elevator + "/points/" + mapEg).with(actor(admin)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/connections/" + elevator + "/points/" + mapEg).with(actor(admin)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAConnectionRemovesItsPointsFromEveryMap() throws Exception {
        UUID mapEg = uploadMap(floorEg);
        UUID elevator = createConnection("Aufzug A", "ELEVATOR");
        point(elevator, mapEg, 0.1, 0.1, 201);

        mvc.perform(delete("/api/connections/" + elevator).with(actor(admin)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(db.queryForObject("select count(*) from connection_point", Long.class)).isZero();
        mvc.perform(get("/api/maps/" + mapEg).with(actor(user))).andExpect(jsonPath("$.connections").isEmpty());
    }

    @Test
    void duplicateNamesAndBadPointsAreRejected() throws Exception {
        UUID mapEg = uploadMap(floorEg);
        UUID elevator = createConnection("Aufzug A", "ELEVATOR");
        mvc.perform(post("/api/connections").with(actor(admin)).with(csrf()).contentType("application/json")
                        .content("{\"name\":\"aufzug a\",\"type\":\"STAIRS\"}"))
                .andExpect(status().isConflict());
        point(elevator, mapEg, 1.5, 0.1, 400);
        point(UUID.randomUUID(), mapEg, 0.1, 0.1, 404);
        point(elevator, UUID.randomUUID(), 0.1, 0.1, 404);
    }

    @Test
    void nonAdminCannotChangeConnections() throws Exception {
        mvc.perform(post("/api/connections").with(actor(user)).with(csrf()).contentType("application/json")
                        .content("{\"name\":\"X\",\"type\":\"STAIRS\"}"))
                .andExpect(status().isForbidden());
    }
}
