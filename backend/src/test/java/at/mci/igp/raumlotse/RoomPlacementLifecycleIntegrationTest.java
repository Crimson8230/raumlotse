package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** US4: placements follow the lifecycle of rooms and maps. */
class RoomPlacementLifecycleIntegrationTest extends MapIntegrationSupport {

    UUID mapWithPlacedRoom() throws Exception {
        UUID map = uploadMap(floorEg);
        mvc.perform(put("/api/maps/" + map + "/placements/" + roomEg).with(actor(admin)).with(csrf())
                        .contentType("application/json").content("{\"x\":0.4,\"y\":0.6}"))
                .andExpect(status().isCreated());
        return map;
    }

    @Test
    void renamingAPlacedRoomChangesTheMarkerLabel() throws Exception {
        UUID map = mapWithPlacedRoom();
        db.update("update room set name='Seminarraum 1' where id=?", roomEg);

        mvc.perform(get("/api/maps/" + map).with(actor(user)))
                .andExpect(jsonPath("$.placements[0].room.name").value("Seminarraum 1"));
    }

    @Test
    void deletingAPlacedRoomRemovesItsMarkerAndRow() throws Exception {
        UUID map = mapWithPlacedRoom();

        mvc.perform(delete("/api/rooms/" + roomEg).with(actor(admin)).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(get("/api/maps/" + map).with(actor(user)))
                .andExpect(jsonPath("$.placements").isEmpty())
                .andExpect(jsonPath("$.placedRoomCount").value(0));
        assertThat(db.queryForObject("select count(*) from room_placement", Long.class)).isZero();
    }

    @Test
    void aNewRoomAppearsInTheUnplacedList() throws Exception {
        UUID map = mapWithPlacedRoom();
        room("R-EG-NEU", floorEg);

        mvc.perform(get("/api/maps/" + map + "/unplaced-rooms").with(actor(user)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("R-EG-NEU"));
    }

    @Test
    void deletingTheMapMakesItsRoomsUnplacedAgain() throws Exception {
        UUID map = mapWithPlacedRoom();
        mvc.perform(delete("/api/maps/" + map).with(actor(admin)).with(csrf())).andExpect(status().isNoContent());
        assertThat(db.queryForObject("select count(*) from room_placement", Long.class)).isZero();

        UUID recreated = uploadMap(floorEg);
        mvc.perform(get("/api/maps/" + recreated + "/unplaced-rooms").with(actor(user)))
                .andExpect(jsonPath("$[0].name").value("R-EG"));
    }

    @Test
    void aFloorWithAMapCannotBeDeleted() throws Exception {
        uploadMap(floorOg);
        db.update("delete from room where id=?", roomOg);

        mvc.perform(delete("/api/floors/" + floorOg).with(actor(admin)).with(csrf())).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from floor where id=?", Long.class, floorOg)).isEqualTo(1);
    }
}
