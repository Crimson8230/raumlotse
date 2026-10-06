package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Feature 013, US4: reservations are owned by user identity, end to end through the real filter chain. */
class ReservationOwnershipIntegrationTest extends MapIntegrationSupport {

    UUID owner;
    UUID stranger;
    UUID arrangement;

    @BeforeEach
    void people() {
        // Both share the display name "Fixture" (see MapIntegrationSupport#actor): identity decides, not the name.
        owner = account("owner@example.test", "VIEWER");
        stranger = account("stranger@example.test", "STUDENT");
        arrangement = UUID.randomUUID();
        db.update("insert into seating_arrangement(id,room_id,name,max_capacity) values (?,?,?,?)",
                arrangement, roomEg, "Theater", 30);
    }

    private UUID book(UUID as, Instant start) throws Exception {
        String body = mvc.perform(post("/api/rooms/" + roomEg + "/reservations").with(actor(as)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime":"%s","endTime":"%s","seatingArrangementId":"%s",
                                 "expectedAttendees":5,"reservedFor":"Gast","note":"private note"}
                                """.formatted(start, start.plus(1, ChronoUnit.HOURS), arrangement)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownedByMe").value(true))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    @Test
    void strangerGetsNotFoundForEveryActionAndNothingChanges() throws Exception {
        UUID id = book(owner, Instant.now().plus(2, ChronoUnit.DAYS));
        String path = "/api/reservations/" + id;

        mvc.perform(get(path).with(actor(stranger))).andExpect(status().isNotFound());
        mvc.perform(patch(path).with(actor(stranger)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"hijacked\"}")).andExpect(status().isNotFound());
        for (String action : new String[] {"activate", "complete", "expire", "cancel"}) {
            mvc.perform(post(path + "/" + action).with(actor(stranger)).with(csrf()))
                    .andExpect(status().isNotFound());
        }

        assertThat(db.queryForObject("select status from reservation where id = ?", String.class, id))
                .isEqualTo("RESERVED");
        assertThat(db.queryForObject("select note from reservation where id = ?", String.class, id))
                .isEqualTo("private note");
    }

    @Test
    void ownerAndAdministratorMayManageTheReservation() throws Exception {
        UUID id = book(owner, Instant.now().plus(2, ChronoUnit.DAYS));
        UUID second = book(owner, Instant.now().plus(3, ChronoUnit.DAYS));

        mvc.perform(get("/api/reservations/" + id).with(actor(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownedByMe").value(true));
        mvc.perform(post("/api/reservations/" + id + "/cancel").with(actor(owner)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/reservations/" + second + "/cancel").with(actor(admin)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownedByMe").value(false));
    }

    @Test
    void roomScheduleShowsOccupancyOnlyToOthers() throws Exception {
        book(owner, Instant.now().plus(2, ChronoUnit.DAYS));

        mvc.perform(get("/api/rooms/" + roomEg + "/reservations").with(actor(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownedByMe").value(false))
                .andExpect(jsonPath("$[0].note").doesNotExist())
                .andExpect(jsonPath("$[0].reservedFor").doesNotExist())
                .andExpect(jsonPath("$[0].createdBy").doesNotExist());
        mvc.perform(get("/api/rooms/" + roomEg + "/reservations").with(actor(owner)))
                .andExpect(jsonPath("$[0].ownedByMe").value(true))
                .andExpect(jsonPath("$[0].note").value("private note"));
    }

    @Test
    void myUpcomingListsExactlyTheCallersOwnBookings() throws Exception {
        UUID mine = book(owner, Instant.now().plus(2, ChronoUnit.DAYS));
        book(stranger, Instant.now().plus(5, ChronoUnit.DAYS));

        mvc.perform(get("/api/reservations/my-upcoming").with(actor(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.toString()));
        mvc.perform(get("/api/reservations/my-upcoming").with(actor(admin)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
