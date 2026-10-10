package at.mci.igp.raumlotse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.AbstractIntegrationTest;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.RoomService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Queries used by on-site check-in, presence events and room release (feature 014). */
@Transactional
class ReservationRepositoryPresenceTest extends AbstractIntegrationTest {
    private static final Instant DAY_START = Instant.parse("2031-03-10T23:00:00Z"); // 11.03.2031 00:00 Europe/Berlin
    private static final Instant DAY_END = DAY_START.plus(1, ChronoUnit.DAYS);

    @Autowired BuildingService buildings;
    @Autowired FloorService floors;
    @Autowired RoomService rooms;
    @Autowired ReservationRepository reservations;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Room room;
    private Room otherRoom;

    @BeforeEach
    void createRooms() {
        var building = buildings.create("Presence Repo " + UUID.randomUUID());
        var floor = floors.create(building.getId(), "1");
        room = rooms.create(new RoomCreateRequest("Presence Repo Room", floor.getId(),
                List.of(new SeatingArrangementRequest("Standard", 10)), List.of()));
        otherRoom = rooms.create(new RoomCreateRequest("Presence Repo Other", floor.getId(),
                List.of(new SeatingArrangementRequest("Standard", 10)), List.of()));
    }

    @Test
    void checkInCandidatesAreTodaysReservedActiveOrExpiredBookingsOfTheRoomOrderedByStart() {
        Reservation later = save(room, DAY_START.plus(14, ChronoUnit.HOURS), ReservationStatus.RESERVED);
        Reservation earlier = save(room, DAY_START.plus(8, ChronoUnit.HOURS), ReservationStatus.EXPIRED);
        Reservation active = save(room, DAY_START.plus(11, ChronoUnit.HOURS), ReservationStatus.ACTIVE);
        save(room, DAY_START.plus(9, ChronoUnit.HOURS), ReservationStatus.CANCELLED);
        save(room, DAY_START.plus(12, ChronoUnit.HOURS), ReservationStatus.COMPLETED);
        save(room, DAY_START.minus(1, ChronoUnit.HOURS), ReservationStatus.RESERVED);
        save(room, DAY_END, ReservationStatus.RESERVED);
        save(otherRoom, DAY_START.plus(10, ChronoUnit.HOURS), ReservationStatus.RESERVED);

        assertThat(reservations.findCheckInCandidates(room.getId(), DAY_START, DAY_END))
                .extracting(Reservation::getId)
                .containsExactly(earlier.getId(), active.getId(), later.getId());
    }

    @Test
    void activeCoveringFindsOnlyAnActiveBookingWhoseIntervalContainsNow() {
        Instant start = DAY_START.plus(10, ChronoUnit.HOURS);
        Reservation active = save(room, start, ReservationStatus.ACTIVE);
        save(otherRoom, start, ReservationStatus.ACTIVE);

        assertThat(reservations.findActiveCovering(room.getId(), start)).map(Reservation::getId).contains(active.getId());
        assertThat(reservations.findActiveCovering(room.getId(), start.plus(30, ChronoUnit.MINUTES))).isPresent();
        assertThat(reservations.findActiveCovering(room.getId(), start.plus(1, ChronoUnit.HOURS))).isEmpty();
        assertThat(reservations.findActiveCovering(room.getId(), start.minusNanos(1000))).isEmpty();

        active.setStatus(ReservationStatus.RESERVED);
        reservations.saveAndFlush(active);
        assertThat(reservations.findActiveCovering(room.getId(), start)).isEmpty();
    }

    @Test
    void coveringFindsReservedAndActiveBookingsWhoseIntervalContainsNow() {
        Instant start = DAY_START.plus(10, ChronoUnit.HOURS);
        Reservation reserved = save(room, start, ReservationStatus.RESERVED);
        save(room, start, ReservationStatus.EXPIRED);
        save(room, start, ReservationStatus.CANCELLED);
        save(otherRoom, start, ReservationStatus.ACTIVE);

        assertThat(reservations.findCovering(room.getId(), start.plus(10, ChronoUnit.MINUTES)))
                .extracting(Reservation::getId).containsExactly(reserved.getId());
        assertThat(reservations.findCovering(room.getId(), start.plus(1, ChronoUnit.HOURS))).isEmpty();
    }

    @Test
    void aBookingCheckedInEarlyIsInUseFromItsCheckInTime() {
        Instant start = DAY_START.plus(10, ChronoUnit.HOURS);
        Instant checkedIn = start.minus(5, ChronoUnit.MINUTES);
        Reservation early = save(room, start, ReservationStatus.ACTIVE);
        UUID owner = UUID.randomUUID();
        jdbc.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
                owner, owner + "@example.test", "Early", "{pbkdf2-sha256-600000-v1}test-fixture-not-a-real-password");
        early.recordCheckIn(at.mci.igp.raumlotse.domain.CheckInMethod.QR, owner, checkedIn);
        early.setCreatedByUserId(owner);
        reservations.saveAndFlush(early);
        Instant meanwhile = checkedIn.plus(2, ChronoUnit.MINUTES);

        assertThat(reservations.findCovering(room.getId(), meanwhile)).extracting(Reservation::getId).containsExactly(early.getId());
        assertThat(reservations.findActiveCovering(room.getId(), meanwhile)).isPresent();
        assertThat(reservations.findEligibleDeviceReservations(room.getId(), early.getCreatedByUserId(), meanwhile))
                .extracting(Reservation::getId).containsExactly(early.getId());
        assertThat(reservations.findCovering(room.getId(), checkedIn.minusSeconds(1))).isEmpty();
    }

    @Test
    void aReservedBookingCoversOnlyFromItsStart() {
        Instant start = DAY_START.plus(10, ChronoUnit.HOURS);
        save(room, start, ReservationStatus.RESERVED);

        assertThat(reservations.findCovering(room.getId(), start.minus(5, ChronoUnit.MINUTES))).isEmpty();
    }

    @Test
    void anotherActiveBookingOfTheRoomIsDetectedExcludingTheGivenOne() {
        Reservation first = save(room, DAY_START.plus(10, ChronoUnit.HOURS), ReservationStatus.ACTIVE);

        assertThat(reservations.existsByRoomIdAndStatusAndIdNot(room.getId(), ReservationStatus.ACTIVE, first.getId()))
                .isFalse();

        save(room, DAY_START.plus(11, ChronoUnit.HOURS), ReservationStatus.ACTIVE);
        assertThat(reservations.existsByRoomIdAndStatusAndIdNot(room.getId(), ReservationStatus.ACTIVE, first.getId()))
                .isTrue();
    }

    private Reservation save(Room target, Instant start, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setRoom(target);
        reservation.setSeatingArrangement(target.getSeatingArrangements().get(0));
        reservation.setStartTime(start);
        reservation.setEndTime(start.plus(1, ChronoUnit.HOURS));
        reservation.setStatus(status);
        reservation.setExpectedAttendees(1);
        reservation.setReservedFor("Presence Repo");
        reservation.setCreatedBy("Presence Repo");
        return reservations.saveAndFlush(reservation);
    }
}
