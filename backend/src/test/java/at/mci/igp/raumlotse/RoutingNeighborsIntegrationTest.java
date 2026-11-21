package at.mci.igp.raumlotse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import at.mci.igp.raumlotse.service.RoutingService;

/** Neighbors endpoint: corridor y = 0.5 on the ground floor, rooms north of it (marker 0.2 above the corridor). */
class RoutingNeighborsIntegrationTest extends RoutingIntegrationSupport {

    UUID west;
    UUID self;
    UUID east;
    UUID wpWest;
    UUID wpSelf;
    UUID wpEast;

    @BeforeEach
    void corridor() throws Exception {
        wpWest = waypoint(mapEg, 0.2, 0.5);
        wpSelf = waypoint(mapEg, 0.5, 0.5);
        wpEast = waypoint(mapEg, 0.8, 0.5);
        segment(mapEg, wpWest, wpSelf);
        segment(mapEg, wpSelf, wpEast);
        west = room("West", floorEg);
        east = room("East", floorEg);
        self = roomEg;
        roomAt(mapEg, west, wpWest, 0.2, 0.5);
        roomAt(mapEg, self, wpSelf, 0.5, 0.5);
        roomAt(mapEg, east, wpEast, 0.8, 0.5);
    }

    @Test
    void returnsLeftAndRightNeighborsWithDistanceTimeAndStatus() throws Exception {
        UUID layout = UUID.randomUUID();
        db.update("insert into seating_arrangement(id,room_id,name,max_capacity) values (?,?,?,?)", layout, east, "T", 10);
        Instant now = Instant.now();
        db.update("insert into reservation(id,room_id,seating_arrangement_id,start_time,end_time,status,"
                        + "expected_attendees,created_by,reserved_for) values (?,?,?,?,?,'RESERVED',1,'owner','Owner')",
                UUID.randomUUID(), east, layout, Timestamp.from(now.minus(Duration.ofMinutes(5))),
                Timestamp.from(now.plus(Duration.ofHours(1))));

        mvc.perform(get("/api/rooms/" + self + "/neighbors").with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(self.toString()))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].direction").value("LEFT"))
                .andExpect(jsonPath("$.entries[0].kind").value("ROOM"))
                .andExpect(jsonPath("$.entries[0].name").value("West"))
                .andExpect(jsonPath("$.entries[0].roomId").value(west.toString()))
                .andExpect(jsonPath("$.entries[0].distanceMeters").value(6.0))
                .andExpect(jsonPath("$.entries[0].walkingSeconds").value(5))
                .andExpect(jsonPath("$.entries[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.entries[1].direction").value("RIGHT"))
                .andExpect(jsonPath("$.entries[1].name").value("East"))
                .andExpect(jsonPath("$.entries[1].status").value("RESERVED"));
    }

    @Test
    void listsStairsAndElevatorAsUpEntries() throws Exception {
        UUID wpStairs = waypoint(mapEg, 0.6, 0.5);
        UUID wpLift = waypoint(mapEg, 0.9, 0.5);
        segment(mapEg, wpSelf, wpStairs);
        segment(mapEg, wpStairs, wpLift);
        UUID upStairs = waypoint(mapOg, 0.6, 0.5);
        UUID upLift = waypoint(mapOg, 0.9, 0.5);
        UUID stairs = connection("Treppe A", "STAIRS");
        UUID lift = connection("Aufzug 1", "ELEVATOR");
        attachPoint(mapEg, connectionPoint(stairs, mapEg, 0.6, 0.5), wpStairs);
        attachPoint(mapOg, connectionPoint(stairs, mapOg, 0.6, 0.5), upStairs);
        attachPoint(mapEg, connectionPoint(lift, mapEg, 0.9, 0.5), wpLift);
        attachPoint(mapOg, connectionPoint(lift, mapOg, 0.9, 0.5), upLift);

        mvc.perform(get("/api/rooms/" + self + "/neighbors").with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.kind=='STAIRS')].direction").value("UP"))
                .andExpect(jsonPath("$.entries[?(@.kind=='STAIRS')].name").value("Treppe A"))
                .andExpect(jsonPath("$.entries[?(@.kind=='STAIRS')].targetFloorName").value("1. OG"))
                .andExpect(jsonPath("$.entries[?(@.kind=='STAIRS')].distanceMeters").value(2.0))
                .andExpect(jsonPath("$.entries[?(@.kind=='ELEVATOR')].direction").value("UP"))
                .andExpect(jsonPath("$.entries[?(@.kind=='ELEVATOR')].distanceMeters").value(8.0));
    }

    @Test
    void unattachedRoomIsAvailableFalseWithReason() throws Exception {
        UUID loose = room("Loose", floorEg);
        place(mapEg, loose, 0.4, 0.2);
        mvc.perform(get("/api/rooms/" + loose + "/neighbors").with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("ROOM_NOT_ATTACHED"))
                .andExpect(jsonPath("$.entries.length()").value(0));
    }

    @Test
    void mapWithoutScaleIsAvailableFalseWithReason() throws Exception {
        send(delete("/api/maps/" + mapEg + "/reference-segment"), null);
        mvc.perform(get("/api/rooms/" + self + "/neighbors").with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("SCALE_MISSING"));
    }

    @Test
    void aloneOnTheFloorHasNoNeighbors() throws Exception {
        UUID wpLonely = waypoint(mapOg, 0.5, 0.5);
        mvc.perform(get("/api/rooms/" + roomOg + "/neighbors").with(actor(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("ROOM_NOT_ATTACHED"));
        roomAt(mapOg, roomOg, wpLonely, 0.5, 0.5);
        mvc.perform(get("/api/rooms/" + roomOg + "/neighbors").with(actor(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("NO_NEIGHBORS"));
    }

    @Autowired
    RoutingService routing;

    @Test
    void neighborsForAWholeFloorMatchTheSingleRoomResults() throws Exception {
        UUID loose = room("Loose", floorEg);
        place(mapEg, loose, 0.4, 0.2);
        var all = routing.neighborsForFloor(mapEg);

        org.assertj.core.api.Assertions.assertThat(all).containsOnlyKeys(west, self, east);
        for (UUID room : all.keySet()) {
            org.assertj.core.api.Assertions.assertThat(all.get(room)).isEqualTo(routing.neighbors(room));
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> routing.neighborsForFloor(UUID.randomUUID()))
                .isInstanceOf(at.mci.igp.raumlotse.exception.NotFoundException.class);
    }

    @Test
    void unknownRoomIs404() throws Exception {
        mvc.perform(get("/api/rooms/" + UUID.randomUUID() + "/neighbors").with(actor(user)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousIs401AndRoutingHasNoWriteMethods() throws Exception {
        mvc.perform(get("/api/rooms/" + self + "/neighbors")).andExpect(status().isUnauthorized());
        for (var builder : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                post("/api/rooms/" + self + "/neighbors"), put("/api/rooms/" + self + "/neighbors"),
                delete("/api/rooms/" + self + "/neighbors")}) {
            mvc.perform(builder.with(actor(admin)).with(SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().is4xxClientError());
        }
    }
}
