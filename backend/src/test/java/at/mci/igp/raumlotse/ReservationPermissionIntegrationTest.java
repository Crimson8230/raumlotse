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

/** Distinguishes reading, creating, ownership and device rights at the API boundary. */
class ReservationPermissionIntegrationTest extends MapIntegrationSupport {
    private UUID viewer;
    private UUID arrangement;

    @BeforeEach
    void reservationFixtures() {
        viewer = account("viewer@example.test", "VIEWER");
        arrangement = UUID.randomUUID();
        db.update("insert into seating_arrangement(id,room_id,name,max_capacity) values (?,?,?,?)",
                arrangement, roomEg, "Theater", 30);
    }

    private UUID book(UUID owner) throws Exception {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        String body = mvc.perform(post("/api/rooms/" + roomEg + "/reservations")
                        .with(actor(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime":"%s","endTime":"%s","seatingArrangementId":"%s",
                                 "expectedAttendees":5,"reservedFor":"Private guest","note":"private note"}
                                """.formatted(start, start.plus(1, ChronoUnit.HOURS), arrangement)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    @Test
    void viewerCanReadAnOldOwnBookingButCannotCreateOrChangeIt() throws Exception {
        UUID id = book(user);
        db.update("update reservation set created_by_user_id=? where id=?", viewer, id);
        mvc.perform(get("/api/reservations/" + id).with(actor(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownedByMe").value(true));
        mvc.perform(post("/api/rooms/" + roomEg + "/reservations").with(actor(viewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/reservations/" + id).with(actor(viewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"changed\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
        assertThat(db.queryForObject("select note from reservation where id=?", String.class, id))
                .isEqualTo("private note");
    }

    @Test
    void foreignBookingLooksLikeMissingUntilForeignRightIsGranted() throws Exception {
        UUID id = book(user);
        String foreignBody = mvc.perform(get("/api/reservations/" + id).with(actor(viewer)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String missingBody = mvc.perform(get("/api/reservations/" + UUID.randomUUID()).with(actor(viewer)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        assertThat(foreignBody).isEqualTo(missingBody);
        mvc.perform(get("/api/rooms/" + roomEg + "/reservations").with(actor(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].note").doesNotExist())
                .andExpect(jsonPath("$[0].reservedFor").doesNotExist());

        db.update("insert into role_permission(role_code,permission_code) values ('VIEWER','OTHER_RESERVATION_MANAGE')");
        try {
            mvc.perform(get("/api/reservations/" + id).with(actor(viewer)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.ownedByMe").value(false));
        } finally {
            db.update("delete from role_permission where role_code='VIEWER' and permission_code='OTHER_RESERVATION_MANAGE'");
        }
    }

    @Test
    void adminHasNoForeignOverrideAfterItsPermissionIsRevoked() throws Exception {
        UUID id = book(user);
        db.update("delete from role_permission where role_code='ADMIN' and permission_code='OTHER_RESERVATION_MANAGE'");
        try {
            mvc.perform(get("/api/reservations/" + id).with(actor(admin)))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/reservations/" + id + "/cancel").with(actor(admin)).with(csrf()))
                    .andExpect(status().isNotFound());
            assertThat(db.queryForObject("select status from reservation where id=?", String.class, id))
                    .isEqualTo("RESERVED");
        } finally {
            db.update("insert into role_permission(role_code,permission_code) values ('ADMIN','OTHER_RESERVATION_MANAGE')");
        }
    }
}
