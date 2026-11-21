package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.service.PathGeometry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SC-004: neighbors and a route across five floors of 500 waypoints each answer in under one second. */
class RoutingPerformanceIntegrationTest extends MapIntegrationSupport {

    @Test
    void neighborsAndCrossFloorRouteAreFast() throws Exception {
        List<UUID> floors = new ArrayList<>(List.of(floorEg, floorOg));
        for (String name : List.of("2. OG", "3. OG", "4. OG")) {
            UUID f = UUID.randomUUID();
            db.update("insert into floor(id,building_id,name) values (?,?,?)", f, building, name);
            floors.add(f);
        }
        UUID lift = UUID.randomUUID();
        db.update("insert into connection(id,name,type) values (?,?,?)", lift, "Aufzug", "ELEVATOR");
        List<UUID> rooms = new ArrayList<>();
        List<UUID> startRooms = new ArrayList<>();
        for (int level = 0; level < floors.size(); level++) {
            db.update("update floor set position = ?, ground_floor = ? where id = ?", level, level == 0, floors.get(level));
            UUID map = uploadMap(floors.get(level));
            db.update("insert into map_reference_segment(map_id,x1,y1,x2,y2,length_m) values (?,?,?,?,?,?)",
                    map, 0.0, 0.5, 1.0, 0.5, 20);
            List<UUID> wps = grid(map);
            UUID start = room("Start " + level, floors.get(level));
            UUID end = room("End " + level, floors.get(level));
            UUID near = room("Near " + level, floors.get(level));
            attach(start, map, wps.get(0));
            attach(near, map, wps.get(1));
            attach(end, map, wps.get(499));
            rooms.add(end);
            startRooms.add(start);
            UUID point = UUID.randomUUID();
            db.update("insert into connection_point(id,connection_id,map_id,x,y) values (?,?,?,?,?)", point, lift, map, 0.3, 0.3);
            db.update("insert into connection_point_path_attachment(connection_point_id,map_id,waypoint_id) values (?,?,?)",
                    point, map, wps.get(0));
        }

        UUID from = startRooms.get(0);
        UUID to = rooms.get(4);
        mvc.perform(get("/api/rooms/" + from + "/neighbors").with(actor(user))).andExpect(status().isOk()); // warm-up
        mvc.perform(get("/api/routes?fromRoomId=" + from + "&toRoomId=" + to).with(actor(user))).andExpect(status().isOk());

        long start = System.nanoTime();
        mvc.perform(get("/api/rooms/" + from + "/neighbors").with(actor(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        Duration neighbors = Duration.ofNanos(System.nanoTime() - start);

        start = System.nanoTime();
        mvc.perform(get("/api/routes?fromRoomId=" + from + "&toRoomId=" + to).with(actor(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.distanceMeters").isNumber());
        Duration route = Duration.ofNanos(System.nanoTime() - start);

        assertThat(neighbors).isLessThan(Duration.ofSeconds(1));
        assertThat(route).isLessThan(Duration.ofSeconds(1));
    }

    private List<UUID> grid(UUID map) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            db.update("insert into path_waypoint(id,map_id,x,y) values (?,?,?,?)", id, map,
                    (i % 25) / 25.0 + 0.001, (i / 25) / 20.0 + 0.001);
        }
        for (int i = 0; i < 500; i++) {
            for (int step : new int[] {1, 25}) {
                int j = i + step;
                if (j < 500 && (step == 25 || (i % 25) != 24)) {
                    UUID[] pair = PathGeometry.orderedPair(ids.get(i), ids.get(j));
                    db.update("insert into path_segment(id,map_id,a_id,b_id) values (?,?,?,?)", UUID.randomUUID(),
                            map, pair[0], pair[1]);
                }
            }
        }
        return ids;
    }

    private void attach(UUID room, UUID map, UUID waypoint) {
        db.update("insert into room_placement(room_id,map_id,x,y) values (?,?,?,?)", room, map, 0.5, 0.95);
        db.update("insert into room_path_attachment(room_id,map_id,waypoint_id) values (?,?,?)", room, map, waypoint);
    }
}
