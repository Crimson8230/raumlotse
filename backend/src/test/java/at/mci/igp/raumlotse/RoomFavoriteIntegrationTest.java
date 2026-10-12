package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.service.RoomFavoriteService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Personal favorite rooms (feature 017, US2): API, limit of 10 and per-user isolation against PostgreSQL. */
class RoomFavoriteIntegrationTest extends MapIntegrationSupport {

    private static final String FAVORITES = "/api/me/favorite-rooms";

    @Autowired
    private RoomFavoriteService favoriteService;

    private void mark(UUID who, UUID roomId) throws Exception {
        mvc.perform(put(FAVORITES + "/" + roomId).with(actor(who)).with(csrf())).andExpect(status().isNoContent());
    }

    private long favoriteRows(UUID who) {
        return db.queryForObject("select count(*) from room_favorite where user_id = ?", Long.class, who);
    }

    @Test
    void markListAndUnmarkAFavorite() throws Exception {
        mark(user, roomOg);
        mark(user, roomEg);
        mark(user, roomEg);

        mvc.perform(get(FAVORITES).with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].roomId").value(roomEg.toString()))
                .andExpect(jsonPath("$[0].roomName").value("R-EG"))
                .andExpect(jsonPath("$[0].buildingName").value("Haus A"))
                .andExpect(jsonPath("$[0].floorName").value("EG"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].roomId").value(roomOg.toString()));

        mvc.perform(delete(FAVORITES + "/" + roomEg).with(actor(user)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(FAVORITES + "/" + roomEg).with(actor(user)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(FAVORITES + "/" + UUID.randomUUID()).with(actor(user)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(favoriteRows(user)).isEqualTo(1);
    }

    @Test
    void onlyFullyActiveRoomsCanBeMarked() throws Exception {
        db.update("update room set status = 'DEACTIVATED' where id = ?", roomEg);
        UUID closedFloorRoom = room("Stockwerk zu", floorOg);
        db.update("update floor set status = 'DEACTIVATED' where id = ?", floorOg);

        for (UUID roomId : List.of(roomEg, closedFloorRoom, UUID.randomUUID())) {
            mvc.perform(put(FAVORITES + "/" + roomId).with(actor(user)).with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        }
        assertThat(favoriteRows(user)).isZero();
    }

    @Test
    void deactivatedFavoritesStayListedAsUnavailable() throws Exception {
        mark(user, roomEg);
        db.update("update building set status = 'DEACTIVATED' where id = ?", building);

        mvc.perform(get(FAVORITES).with(actor(user)))
                .andExpect(jsonPath("$[0].roomId").value(roomEg.toString()))
                .andExpect(jsonPath("$[0].available").value(false));
    }

    @Test
    void favoritesArePersonal() throws Exception {
        UUID other = account("other@example.test", "STUDENT");
        mark(user, roomEg);

        mvc.perform(get(FAVORITES).with(actor(other))).andExpect(jsonPath("$", Matchers.hasSize(0)));
    }

    @Test
    void theEleventhFavoriteIsRefusedButReMarkingAnExistingOneSucceeds() throws Exception {
        List<UUID> rooms = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            rooms.add(room("Fav " + i, floorEg));
        }
        for (UUID roomId : rooms.subList(0, 10)) {
            mark(user, roomId);
        }

        mvc.perform(put(FAVORITES + "/" + rooms.get(10)).with(actor(user)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FAVORITE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.detail").value("Höchstens 10 Favoriten – entferne zuerst einen anderen."));
        mark(user, rooms.get(0));

        assertThat(favoriteRows(user)).isEqualTo(10);
    }

    @Test
    void unavailableFavoritesCountTowardTheLimit() throws Exception {
        List<UUID> rooms = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            rooms.add(room("Fav " + i, floorEg));
        }
        for (UUID roomId : rooms.subList(0, 10)) {
            mark(user, roomId);
        }
        db.update("update room set status = 'DEACTIVATED' where id = ?", rooms.get(0));

        mvc.perform(put(FAVORITES + "/" + rooms.get(10)).with(actor(user)).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentMarksNeverExceedTheLimit() throws Exception {
        for (int i = 0; i < 9; i++) {
            favoriteService.mark(user, room("Fav " + i, floorEg));
        }
        UUID tenth = room("Zehn", floorEg);
        UUID eleventh = room("Elf", floorEg);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (UUID roomId : List.of(tenth, eleventh)) {
                Callable<Boolean> attempt = () -> {
                    start.await();
                    try {
                        favoriteService.mark(user, roomId);
                        return true;
                    } catch (ConflictException limitReached) {
                        return false;
                    }
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();

            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
        assertThat(favoriteRows(user)).isEqualTo(10);
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get(FAVORITES)).andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutCsrfTokenAreRejected() throws Exception {
        mvc.perform(put(FAVORITES + "/" + roomEg).with(actor(user))).andExpect(status().isForbidden());
        mvc.perform(delete(FAVORITES + "/" + roomEg).with(actor(user))).andExpect(status().isForbidden());
        assertThat(favoriteRows(user)).isZero();
    }
}
