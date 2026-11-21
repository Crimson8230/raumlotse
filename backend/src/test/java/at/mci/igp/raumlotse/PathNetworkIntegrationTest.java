package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end tests of the path network API (waypoints, segments, scale, attachments, issues, read model). */
class PathNetworkIntegrationTest extends MapIntegrationSupport {

    UUID map;      // EG, 200 x 100 px
    UUID mapOg;

    @BeforeEach
    void maps() throws Exception {
        map = uploadMap(floorEg);
        mapOg = uploadMap(floorOg);
    }

    // ---- helpers

    ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String body,
            UUID actor) throws Exception {
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(b.with(actor(actor)).with(csrf()));
    }

    static String pos(double x, double y) {
        return "{\"x\":" + x + ",\"y\":" + y + "}";
    }

    UUID waypoint(UUID mapId, double x, double y) throws Exception {
        String body = send(post("/api/maps/" + mapId + "/waypoints"), pos(x, y), admin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    UUID segment(UUID mapId, UUID a, UUID b) throws Exception {
        String body = send(post("/api/maps/" + mapId + "/segments"),
                "{\"fromWaypointId\":\"" + a + "\",\"toWaypointId\":\"" + b + "\"}", admin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    void reference(UUID mapId, double x1, double y1, double x2, double y2, double meters) throws Exception {
        send(put("/api/maps/" + mapId + "/reference-segment"),
                "{\"x1\":" + x1 + ",\"y1\":" + y1 + ",\"x2\":" + x2 + ",\"y2\":" + y2 + ",\"lengthMeters\":" + meters
                        + "}", admin).andExpect(status().is2xxSuccessful());
    }

    void place(UUID mapId, UUID room, double x, double y) throws Exception {
        send(put("/api/maps/" + mapId + "/placements/" + room), pos(x, y), admin)
                .andExpect(status().is2xxSuccessful());
    }

    ResultActions network(UUID mapId) throws Exception {
        return mvc.perform(get("/api/maps/" + mapId + "/path-network").with(actor(user)));
    }

    long count(String table) {
        return db.queryForObject("select count(*) from " + table, Long.class);
    }

    UUID connectionPoint(String name, UUID mapId, double x, double y, UUID connection) {
        UUID id = UUID.randomUUID();
        db.update("insert into connection_point(id,connection_id,map_id,x,y) values (?,?,?,?,?)",
                id, connection, mapId, x, y);
        return id;
    }

    UUID connection(String name) {
        UUID id = UUID.randomUUID();
        db.update("insert into connection(id,name,type) values (?,?,?)", id, name, "ELEVATOR");
        return id;
    }

    // ---- US1: draw

    @Test
    void drawsCorridorWithJunctionAndReloadsIdentically() throws Exception {
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.5, 0.5);
        UUID c = waypoint(map, 0.9, 0.5);
        UUID d = waypoint(map, 0.5, 0.9);
        segment(map, a, b);
        segment(map, b, c);
        segment(map, b, d);
        network(map).andExpect(status().isOk())
                .andExpect(jsonPath("$.mapId").value(map.toString()))
                .andExpect(jsonPath("$.waypoints.length()").value(4))
                .andExpect(jsonPath("$.segments.length()").value(3))
                .andExpect(jsonPath("$.scale.available").value(false))
                .andExpect(jsonPath("$.segments[0].lengthMeters").doesNotExist())
                .andExpect(jsonPath("$.issues.componentCount").value(1))
                .andExpect(jsonPath("$.waypoints[?(@.id=='" + b + "')].x").value(0.5));
    }

    @Test
    void movesWaypointAndSegmentsFollow() throws Exception {
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.5, 0.5);
        segment(map, a, b);
        send(put("/api/maps/" + map + "/waypoints/" + b), pos(0.6, 0.4), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.x").value(0.6));
        network(map).andExpect(jsonPath("$.waypoints[?(@.id=='" + b + "')].y").value(0.4))
                .andExpect(jsonPath("$.segments.length()").value(1));
    }

    @Test
    void deletingSegmentKeepsWaypointsAndDeletingWaypointRemovesItsSegments() throws Exception {
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.5, 0.5);
        UUID c = waypoint(map, 0.9, 0.5);
        UUID ab = segment(map, a, b);
        segment(map, b, c);
        send(delete("/api/maps/" + map + "/segments/" + ab), null, admin).andExpect(status().isNoContent());
        assertThat(count("path_waypoint")).isEqualTo(3);
        assertThat(count("path_segment")).isEqualTo(1);
        send(delete("/api/maps/" + map + "/waypoints/" + b), null, admin).andExpect(status().isNoContent());
        assertThat(count("path_waypoint")).isEqualTo(2);
        assertThat(count("path_segment")).isZero();
        send(delete("/api/maps/" + map + "/waypoints/" + b), null, admin).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidWaypointsAndSegments() throws Exception {
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.5, 0.5);
        UUID foreign = waypoint(mapOg, 0.5, 0.5);
        send(post("/api/maps/" + map + "/waypoints"), pos(1.2, 0.5), admin).andExpect(status().isBadRequest());
        send(post("/api/maps/" + map + "/waypoints"), pos(0.1005, 0.5), admin).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(a.toString())));
        send(post("/api/maps/" + UUID.randomUUID() + "/waypoints"), pos(0.3, 0.3), admin)
                .andExpect(status().isNotFound());
        send(post("/api/maps/" + map + "/segments"),
                "{\"fromWaypointId\":\"" + a + "\",\"toWaypointId\":\"" + a + "\"}", admin)
                .andExpect(status().isBadRequest());
        segment(map, a, b);
        send(post("/api/maps/" + map + "/segments"),
                "{\"fromWaypointId\":\"" + b + "\",\"toWaypointId\":\"" + a + "\"}", admin)
                .andExpect(status().isConflict());
        send(post("/api/maps/" + map + "/segments"),
                "{\"fromWaypointId\":\"" + a + "\",\"toWaypointId\":\"" + foreign + "\"}", admin)
                .andExpect(status().isUnprocessableContent());
        send(post("/api/maps/" + map + "/segments"),
                "{\"fromWaypointId\":\"" + a + "\",\"toWaypointId\":\"" + UUID.randomUUID() + "\"}", admin)
                .andExpect(status().isNotFound());
        send(put("/api/maps/" + map + "/waypoints/" + b), pos(0.1, 0.5), admin).andExpect(status().isConflict());
        send(put("/api/maps/" + map + "/waypoints/" + foreign), pos(0.3, 0.3), admin)
                .andExpect(status().isNotFound());
    }

    @Test
    void splitReplacesSegmentByTwoSegmentsAroundProjectedWaypoint() throws Exception {
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.9, 0.5);
        UUID ab = segment(map, a, b);
        send(post("/api/maps/" + map + "/segments/" + ab + "/split"), pos(0.5, 0.8), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waypoint.x").value(closeTo(0.5, 1e-4)))
                .andExpect(jsonPath("$.waypoint.y").value(closeTo(0.5, 1e-4)))
                .andExpect(jsonPath("$.segments.length()").value(2));
        assertThat(count("path_waypoint")).isEqualTo(3);
        assertThat(count("path_segment")).isEqualTo(2);
        network(map).andExpect(jsonPath("$.issues.componentCount").value(1));
    }

    @Test
    void splitAtAnEndWaypointIsRejectedAndKeepsTheSegment() throws Exception {
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.9, 0.5);
        UUID ab = segment(map, a, b);
        send(post("/api/maps/" + map + "/segments/" + ab + "/split"), pos(0.0, 0.5), admin)
                .andExpect(status().isConflict());
        assertThat(count("path_segment")).isEqualTo(1);
        send(post("/api/maps/" + map + "/segments/" + UUID.randomUUID() + "/split"), pos(0.5, 0.5), admin)
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminCanReadButNotWrite() throws Exception {
        waypoint(map, 0.1, 0.5);
        network(map).andExpect(status().isOk()).andExpect(jsonPath("$.waypoints.length()").value(1));
        send(post("/api/maps/" + map + "/waypoints"), pos(0.3, 0.3), user).andExpect(status().isForbidden());
        mvc.perform(get("/api/maps/" + map + "/path-network")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingTheMapRemovesItsNetwork() throws Exception {
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.5, 0.5);
        segment(map, a, b);
        reference(map, 0.1, 0.5, 0.6, 0.5, 10);
        send(delete("/api/maps/" + map), null, admin).andExpect(status().isNoContent());
        assertThat(count("path_waypoint")).isZero();
        assertThat(count("path_segment")).isZero();
        assertThat(count("map_reference_segment")).isZero();
    }

    // ---- US2: scale

    @Test
    void referenceSegmentYieldsScaleAndMeterLengths() throws Exception {
        // 200 x 100 px; reference 0.5 of width = 100 px = 10 m => 10 px/m
        send(put("/api/maps/" + map + "/reference-segment"),
                "{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5,\"lengthMeters\":10}", admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pixelsPerMeter").value(closeTo(10.0, 1e-6)));
        UUID a = waypoint(map, 0.1, 0.2);
        UUID b = waypoint(map, 0.6, 0.2);
        UUID ab = segment(map, a, b);
        network(map).andExpect(jsonPath("$.scale.available").value(true))
                .andExpect(jsonPath("$.scale.pixelsPerMeter").value(closeTo(10.0, 1e-6)))
                .andExpect(jsonPath("$.referenceSegment.lengthMeters").value(10.0))
                .andExpect(jsonPath("$.segments[?(@.id=='" + ab + "')].lengthMeters")
                        .value(org.hamcrest.Matchers.contains(closeTo(10.0, 0.5))));
        // changing the length recalculates all segment lengths
        send(put("/api/maps/" + map + "/reference-segment"),
                "{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5,\"lengthMeters\":20}", admin)
                .andExpect(status().isOk());
        network(map).andExpect(jsonPath("$.segments[?(@.id=='" + ab + "')].lengthMeters")
                .value(org.hamcrest.Matchers.contains(closeTo(20.0, 1.0))));
        // deleting makes the scale unavailable again
        send(delete("/api/maps/" + map + "/reference-segment"), null, admin).andExpect(status().isNoContent());
        network(map).andExpect(jsonPath("$.scale.available").value(false))
                .andExpect(jsonPath("$.referenceSegment").doesNotExist())
                .andExpect(jsonPath("$.issues.missingScale").value(true));
        send(delete("/api/maps/" + map + "/reference-segment"), null, admin).andExpect(status().isNotFound());
    }

    @Test
    void segmentOverReferenceStretchIsWithinFivePercentOfEnteredLength() throws Exception {
        reference(map, 0.1, 0.5, 0.9, 0.5, 40);
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.9, 0.5);
        UUID ab = segment(map, a, b);
        String body = network(map).andReturn().getResponse().getContentAsString();
        double length = ((Number) ((net.minidev.json.JSONArray) JsonPath.read(body,
                "$.segments[?(@.id=='" + ab + "')].lengthMeters")).get(0)).doubleValue();
        assertThat(length).isBetween(40 * 0.95, 40 * 1.05);
    }

    @Test
    void rejectsInvalidReferenceSegments() throws Exception {
        for (String body : new String[] {
                "{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5,\"lengthMeters\":0}",
                "{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5,\"lengthMeters\":-4}",
                "{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5}",
                "{\"x1\":0.5,\"y1\":0.5,\"x2\":0.5,\"y2\":0.5,\"lengthMeters\":4}",
                "{\"x1\":0.1,\"y1\":0.5,\"x2\":1.6,\"y2\":0.5,\"lengthMeters\":4}"}) {
            send(put("/api/maps/" + map + "/reference-segment"), body, admin).andExpect(status().isBadRequest());
        }
        send(put("/api/maps/" + UUID.randomUUID() + "/reference-segment"),
                "{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5,\"lengthMeters\":4}", admin)
                .andExpect(status().isNotFound());
        assertThat(count("map_reference_segment")).isZero();
    }

    // ---- US3: rooms

    @Test
    void attachesPlacedRoomAndKeepsItWhenPlacementMoves() throws Exception {
        place(map, roomEg, 0.3, 0.3);
        UUID wp = waypoint(map, 0.3, 0.5);
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg), "{\"waypointId\":\"" + wp + "\"}", admin)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.waypointId").value(wp.toString()));
        UUID wp2 = waypoint(map, 0.7, 0.5);
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg), "{\"waypointId\":\"" + wp2 + "\"}", admin)
                .andExpect(status().isOk());
        place(map, roomEg, 0.35, 0.35);
        network(map).andExpect(jsonPath("$.roomAttachments.length()").value(1))
                .andExpect(jsonPath("$.roomAttachments[0].waypointId").value(wp2.toString()))
                .andExpect(jsonPath("$.issues.unattachedRoomIds.length()").value(0));
        send(delete("/api/maps/" + map + "/room-attachments/" + roomEg), null, admin)
                .andExpect(status().isNoContent());
        network(map).andExpect(jsonPath("$.roomAttachments.length()").value(0))
                .andExpect(jsonPath("$.issues.unattachedRoomIds[0]").value(roomEg.toString()));
        send(delete("/api/maps/" + map + "/room-attachments/" + roomEg), null, admin)
                .andExpect(status().isNotFound());
    }

    @Test
    void roomAttachmentNeedsPlacementAndSameMapWaypoint() throws Exception {
        UUID wp = waypoint(map, 0.3, 0.5);
        UUID foreign = waypoint(mapOg, 0.3, 0.5);
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg), "{\"waypointId\":\"" + wp + "\"}", admin)
                .andExpect(status().isUnprocessableContent());
        place(map, roomEg, 0.3, 0.3);
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg), "{\"waypointId\":\"" + foreign + "\"}", admin)
                .andExpect(status().isUnprocessableContent());
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg),
                "{\"waypointId\":\"" + UUID.randomUUID() + "\"}", admin).andExpect(status().isNotFound());
        send(put("/api/maps/" + map + "/room-attachments/" + UUID.randomUUID()),
                "{\"waypointId\":\"" + wp + "\"}", admin).andExpect(status().isNotFound());
        send(put("/api/maps/" + mapOg + "/room-attachments/" + roomEg),
                "{\"waypointId\":\"" + foreign + "\"}", admin).andExpect(status().isUnprocessableContent());
    }

    @Test
    void removingPlacementRoomOrWaypointLeavesNoDanglingAttachment() throws Exception {
        place(map, roomEg, 0.3, 0.3);
        UUID wp = waypoint(map, 0.3, 0.5);
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg), "{\"waypointId\":\"" + wp + "\"}", admin)
                .andExpect(status().isCreated());
        send(delete("/api/maps/" + map + "/placements/" + roomEg), null, admin).andExpect(status().isNoContent());
        assertThat(count("room_path_attachment")).isZero();

        place(map, roomEg, 0.3, 0.3);
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg), "{\"waypointId\":\"" + wp + "\"}", admin);
        send(delete("/api/maps/" + map + "/waypoints/" + wp), null, admin).andExpect(status().isNoContent());
        assertThat(count("room_path_attachment")).isZero();
        network(map).andExpect(jsonPath("$.issues.unattachedRoomIds[0]").value(roomEg.toString()));

        UUID wp2 = waypoint(map, 0.3, 0.5);
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg), "{\"waypointId\":\"" + wp2 + "\"}", admin);
        db.update("delete from room where id=?", roomEg);
        assertThat(count("room_path_attachment")).isZero();
    }

    // ---- US4: connection points

    @Test
    void attachesConnectionPointsAndReportsFullAttachment() throws Exception {
        UUID lift = connection("Lift 1");
        UUID pointEg = connectionPoint("Lift 1", map, 0.8, 0.8, lift);
        UUID pointOg = connectionPoint("Lift 1", mapOg, 0.8, 0.8, lift);
        UUID wpEg = waypoint(map, 0.8, 0.6);
        UUID wpOg = waypoint(mapOg, 0.8, 0.6);
        send(put("/api/maps/" + map + "/connection-point-attachments/" + pointEg),
                "{\"waypointId\":\"" + wpEg + "\"}", admin).andExpect(status().isCreated());
        send(put("/api/maps/" + map + "/connection-point-attachments/" + pointEg),
                "{\"waypointId\":\"" + wpEg + "\"}", admin).andExpect(status().isOk());
        mvc.perform(get("/api/path-network").with(actor(user)))
                .andExpect(jsonPath("$.connectionGroups[0].fullyAttached").value(false));
        send(put("/api/maps/" + mapOg + "/connection-point-attachments/" + pointOg),
                "{\"waypointId\":\"" + wpOg + "\"}", admin).andExpect(status().isCreated());
        mvc.perform(get("/api/path-network").with(actor(user)))
                .andExpect(jsonPath("$.connectionGroups[0].fullyAttached").value(true))
                .andExpect(jsonPath("$.connectionGroups[0].points.length()").value(2));
        // deleting a waypoint leaves the connection not fully attached
        send(delete("/api/maps/" + mapOg + "/waypoints/" + wpOg), null, admin).andExpect(status().isNoContent());
        mvc.perform(get("/api/path-network").with(actor(user)))
                .andExpect(jsonPath("$.connectionGroups[0].fullyAttached").value(false));
        assertThat(count("connection_point_path_attachment")).isEqualTo(1);
    }

    @Test
    void connectionPointAttachmentValidatesMapsAndLifecycle() throws Exception {
        UUID lift = connection("Lift 2");
        UUID pointEg = connectionPoint("Lift 2", map, 0.8, 0.8, lift);
        UUID wpEg = waypoint(map, 0.8, 0.6);
        UUID wpOg = waypoint(mapOg, 0.8, 0.6);
        send(put("/api/maps/" + mapOg + "/connection-point-attachments/" + pointEg),
                "{\"waypointId\":\"" + wpOg + "\"}", admin).andExpect(status().isUnprocessableContent());
        send(put("/api/maps/" + map + "/connection-point-attachments/" + pointEg),
                "{\"waypointId\":\"" + wpOg + "\"}", admin).andExpect(status().isUnprocessableContent());
        send(put("/api/maps/" + map + "/connection-point-attachments/" + UUID.randomUUID()),
                "{\"waypointId\":\"" + wpEg + "\"}", admin).andExpect(status().isNotFound());
        send(put("/api/maps/" + map + "/connection-point-attachments/" + pointEg),
                "{\"waypointId\":\"" + wpEg + "\"}", admin).andExpect(status().isCreated());
        send(delete("/api/maps/" + map + "/connection-point-attachments/" + pointEg), null, admin)
                .andExpect(status().isNoContent());
        send(delete("/api/maps/" + map + "/connection-point-attachments/" + pointEg), null, admin)
                .andExpect(status().isNotFound());
        send(put("/api/maps/" + map + "/connection-point-attachments/" + pointEg),
                "{\"waypointId\":\"" + wpEg + "\"}", admin).andExpect(status().isCreated());
        send(delete("/api/connections/" + lift), null, admin).andExpect(status().isNoContent());
        assertThat(count("connection_point_path_attachment")).isZero();
    }

    // ---- US5: issues

    @Test
    void reportsIsolatedPartsUnattachedRoomsAndMissingScale() throws Exception {
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.5, 0.5);
        UUID c = waypoint(map, 0.9, 0.5);
        UUID island1 = waypoint(map, 0.2, 0.9);
        UUID island2 = waypoint(map, 0.4, 0.9);
        segment(map, a, b);
        segment(map, b, c);
        segment(map, island1, island2);
        place(map, roomEg, 0.3, 0.3);
        UUID roomEg2 = room("R-EG-2", floorEg);
        place(map, roomEg2, 0.6, 0.3);
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg2), "{\"waypointId\":\"" + island1 + "\"}", admin)
                .andExpect(status().isCreated());
        network(map).andExpect(jsonPath("$.issues.missingScale").value(true))
                .andExpect(jsonPath("$.issues.componentCount").value(2))
                .andExpect(jsonPath("$.issues.components[0].length()").value(3))
                .andExpect(jsonPath("$.issues.unattachedRoomIds[0]").value(roomEg.toString()))
                .andExpect(jsonPath("$.issues.attachmentsOutsideMainComponent[0].kind").value("ROOM"))
                .andExpect(jsonPath("$.issues.attachmentsOutsideMainComponent[0].id").value(roomEg2.toString()));
        // fix everything
        send(post("/api/maps/" + map + "/segments"),
                "{\"fromWaypointId\":\"" + c + "\",\"toWaypointId\":\"" + island2 + "\"}", admin)
                .andExpect(status().isCreated());
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg), "{\"waypointId\":\"" + a + "\"}", admin);
        reference(map, 0.1, 0.5, 0.6, 0.5, 10);
        network(map).andExpect(jsonPath("$.issues.missingScale").value(false))
                .andExpect(jsonPath("$.issues.componentCount").value(1))
                .andExpect(jsonPath("$.issues.unattachedRoomIds.length()").value(0))
                .andExpect(jsonPath("$.issues.attachmentsOutsideMainComponent.length()").value(0));
    }

    // ---- US6: read model

    @Test
    void networkWideReadReturnsAllMapsAndGroups() throws Exception {
        UUID a = waypoint(map, 0.1, 0.5);
        UUID b = waypoint(map, 0.5, 0.5);
        segment(map, a, b);
        reference(map, 0.1, 0.5, 0.6, 0.5, 10);
        waypoint(mapOg, 0.2, 0.2);
        place(map, roomEg, 0.3, 0.3);
        send(put("/api/maps/" + map + "/room-attachments/" + roomEg), "{\"waypointId\":\"" + a + "\"}", admin);
        UUID lift = connection("Lift 3");
        connectionPoint("Lift 3", map, 0.9, 0.9, lift);
        connectionPoint("Lift 3", mapOg, 0.9, 0.9, lift);
        mvc.perform(get("/api/path-network").with(actor(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.maps.length()").value(2))
                .andExpect(jsonPath("$.maps[?(@.mapId=='" + map + "')].segments.length()")
                        .value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.maps[?(@.mapId=='" + map + "')].scale.available")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.maps[?(@.mapId=='" + mapOg + "')].scale.available")
                        .value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.maps[?(@.mapId=='" + map + "')].roomAttachments.length()")
                        .value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$.connectionGroups.length()").value(1))
                .andExpect(jsonPath("$.connectionGroups[0].name").value("Lift 3"))
                .andExpect(jsonPath("$.connectionGroups[0].type").value("ELEVATOR"))
                .andExpect(jsonPath("$.connectionGroups[0].points.length()").value(2))
                .andExpect(jsonPath("$.connectionGroups[0].points[0].waypointId").doesNotExist());
        mvc.perform(get("/api/path-network")).andExpect(status().isUnauthorized());
    }
}
