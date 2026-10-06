package at.mci.igp.raumlotse;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Shared fixtures for the map feature's API integration tests: accounts, building/floor/room rows, map upload. */
@AutoConfigureMockMvc
abstract class MapIntegrationSupport extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate db;

    protected UUID admin;
    protected UUID user;
    protected UUID building;
    protected UUID floorEg;
    protected UUID floorOg;
    protected UUID roomEg;
    protected UUID roomOg;

    @BeforeEach
    void resetMapFixtures() {
        // The Postgres container is shared by all integration tests, so rows of other features must go first.
        for (String table : List.of("booking_confirmation", "reservation", "connection_point", "connection", "room_placement", "floor_map_image",
                "floor_map", "room_equipment", "room", "floor", "building", "role_assignment", "user_role_state",
                "user_account")) {
            db.update("delete from " + table);
        }
        admin = account("admin@example.test", "ADMIN");
        user = account("user@example.test", "STUDENT");
        building = UUID.randomUUID();
        floorEg = UUID.randomUUID();
        floorOg = UUID.randomUUID();
        db.update("insert into building(id,name) values (?,?)", building, "Haus A");
        db.update("insert into floor(id,building_id,name) values (?,?,?)", floorEg, building, "EG");
        db.update("insert into floor(id,building_id,name) values (?,?,?)", floorOg, building, "1. OG");
        roomEg = room("R-EG", floorEg);
        roomOg = room("R-OG", floorOg);
    }

    protected UUID account(String email, String role) {
        UUID id = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
                id, email, "Fixture", "{pbkdf2-sha256-600000-v1}test-fixture-not-a-real-password");
        db.update("insert into user_role_state(user_id) values (?)", id);
        db.update("insert into role_assignment(user_id,role_code) values (?,?)", id, role);
        return id;
    }

    protected UUID room(String name, UUID floor) {
        UUID id = UUID.randomUUID();
        db.update("insert into room(id,name,floor_id) values (?,?,?)", id, name, floor);
        return id;
    }

    protected RequestPostProcessor actor(UUID id) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(id, "Fixture"), null, List.of()));
    }

    protected static byte[] png(int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    /** Uploads a map for the floor through the API as admin and returns its id. */
    protected UUID uploadMap(UUID floor) throws Exception {
        String body = mvc.perform(multipart(HttpMethod.PUT, "/api/floors/" + floor + "/map")
                        .file(new MockMultipartFile("image", "plan.png", "image/png", png(200, 100)))
                        .with(actor(admin)).with(csrf()))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
    }
}
