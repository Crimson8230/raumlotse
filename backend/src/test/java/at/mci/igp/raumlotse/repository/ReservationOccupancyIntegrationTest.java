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
}
