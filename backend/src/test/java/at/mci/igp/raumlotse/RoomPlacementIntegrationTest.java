package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class RoomPlacementIntegrationTest extends MapIntegrationSupport {

    String place(UUID map, UUID room, double x, double y) {
        return "/api/maps/" + map + "/placements/" + room;
    }

    @Test
    void placementSurvivesReloadAndCanBeMovedAndRemoved() throws Exception {
        UUID map = uploadMap(floorEg);
        mvc.perform(put(place(map, roomEg, 0, 0)).with(actor(admin)).with(csrf())
                        .contentType("application/json").content("{\"x\":0.25,\"y\":0.75}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/maps/" + map).with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placedRoomCount").value(1))
                .andExpect(jsonPath("$.placements[0].room.name").value("R-EG"))
                .andExpect(jsonPath("$.placements[0].x").value(0.25))
                .andExpect(jsonPath("$.placements[0].y").value(0.75));

        mvc.perform(put(place(map, roomEg, 0, 0)).with(actor(admin)).with(csrf())
                        .contentType("application/json").content("{\"x\":0.5,\"y\":0.5}"))
                .andExpect(status().isOk());
        assertThat(db.queryForObject("select count(*) from room_placement", Long.class)).isEqualTo(1);
        mvc.perform(get("/api/maps/" + map).with(actor(user)))
                .andExpect(jsonPath("$.placements[0].x").value(0.5));

        mvc.perform(delete(place(map, roomEg, 0, 0)).with(actor(admin)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/maps/" + map).with(actor(user))).andExpect(jsonPath("$.placements").isEmpty());
        mvc.perform(delete(place(map, roomEg, 0, 0)).with(actor(admin)).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void roomOfOtherFloorAndBadCoordinatesAreRejected() throws Exception {
        UUID map = uploadMap(floorEg);
        mvc.perform(put(place(map, roomOg, 0, 0)).with(actor(admin)).with(csrf())
                        .contentType("application/json").content("{\"x\":0.5,\"y\":0.5}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ROOM_FLOOR_MISMATCH"));
        mvc.perform(put(place(map, roomEg, 0, 0)).with(actor(admin)).with(csrf())
                        .contentType("application/json").content("{\"x\":1.2,\"y\":0.5}"))
                .andExpect(status().isBadRequest());
        assertThat(db.queryForObject("select count(*) from room_placement", Long.class)).isZero();
    }

    @Test
    void nonAdminCannotPlace() throws Exception {
        UUID map = uploadMap(floorEg);
        mvc.perform(put(place(map, roomEg, 0, 0)).with(actor(user)).with(csrf())
                        .contentType("application/json").content("{\"x\":0.5,\"y\":0.5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentFirstPlacementsOfTheSameRoomLeaveExactlyOneRowAndNoError() throws Exception {
        UUID map = uploadMap(floorEg);
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double x = (i + 1) / 10.0;
                calls.add(() -> mvc.perform(put(place(map, roomEg, 0, 0)).with(actor(admin)).with(csrf())
                                .contentType("application/json").content("{\"x\":" + x + ",\"y\":0.5}"))
                        .andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isIn(200, 201);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(db.queryForObject("select count(*) from room_placement", Long.class)).isEqualTo(1);
    }
}
