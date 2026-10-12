package at.mci.igp.raumlotse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.AbstractIntegrationTest;
import at.mci.igp.raumlotse.dto.RoomBookingCount;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Popularity counts for the room search ranking (feature 017, FR-005) against PostgreSQL. */
class ReservationPopularityRepositoryTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final Instant SINCE = NOW.minus(Duration.ofDays(90));

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReservationRepository reservations;

    private UUID room;
    private UUID otherRoom;
    private UUID silentRoom;

    private UUID room(UUID floor, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into room(id,name,floor_id) values (?,?,?)", id, name, floor);
        jdbc.update("insert into seating_arrangement(id,room_id,name,max_capacity) values (?,?,?,?)",
                UUID.randomUUID(), id, "Theater", 20);
        return id;
    }

    private void booking(UUID roomId, Instant start, String status) {
        UUID layout = jdbc.queryForObject("select id from seating_arrangement where room_id = ?", UUID.class, roomId);
        jdbc.update("insert into reservation(id,room_id,seating_arrangement_id,start_time,end_time,status,"
                        + "expected_attendees,created_by,reserved_for) values (?,?,?,?,?,?,1,'owner','Owner')",
                UUID.randomUUID(), roomId, layout, Timestamp.from(start), Timestamp.from(start.plusSeconds(3600)),
                status);
    }

    @BeforeEach
    void seed() {
        UUID building = UUID.randomUUID();
        UUID floor = UUID.randomUUID();
        jdbc.update("insert into building(id,name) values (?,?)", building, "Popularität " + building);
        jdbc.update("insert into floor(id,building_id,name) values (?,?,?)", floor, building, "EG");
        room = room(floor, "Beliebt");
        otherRoom = room(floor, "Selten");
        silentRoom = room(floor, "Nie gebucht");
    }

    private Map<UUID, Long> counts() {
        return reservations.countRecentByRoom(SINCE, NOW).stream()
                .collect(Collectors.toMap(RoomBookingCount::roomId, RoomBookingCount::count));
    }

    @Test
    void countsReservedActiveAndCompletedBookingsStartingInTheWindow() {
        booking(room, NOW.minus(Duration.ofDays(1)), "COMPLETED");
        booking(room, NOW.minus(Duration.ofDays(2)), "ACTIVE");
        booking(room, NOW.minus(Duration.ofDays(3)), "RESERVED");
        booking(room, NOW.minus(Duration.ofDays(4)), "CANCELLED");
        booking(room, NOW.minus(Duration.ofDays(5)), "EXPIRED");
        booking(otherRoom, NOW.minus(Duration.ofDays(10)), "COMPLETED");

        Map<UUID, Long> counts = counts();

        assertThat(counts).containsEntry(room, 3L).containsEntry(otherRoom, 1L).doesNotContainKey(silentRoom);
    }

    @Test
    void windowStartsExactlyNinetyDaysAgoAndEndsNow() {
        booking(room, SINCE, "COMPLETED");
        booking(room, SINCE.minusSeconds(1), "COMPLETED");
        booking(room, NOW, "RESERVED");
        booking(room, NOW.plusSeconds(1), "RESERVED");

        assertThat(counts()).containsEntry(room, 2L);
    }
}
