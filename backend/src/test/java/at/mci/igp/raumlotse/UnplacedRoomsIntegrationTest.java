package at.mci.igp.raumlotse;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnplacedRoomsIntegrationTest extends MapIntegrationSupport {

    @Test
    void listsOnlyActiveUnplacedRoomsOfTheMapsFloor() throws Exception {
        UUID map = uploadMap(floorEg);
        UUID second = room("R-EG-2", floorEg);
        UUID deactivated = room("R-EG-OFF", floorEg);
        db.update("update room set status='DEACTIVATED' where id=?", deactivated);

        mvc.perform(get("/api/maps/" + map + "/unplaced-rooms").with(actor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("R-EG"))
                .andExpect(jsonPath("$[1].name").value("R-EG-2"));

        mvc.perform(put("/api/maps/" + map + "/placements/" + second).with(actor(admin)).with(csrf())
                        .contentType("application/json").content("{\"x\":0.1,\"y\":0.1}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/maps/" + map + "/unplaced-rooms").with(actor(admin)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("R-EG"));
    }

    @Test
    void deactivatedRoomWithPlacementStaysOnTheMapAndIsFlagged() throws Exception {
        UUID map = uploadMap(floorEg);
        mvc.perform(put("/api/maps/" + map + "/placements/" + roomEg).with(actor(admin)).with(csrf())
                        .contentType("application/json").content("{\"x\":0.3,\"y\":0.3}"))
                .andExpect(status().isCreated());
        db.update("update room set status='DEACTIVATED' where id=?", roomEg);

        mvc.perform(get("/api/maps/" + map).with(actor(user)))
                .andExpect(jsonPath("$.placements[0].room.status").value("DEACTIVATED"));
        mvc.perform(get("/api/maps/" + map + "/unplaced-rooms").with(actor(admin)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unknownMapReturns404AndAnonymousIsDenied() throws Exception {
        mvc.perform(get("/api/maps/" + UUID.randomUUID() + "/unplaced-rooms").with(actor(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/maps/" + UUID.randomUUID() + "/unplaced-rooms")).andExpect(status().isUnauthorized());
    }
}
