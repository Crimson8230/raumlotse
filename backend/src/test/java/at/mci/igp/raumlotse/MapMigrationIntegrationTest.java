package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class MapMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate db;

    UUID floor;
    UUID otherFloor;
    UUID room;
    UUID map;

    @BeforeEach
    void fixtures() {
        db.update("delete from booking_confirmation");
        db.update("delete from reservation");
        db.update("delete from connection_point");
        db.update("delete from connection");
        db.update("delete from room_placement");
        db.update("delete from floor_map_image");
        db.update("delete from floor_map");
        db.update("delete from room_equipment");
        db.update("delete from room");
        db.update("delete from floor");
        db.update("delete from building");
        UUID building = UUID.randomUUID();
        floor = UUID.randomUUID();
        otherFloor = UUID.randomUUID();
        room = UUID.randomUUID();
        db.update("insert into building(id,name) values (?,?)", building, "B");
        db.update("insert into floor(id,building_id,name) values (?,?,?)", floor, building, "EG");
        db.update("insert into floor(id,building_id,name) values (?,?,?)", otherFloor, building, "1.OG");
        db.update("insert into room(id,name,floor_id) values (?,?,?)", room, "R1", floor);
        map = insertMap(floor, "image/png");
    }

    UUID insertMap(UUID floorId, String contentType) {
        UUID id = UUID.randomUUID();
        db.update("insert into floor_map(id,floor_id,content_type,width_px,height_px) values (?,?,?,?,?)",
                id, floorId, contentType, 100, 50);
        db.update("insert into floor_map_image(map_id,image) values (?,?)", id, new byte[] {1});
        return id;
    }

    @Test
    void oneMapPerFloor() {
        assertThatThrownBy(() -> insertMap(floor, "image/png")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyPngAndJpegContentTypes() {
        assertThatThrownBy(() -> insertMap(otherFloor, "image/svg+xml"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertMap(otherFloor, "image/jpeg");
    }

    @Test
    void imageDimensionsMustBePositive() {
        assertThatThrownBy(() -> db.update(
                "insert into floor_map(id,floor_id,content_type,width_px,height_px) values (?,?,?,?,?)",
                UUID.randomUUID(), otherFloor, "image/png", 0, 10))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void placementCoordinatesStayWithinUnitSquareAndRoomHasOnePlacement() {
        db.update("insert into room_placement(room_id,map_id,x,y) values (?,?,?,?)", room, map, 0.0, 1.0);
        assertThatThrownBy(() -> db.update("insert into room_placement(room_id,map_id,x,y) values (?,?,?,?)",
                room, map, 0.5, 0.5)).isInstanceOf(DataIntegrityViolationException.class);
        db.update("delete from room_placement");
        assertThatThrownBy(() -> db.update("insert into room_placement(room_id,map_id,x,y) values (?,?,?,?)",
                room, map, 1.2, 0.5)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into room_placement(room_id,map_id,x,y) values (?,?,?,?)",
                room, map, 0.5, -0.1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void placementIsRemovedWithRoomAndWithMap() {
        db.update("insert into room_placement(room_id,map_id,x,y) values (?,?,?,?)", room, map, 0.1, 0.2);
        db.update("delete from room where id=?", room);
        assertThat(db.queryForObject("select count(*) from room_placement", Long.class)).isZero();
        UUID room2 = UUID.randomUUID();
        db.update("insert into room(id,name,floor_id) values (?,?,?)", room2, "R2", floor);
        db.update("insert into room_placement(room_id,map_id,x,y) values (?,?,?,?)", room2, map, 0.1, 0.2);
        db.update("delete from floor_map where id=?", map);
        assertThat(db.queryForObject("select count(*) from room_placement", Long.class)).isZero();
    }

    @Test
    void connectionRulesAreEnforcedAndPointsCascade() {
        UUID connection = UUID.randomUUID();
        db.update("insert into connection(id,name,type) values (?,?,?)", connection, "Aufzug A", "ELEVATOR");
        assertThatThrownBy(() -> db.update("insert into connection(id,name,type) values (?,?,?)",
                UUID.randomUUID(), "aufzug a", "STAIRS")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into connection(id,name,type) values (?,?,?)",
                UUID.randomUUID(), "Rampe", "RAMP")).isInstanceOf(DataIntegrityViolationException.class);

        db.update("insert into connection_point(id,connection_id,map_id,x,y) values (?,?,?,?,?)",
                UUID.randomUUID(), connection, map, 0.5, 0.5);
        assertThatThrownBy(() -> db.update("insert into connection_point(id,connection_id,map_id,x,y) values (?,?,?,?,?)",
                UUID.randomUUID(), connection, map, 0.1, 0.1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into connection_point(id,connection_id,map_id,x,y) values (?,?,?,?,?)",
                UUID.randomUUID(), connection, insertMap(otherFloor, "image/png"), 1.5, 0.1))
                .isInstanceOf(DataIntegrityViolationException.class);

        db.update("delete from floor_map where id=?", map);
        assertThat(db.queryForObject("select count(*) from connection_point", Long.class)).isZero();
        assertThat(db.queryForObject("select count(*) from connection", Long.class)).isEqualTo(1);
    }
}
