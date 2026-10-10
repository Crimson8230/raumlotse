package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.TestActors;
import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.AbstractIntegrationTest;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.RoomService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

/** {@code findOccupiedRoomIds} applies the reservation rule: RESERVED/ACTIVE block [start, end). */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationOccupancyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private FloorService floorService;

    private Room room;
    private ReservationResponse reservation;
    /** Tomorrow 10:00 UTC; the reservation runs 10:00-12:00. */
    private Instant ten;

    @BeforeEach
    void seed() {
        var building = buildingService.create("Belegung " + UUID.randomUUID());
        var floor = floorService.create(building.getId(), "EG");
        room = roomService.create(new RoomCreateRequest("Raum", floor.getId(),
                List.of(new SeatingArrangementRequest("Theater", 20)), List.of()));
        ten = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(10, ChronoUnit.HOURS);
        reservation = TestActors.create(jdbc, reservationService, room.getId(), new ReservationCreateRequest(
                ten, at(12, 0), room.getSeatingArrangements().get(0).getId(), 10, List.of(), null, "Test"));
    }

    private Instant at(int hour, int minute) {
        return ten.plus(hour - 10, ChronoUnit.HOURS).plus(minute, ChronoUnit.MINUTES);
    }

    private boolean occupied(Instant from, Instant to) {
        return reservationRepository.findOccupiedRoomIds(from, to).contains(room.getId());
    }

    @Test
    void overlappingWindowIsOccupied() {
        assertThat(occupied(at(11, 0), at(13, 0))).isTrue();
        assertThat(occupied(at(9, 0), at(10, 1))).isTrue();
    }

    @Test
    void touchingWindowsAreFreeBecauseTheIntervalIsHalfOpen() {
        assertThat(occupied(at(12, 0), at(13, 0))).isFalse();
        assertThat(occupied(at(9, 0), at(10, 0))).isFalse();
    }

    /** Check-in is only possible shortly before the start (feature 014); these tests only need the status. */
    private void markActive() {
        var entity = reservationRepository.findById(reservation.id()).orElseThrow();
        entity.setStatus(at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE);
        reservationRepository.saveAndFlush(entity);
    }

    @Test
    void activeReservationStillOccupiesTheRoom() {
        markActive();

        assertThat(occupied(at(11, 0), at(13, 0))).isTrue();
    }

    @Test
    void cancelledReservationDoesNotOccupy() {
        reservationService.cancelReservation(reservation.id(), TestActors.ADMIN);

        assertThat(occupied(at(11, 0), at(13, 0))).isFalse();
    }

    @Test
    void expiredReservationDoesNotOccupy() {
        reservationService.expireReservation(reservation.id(), TestActors.ADMIN);

        assertThat(occupied(at(11, 0), at(13, 0))).isFalse();
    }

    @Test
    void completedReservationDoesNotOccupy() {
        markActive();
        reservationService.completeReservation(reservation.id(), TestActors.ADMIN);

        assertThat(occupied(at(11, 0), at(13, 0))).isFalse();
    }

    @Test
    void findTop10ByCreatedByUserIdAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc_filtersAndOrdersCorrectly() {
        UUID targetUser = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        for (UUID id : new UUID[] {targetUser, otherUser}) {
            jdbc.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)", id,
                    id + "@example.test", "user", "{pbkdf2-sha256-600000-v1}test-fixture-not-a-real-password");
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // 1. Target user, past reservation (endTime <= now)
        at.mci.igp.raumlotse.domain.Reservation pastRes = new at.mci.igp.raumlotse.domain.Reservation();
        pastRes.setRoom(room);
        pastRes.setSeatingArrangement(room.getSeatingArrangements().get(0));
        pastRes.setStartTime(now.minus(2, ChronoUnit.HOURS));
        pastRes.setEndTime(now.minus(1, ChronoUnit.HOURS));
        pastRes.setStatus(at.mci.igp.raumlotse.domain.ReservationStatus.COMPLETED);
        pastRes.setExpectedAttendees(5);
        pastRes.setCreatedBy("user");
        pastRes.setCreatedByUserId(targetUser);
        pastRes.setReservedFor("Past");
        reservationRepository.save(pastRes);

        // 2. Target user, future cancelled reservation
        at.mci.igp.raumlotse.domain.Reservation cancelledRes = new at.mci.igp.raumlotse.domain.Reservation();
        cancelledRes.setRoom(room);
        cancelledRes.setSeatingArrangement(room.getSeatingArrangements().get(0));
        cancelledRes.setStartTime(now.plus(1, ChronoUnit.DAYS));
        cancelledRes.setEndTime(now.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        cancelledRes.setStatus(at.mci.igp.raumlotse.domain.ReservationStatus.CANCELLED);
        cancelledRes.setExpectedAttendees(5);
        cancelledRes.setCreatedBy("user");
        cancelledRes.setCreatedByUserId(targetUser);
        cancelledRes.setReservedFor("Cancelled");
        reservationRepository.save(cancelledRes);

        // 3. Other user, future valid reservation
        at.mci.igp.raumlotse.domain.Reservation otherRes = new at.mci.igp.raumlotse.domain.Reservation();
        otherRes.setRoom(room);
        otherRes.setSeatingArrangement(room.getSeatingArrangements().get(0));
        otherRes.setStartTime(now.plus(2, ChronoUnit.DAYS));
        otherRes.setEndTime(now.plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        otherRes.setStatus(at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED);
        otherRes.setExpectedAttendees(5);
        otherRes.setCreatedBy("user");
        otherRes.setCreatedByUserId(otherUser);
        otherRes.setReservedFor("Other");
        reservationRepository.save(otherRes);

        // 4. Target user: create 12 future reservations in descending order to test sorting and top 10 limit
        for (int i = 12; i >= 1; i--) {
            at.mci.igp.raumlotse.domain.Reservation res = new at.mci.igp.raumlotse.domain.Reservation();
            res.setRoom(room);
            res.setSeatingArrangement(room.getSeatingArrangements().get(0));
            res.setStartTime(now.plus(3 + i, ChronoUnit.DAYS));
            res.setEndTime(now.plus(3 + i, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
            res.setStatus(i % 2 == 0 ? at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED : at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE);
            res.setExpectedAttendees(5);
            res.setCreatedBy("user");
        res.setCreatedByUserId(targetUser);
            res.setReservedFor("Upcoming " + i);
            reservationRepository.save(res);
        }

        List<at.mci.igp.raumlotse.domain.Reservation> results = reservationRepository
                .findTop10ByCreatedByUserIdAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc(
                        targetUser,
                        List.of(at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED, at.mci.igp.raumlotse.domain.ReservationStatus.ACTIVE),
                        now);

        assertThat(results).hasSize(10);
        assertThat(results.stream().map(at.mci.igp.raumlotse.domain.Reservation::getReservedFor).toList())
                .containsExactly(
                        "Upcoming 1", "Upcoming 2", "Upcoming 3", "Upcoming 4", "Upcoming 5",
                        "Upcoming 6", "Upcoming 7", "Upcoming 8", "Upcoming 9", "Upcoming 10"
                );
    }
}
