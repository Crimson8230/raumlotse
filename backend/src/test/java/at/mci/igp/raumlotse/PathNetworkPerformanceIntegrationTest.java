package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-006: 500 waypoints and ~600 segments load in under 2 seconds from both read endpoints. */
class PathNetworkPerformanceIntegrationTest extends MapIntegrationSupport {

    @Test
    void largeNetworkLoadsWithinTwoSeconds() throws Exception {
        UUID map = uploadMap(floorEg);
        db.update("insert into map_reference_segment(map_id,x1,y1,x2,y2,length_m) values (?,?,?,?,?,?)",
                map, 0.1, 0.1, 0.6, 0.1, 25);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            db.update("insert into path_waypoint(id,map_id,x,y) values (?,?,?,?)", id, map,
                    (i % 25) / 25.0 + 0.001, (i / 25) / 20.0 + 0.001);
        }
        int segments = 0;
        for (int i = 0; i < 500 && segments < 600; i++) {
            for (int step : new int[] {1, 25}) {
                int j = i + step;
                if (j < 500 && segments < 600 && (step == 25 || (i % 25) != 24)) {
                    UUID[] pair = at.mci.igp.raumlotse.service.PathGeometry.orderedPair(ids.get(i), ids.get(j));
                    db.update("insert into path_segment(id,map_id,a_id,b_id) values (?,?,?,?)", UUID.randomUUID(),
                            map, pair[0], pair[1]);
                    segments++;
                }
            }
        }
        assertThat(segments).isGreaterThanOrEqualTo(600);
        mvc.perform(get("/api/maps/" + map + "/path-network").with(actor(user))).andExpect(status().isOk()); // warm-up
        mvc.perform(get("/api/path-network").with(actor(user))).andExpect(status().isOk());

        long start = System.nanoTime();
        mvc.perform(get("/api/maps/" + map + "/path-network").with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waypoints.length()").value(500))
                .andExpect(jsonPath("$.segments.length()").value(600));
        Duration single = Duration.ofNanos(System.nanoTime() - start);

        start = System.nanoTime();
        mvc.perform(get("/api/path-network").with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maps[0].waypoints.length()").value(500));
        Duration wide = Duration.ofNanos(System.nanoTime() - start);

        assertThat(single).isLessThan(Duration.ofSeconds(2));
        assertThat(wide).isLessThan(Duration.ofSeconds(2));
    }
}
