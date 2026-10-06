package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapPerformanceIntegrationTest extends MapIntegrationSupport {

    @Test
    void mapDetailWith200PlacementsLoadsWithinOneSecond() throws Exception {
        UUID map = uploadMap(floorEg);
        for (int i = 0; i < 200; i++) {
            UUID room = room("Raum " + i, floorEg);
            db.update("insert into room_placement(room_id,map_id,x,y) values (?,?,?,?)", room, map,
                    (i % 20) / 20.0, (i / 20) / 10.0);
        }
        mvc.perform(get("/api/maps/" + map).with(actor(user))).andExpect(status().isOk()); // warm-up

        long start = System.nanoTime();
        mvc.perform(get("/api/maps/" + map).with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(200));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void imageRevalidationReturns304AndReplacementInvalidatesTheEtag() throws Exception {
        UUID map = uploadMap(floorEg);
        String etag = mvc.perform(get("/api/maps/" + map + "/image").with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getHeader("ETag");

        mvc.perform(get("/api/maps/" + map + "/image").with(actor(user)).header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        uploadMap(floorEg); // replaces the image of the same floor's map
        mvc.perform(get("/api/maps/" + map + "/image").with(actor(user)).header("If-None-Match", etag))
                .andExpect(status().isOk());
    }
}
