package at.mci.igp.raumlotse.repository;

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
        reservation = reservationService.createReservation(room.getId(), new ReservationCreateRequest(
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

    @Test
    void activeReservationStillOccupiesTheRoom() {
        reservationService.activateReservation(reservation.id());

        assertThat(occupied(at(11, 0), at(13, 0))).isTrue();
    }

    @Test
    void cancelledReservationDoesNotOccupy() {
        reservationService.cancelReservation(reservation.id());

        assertThat(occupied(at(11, 0), at(13, 0))).isFalse();
    }

    @Test
    void expiredReservationDoesNotOccupy() {
        reservationService.expireReservation(reservation.id());

        assertThat(occupied(at(11, 0), at(13, 0))).isFalse();
    }

    @Test
    void completedReservationDoesNotOccupy() {
        reservationService.activateReservation(reservation.id());
        reservationService.completeReservation(reservation.id());

        assertThat(occupied(at(11, 0), at(13, 0))).isFalse();
    }

    @Test
    void findTop10ByCreatedByAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc_filtersAndOrdersCorrectly() {
        String targetUser = "user-" + UUID.randomUUID();
        String otherUser = "user-" + UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // 1. Target user, past reservation (endTime <= now)
        at.mci.igp.raumlotse.domain.Reservation pastRes = new at.mci.igp.raumlotse.domain.Reservation();
        pastRes.setRoom(room);
        pastRes.setSeatingArrangement(room.getSeatingArrangements().get(0));
        pastRes.setStartTime(now.minus(2, ChronoUnit.HOURS));
        pastRes.setEndTime(now.minus(1, ChronoUnit.HOURS));
        pastRes.setStatus(at.mci.igp.raumlotse.domain.ReservationStatus.COMPLETED);
        pastRes.setExpectedAttendees(5);
        pastRes.setCreatedBy(targetUser);
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
        cancelledRes.setCreatedBy(targetUser);
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
        otherRes.setCreatedBy(otherUser);
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
            res.setCreatedBy(targetUser);
            res.setReservedFor("Upcoming " + i);
            reservationRepository.save(res);
        }

        List<at.mci.igp.raumlotse.domain.Reservation> results = reservationRepository
                .findTop10ByCreatedByAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc(
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
