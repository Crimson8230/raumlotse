package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.RoomService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** V14 (feature 014): check-in columns on reservation and the DOOR device kind. */
class PresenceCheckInMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired BuildingService buildings;
    @Autowired FloorService floors;
    @Autowired RoomService rooms;
    @Autowired ReservationService reservations;

    @Test
    void reservationHasNullableCheckInColumns() {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                select column_name, data_type, is_nullable from information_schema.columns
                where table_name = 'reservation'
                  and column_name in ('check_in_method', 'checked_in_at', 'checked_in_by_user_id', 'last_presence_at')
                order by column_name
                """);

        assertThat(columns).extracting(c -> c.get("column_name"), c -> c.get("data_type"), c -> c.get("is_nullable"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("check_in_method", "text", "YES"),
                        org.assertj.core.groups.Tuple.tuple("checked_in_at", "timestamp with time zone", "YES"),
                        org.assertj.core.groups.Tuple.tuple("checked_in_by_user_id", "uuid", "YES"),
                        org.assertj.core.groups.Tuple.tuple("last_presence_at", "timestamp with time zone", "YES"));
    }

    @Test
    void checkInMethodOnlyAcceptsKnownMethodsAndNewReservationsStartEmpty() {
        UUID reservationId = createReservation();

        assertThat(jdbc.queryForMap("""
                select check_in_method, checked_in_at, checked_in_by_user_id, last_presence_at
                from reservation where id = ?""", reservationId)).containsOnlyKeys(
                "check_in_method", "checked_in_at", "checked_in_by_user_id", "last_presence_at")
                .allSatisfy((key, value) -> assertThat(value).as(key).isNull());

        jdbc.update("update reservation set check_in_method = 'NFC' where id = ?", reservationId);
        assertThatThrownBy(() -> jdbc.update("update reservation set check_in_method = 'BADGE' where id = ?", reservationId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void doorIsAnAllowedDeviceKindButUnknownKindsStayRejected() {
        UUID roomId = createRoom().getId();

        jdbc.update("insert into room_device_state(room_id, kind) values (?, 'DOOR')", roomId);
        assertThatThrownBy(() -> jdbc.update("insert into room_device_state(room_id, kind) values (?, 'WINDOW')", roomId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Room createRoom() {
        var building = buildings.create("Check-in Migration " + UUID.randomUUID());
        var floor = floors.create(building.getId(), "1");
        return rooms.create(new RoomCreateRequest("Check-in Migration Room", floor.getId(),
                List.of(new SeatingArrangementRequest("Standard", 10)), List.of()));
    }

    private UUID createReservation() {
        Room room = createRoom();
        UUID roomId = room.getId();
        UUID arrangementId = room.getSeatingArrangements().get(0).getId();
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        return TestActors.create(jdbc, reservations, roomId, new ReservationCreateRequest(
                start, start.plus(1, ChronoUnit.HOURS), arrangementId, 2, List.of(), null,
                "Migration Check")).id();
    }
}
