package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

class FloorPositionMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate db;

    @BeforeEach
    void clean() {
        for (String table : new String[] {"booking_confirmation", "reservation", "connection_point", "connection",
                "room_placement", "floor_map_image", "floor_map", "room_equipment", "room", "floor", "building"}) {
            db.update("delete from " + table);
        }
    }

    @Test
    void positionColumnIsNotNullAndDefaultsToZero() {
        UUID building = UUID.randomUUID();
        UUID floor = UUID.randomUUID();
        db.update("insert into building(id,name) values (?,?)", building, "B");
        db.update("insert into floor(id,building_id,name) values (?,?,?)", floor, building, "EG");
        assertThat(db.queryForObject("select position from floor where id = ?", Integer.class, floor)).isZero();
        assertThat(db.queryForObject("""
                select is_nullable from information_schema.columns
                where table_name = 'floor' and column_name = 'position'""", String.class)).isEqualTo("NO");
    }

    @Test
    void backfillOrdersGroundFlaggedFloorFirstThenByNamePerBuilding() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        db.update("insert into building(id,name) values (?,?)", a, "A");
        db.update("insert into building(id,name) values (?,?)", b, "B");
        UUID a1 = floor(a, "1.OG", false);
        UUID aEg = floor(a, "Z-Erdgeschoss", true);
        UUID a2 = floor(a, "2.OG", false);
        UUID b1 = floor(b, "Beta", false);
        UUID b2 = floor(b, "Alpha", false);

        String sql = new String(new ClassPathResource("db/migration/V20__floor_position.sql").getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);
        String withoutComments = sql.lines().filter(line -> !line.strip().startsWith("--"))
                .collect(java.util.stream.Collectors.joining("\n"));
        for (String statement : withoutComments.split(";")) {
            if (statement.strip().toUpperCase().startsWith("UPDATE")) {
                db.execute(statement);
            }
        }

        assertThat(position(aEg)).isZero();
        assertThat(position(a1)).isEqualTo(1);
        assertThat(position(a2)).isEqualTo(2);
        assertThat(position(b2)).isZero();
        assertThat(position(b1)).isEqualTo(1);
        assertThat(db.queryForObject("select ground_floor from floor where id = ?", Boolean.class, aEg)).isTrue();
        assertThat(db.queryForObject("select ground_floor from floor where id = ?", Boolean.class, a1)).isFalse();
        assertThat(db.queryForObject("select ground_floor from floor where id = ?", Boolean.class, b2)).isTrue();
    }

    private UUID floor(UUID building, String name, boolean ground) {
        UUID id = UUID.randomUUID();
        db.update("insert into floor(id,building_id,name,ground_floor) values (?,?,?,?)", id, building, name, ground);
        return id;
    }

    private int position(UUID floor) {
        return db.queryForObject("select position from floor where id = ?", Integer.class, floor);
    }
}
