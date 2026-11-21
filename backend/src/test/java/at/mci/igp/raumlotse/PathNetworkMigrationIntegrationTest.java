package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class PathNetworkMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate db;

    UUID floor;
    UUID otherFloor;
    UUID room;
    UUID map;
    UUID otherMap;
    UUID connection;
    UUID point;

    @BeforeEach
    void fixtures() {
        for (String table : new String[] {"booking_confirmation", "reservation", "connection_point", "connection",
                "room_placement", "floor_map_image", "floor_map", "room_equipment", "room", "floor", "building"}) {
            db.update("delete from " + table);
        }
        UUID building = UUID.randomUUID();
        floor = UUID.randomUUID();
        otherFloor = UUID.randomUUID();
        room = UUID.randomUUID();
        db.update("insert into building(id,name) values (?,?)", building, "B");
        db.update("insert into floor(id,building_id,name) values (?,?,?)", floor, building, "EG");
        db.update("insert into floor(id,building_id,name) values (?,?,?)", otherFloor, building, "1.OG");
        db.update("insert into room(id,name,floor_id) values (?,?,?)", room, "R1", floor);
        map = insertMap(floor);
        otherMap = insertMap(otherFloor);
        db.update("insert into room_placement(room_id,map_id,x,y) values (?,?,?,?)", room, map, 0.1, 0.1);
        connection = UUID.randomUUID();
        point = UUID.randomUUID();
        db.update("insert into connection(id,name,type) values (?,?,?)", connection, "Lift", "ELEVATOR");
        db.update("insert into connection_point(id,connection_id,map_id,x,y) values (?,?,?,?,?)",
                point, connection, map, 0.5, 0.5);
    }

    UUID insertMap(UUID floorId) {
        UUID id = UUID.randomUUID();
        db.update("insert into floor_map(id,floor_id,content_type,width_px,height_px) values (?,?,?,?,?)",
                id, floorId, "image/png", 200, 100);
        db.update("insert into floor_map_image(map_id,image) values (?,?)", id, new byte[] {1});
        return id;
    }

    UUID waypoint(UUID mapId, double x, double y) {
        UUID id = UUID.randomUUID();
        db.update("insert into path_waypoint(id,map_id,x,y) values (?,?,?,?)", id, mapId, x, y);
        return id;
    }

    void segment(UUID mapId, UUID a, UUID b) {
        db.update("insert into path_segment(id,map_id,a_id,b_id) values (?,?,?,?)", UUID.randomUUID(), mapId, a, b);
    }

    static UUID[] ordered(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) < 0 ? new UUID[] {a, b} : new UUID[] {b, a};
    }

    long count(String table) {
        return db.queryForObject("select count(*) from " + table, Long.class);
    }

    @Test
    void waypointCoordinatesMustLieWithinZeroAndOne() {
        assertThatThrownBy(() -> waypoint(map, 1.2, 0.5)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> waypoint(map, 0.5, -0.1)).isInstanceOf(DataIntegrityViolationException.class);
        waypoint(map, 0, 1);
    }

    @Test
    void segmentRejectsSelfLoop() {
        UUID a = waypoint(map, 0.1, 0.1);
        assertThatThrownBy(() -> segment(map, a, a)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void segmentRejectsUnnormalizedPairAndDuplicate() {
        UUID a = waypoint(map, 0.1, 0.1);
        UUID b = waypoint(map, 0.2, 0.2);
        UUID[] pair = ordered(a, b);
        assertThatThrownBy(() -> segment(map, pair[1], pair[0])).isInstanceOf(DataIntegrityViolationException.class);
        segment(map, pair[0], pair[1]);
        assertThatThrownBy(() -> segment(map, pair[0], pair[1])).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void segmentRejectsWaypointOfAnotherMap() {
        UUID a = waypoint(map, 0.1, 0.1);
        UUID b = waypoint(otherMap, 0.2, 0.2);
        UUID[] pair = ordered(a, b);
        assertThatThrownBy(() -> segment(map, pair[0], pair[1])).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingWaypointCascadesSegmentsAndAttachments() {
        UUID a = waypoint(map, 0.1, 0.1);
        UUID b = waypoint(map, 0.2, 0.2);
        UUID[] pair = ordered(a, b);
        segment(map, pair[0], pair[1]);
        db.update("insert into room_path_attachment(room_id,map_id,waypoint_id) values (?,?,?)", room, map, a);
        db.update("insert into connection_point_path_attachment(connection_point_id,map_id,waypoint_id) values (?,?,?)",
                point, map, a);
        db.update("delete from path_waypoint where id=?", a);
        assertThat(count("path_segment")).isZero();
        assertThat(count("room_path_attachment")).isZero();
        assertThat(count("connection_point_path_attachment")).isZero();
        assertThat(count("path_waypoint")).isEqualTo(1);
    }

    @Test
    void roomAttachmentNeedsPlacementOnSameMapAndWaypointOnSameMap() {
        UUID foreign = waypoint(otherMap, 0.3, 0.3);
        assertThatThrownBy(() -> db.update(
                "insert into room_path_attachment(room_id,map_id,waypoint_id) values (?,?,?)", room, map, foreign))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID wp = waypoint(map, 0.3, 0.3);
        UUID unplacedRoom = UUID.randomUUID();
        db.update("insert into room(id,name,floor_id) values (?,?,?)", unplacedRoom, "R2", floor);
        assertThatThrownBy(() -> db.update(
                "insert into room_path_attachment(room_id,map_id,waypoint_id) values (?,?,?)", unplacedRoom, map, wp))
                .isInstanceOf(DataIntegrityViolationException.class);
        db.update("insert into room_path_attachment(room_id,map_id,waypoint_id) values (?,?,?)", room, map, wp);
        assertThatThrownBy(() -> db.update(
                "insert into room_path_attachment(room_id,map_id,waypoint_id) values (?,?,?)", room, map, wp))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingPlacementRemovesRoomAttachment() {
        UUID wp = waypoint(map, 0.3, 0.3);
        db.update("insert into room_path_attachment(room_id,map_id,waypoint_id) values (?,?,?)", room, map, wp);
        db.update("delete from room_placement where room_id=?", room);
        assertThat(count("room_path_attachment")).isZero();
        assertThat(count("path_waypoint")).isEqualTo(1);
    }

    @Test
    void deletingRoomRemovesRoomAttachment() {
        UUID wp = waypoint(map, 0.3, 0.3);
        db.update("insert into room_path_attachment(room_id,map_id,waypoint_id) values (?,?,?)", room, map, wp);
        db.update("delete from room where id=?", room);
        assertThat(count("room_path_attachment")).isZero();
    }

    @Test
    void connectionPointAttachmentIsSameMapAndSingle() {
        UUID foreign = waypoint(otherMap, 0.3, 0.3);
        assertThatThrownBy(() -> db.update(
                "insert into connection_point_path_attachment(connection_point_id,map_id,waypoint_id) values (?,?,?)",
                point, map, foreign)).isInstanceOf(DataIntegrityViolationException.class);
        UUID wp = waypoint(map, 0.3, 0.3);
        db.update("insert into connection_point_path_attachment(connection_point_id,map_id,waypoint_id) values (?,?,?)",
                point, map, wp);
        db.update("delete from connection_point where id=?", point);
        assertThat(count("connection_point_path_attachment")).isZero();
    }

    @Test
    void deletingMapRemovesNetworkReferenceAndAttachments() {
        UUID a = waypoint(map, 0.1, 0.1);
        UUID b = waypoint(map, 0.2, 0.2);
        UUID[] pair = ordered(a, b);
        segment(map, pair[0], pair[1]);
        db.update("insert into room_path_attachment(room_id,map_id,waypoint_id) values (?,?,?)", room, map, a);
        db.update("insert into map_reference_segment(map_id,x1,y1,x2,y2,length_m) values (?,?,?,?,?,?)",
                map, 0.1, 0.1, 0.9, 0.1, 10);
        db.update("delete from floor_map where id=?", map);
        for (String table : new String[] {"path_waypoint", "path_segment", "room_path_attachment",
                "map_reference_segment"}) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    @Test
    void referenceSegmentLengthMustBePositiveAndBounded() {
        assertThatThrownBy(() -> db.update(
                "insert into map_reference_segment(map_id,x1,y1,x2,y2,length_m) values (?,?,?,?,?,?)",
                map, 0.1, 0.1, 0.9, 0.1, 0)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update(
                "insert into map_reference_segment(map_id,x1,y1,x2,y2,length_m) values (?,?,?,?,?,?)",
                map, 0.1, 0.1, 0.9, 0.1, 100001)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update(
                "insert into map_reference_segment(map_id,x1,y1,x2,y2,length_m) values (?,?,?,?,?,?)",
                map, 0.1, 0.1, 1.9, 0.1, 5)).isInstanceOf(DataIntegrityViolationException.class);
        db.update("insert into map_reference_segment(map_id,x1,y1,x2,y2,length_m) values (?,?,?,?,?,?)",
                map, 0.1, 0.1, 0.9, 0.1, 100000);
        assertThatThrownBy(() -> db.update(
                "insert into map_reference_segment(map_id,x1,y1,x2,y2,length_m) values (?,?,?,?,?,?)",
                map, 0.1, 0.1, 0.9, 0.1, 5)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permissionCodesAreUnchanged() {
        Long count = db.queryForObject(
                "select count(*) from information_schema.check_constraints where constraint_name like '%permission%' "
                        + "and check_clause like '%PATH_NETWORK%'", Long.class);
        assertThat(count).isZero();
    }
}
