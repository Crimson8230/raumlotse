package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The JSON of both routing endpoints uses exactly the field names, requirements and enums of the contract file. */
class RoutingContractTest extends RoutingIntegrationSupport {

    static final Path CONTRACT = Path.of("..", "specs", "024-routing-api-neighbor-rooms", "contracts", "routing-api.yaml");

    final ObjectMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
    Map<String, Object> schemas;
    UUID a;
    UUID b;
    UUID up;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Files.exists(CONTRACT), "contract file not available in this build context");
        Map<String, Object> doc = new Yaml().load(Files.readString(CONTRACT));
        schemas = (Map<String, Object>) ((Map<String, Object>) doc.get("components")).get("schemas");

        UUID wpA = waypoint(mapEg, 0.2, 0.5);
        UUID wpB = waypoint(mapEg, 0.5, 0.5);
        UUID lift = waypoint(mapEg, 0.8, 0.5);
        UUID upLift = waypoint(mapOg, 0.8, 0.5);
        UUID upRoom = waypoint(mapOg, 0.5, 0.5);
        segment(mapEg, wpA, wpB);
        segment(mapEg, wpB, lift);
        segment(mapOg, upLift, upRoom);
        UUID connection = connection("Aufzug 1", "ELEVATOR");
        attachPoint(mapEg, connectionPoint(connection, mapEg, 0.8, 0.5), lift);
        attachPoint(mapOg, connectionPoint(connection, mapOg, 0.8, 0.5), upLift);
        a = roomEg;
        b = room("B", floorEg);
        up = roomOg;
        roomAt(mapEg, a, wpA, 0.2, 0.5);
        roomAt(mapEg, b, wpB, 0.5, 0.5);
        roomAt(mapOg, up, upRoom, 0.5, 0.5);
    }

    @SuppressWarnings("unchecked")
    void check(String schemaName, JsonNode node) {
        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        node.propertyNames().forEach(name -> assertThat(properties).as(schemaName + "." + name).containsKey(name));
        for (String name : required) {
            assertThat(node.has(name)).as(schemaName + " requires " + name).isTrue();
        }
        properties.forEach((name, definition) -> {
            JsonNode value = node.get(name);
            Map<String, Object> def = (Map<String, Object>) definition;
            if (value == null || value.isNull()) {
                return;
            }
            if (def.containsKey("enum")) {
                assertThat((List<Object>) def.get("enum")).as(schemaName + "." + name).contains(value.asText());
            }
            String ref = (String) def.get("$ref");
            if (ref != null) {
                check(ref.substring(ref.lastIndexOf('/') + 1), value);
            }
            if ("array".equals(def.get("type")) && def.get("items") instanceof Map<?, ?> items
                    && items.get("$ref") instanceof String itemRef) {
                value.forEach(item -> check(itemRef.substring(itemRef.lastIndexOf('/') + 1), item));
            }
            if ("object".equals(def.get("type")) && def.containsKey("properties")) {
                Map<String, Object> inline = (Map<String, Object>) def.get("properties");
                value.propertyNames().forEach(n -> assertThat(inline).as(schemaName + "." + name + "." + n).containsKey(n));
            }
        });
    }

    @Test
    void neighborsResponseFollowsTheContract() throws Exception {
        String body = mvc.perform(get("/api/rooms/" + a + "/neighbors").with(actor(user))).andReturn().getResponse()
                .getContentAsString();
        JsonNode node = json.readTree(body);
        assertThat(node.get("entries")).isNotEmpty();
        check("Neighbors", node);

        String unavailable = mvc.perform(get("/api/rooms/" + room("Loose", floorEg) + "/neighbors").with(actor(user)))
                .andReturn().getResponse().getContentAsString();
        check("Neighbors", json.readTree(unavailable));
    }

    @Test
    void routeResponseFollowsTheContract() throws Exception {
        String body = mvc.perform(get("/api/routes?fromRoomId=" + a + "&toRoomId=" + up).with(actor(user)))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        assertThat(node.get("steps").size()).isGreaterThan(2);
        check("Route", node);
    }
}
