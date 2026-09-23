package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.dto.ReservationSweepResponse;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.RoomService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationExpirationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomService roomService;

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private FloorService floorService;

    private Room createTestRoom(String roomName) {
        var building = buildingService.create("Building " + UUID.randomUUID());
        var floor = floorService.create(building.getId(), "1");
        return roomService.create(new RoomCreateRequest(
                roomName,
                floor.getId(),
                List.of(new SeatingArrangementRequest("Standard", 30)),
                List.of()));
    }

    @Test
    void unattendedReservationExpiresAndFreesRoomForNewBooking() {
        Room room = createTestRoom("Room Expire Test");
        UUID arrangementId = room.getSeatingArrangements().get(0).getId();

        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        ReservationResponse created = reservationService.createReservation(room.getId(), new ReservationCreateRequest(
                start,
                end,
                arrangementId,
                10,
                List.of(),
                "Test unattended booking",
                "organizer@example.com"));

        assertThat(created.status()).isEqualTo(ReservationStatus.RESERVED);

        // Manually age the reservation start & end time into the past (10 minutes ago) to simulate unattended grace expiration
        var entity = reservationRepository.findById(created.id()).orElseThrow();
        Instant pastStart = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant pastEnd = Instant.now().plus(30, ChronoUnit.MINUTES);
        entity.setStartTime(pastStart);
        entity.setEndTime(pastEnd);
        reservationRepository.saveAndFlush(entity);

        // Run sweep
        int expired = reservationService.expireUnattendedReservations();
        assertThat(expired).isGreaterThanOrEqualTo(1);

        // Verify status updated in DB
        var reloaded = reservationService.getReservation(created.id());
        assertThat(reloaded.status()).isEqualTo(ReservationStatus.EXPIRED);

        // Verify activation fails
        assertThatThrownBy(() -> reservationService.activateReservation(created.id()))
                .isInstanceOf(ConflictException.class);

        // Verify room is now free for a new booking overlapping the remaining slot
        ReservationResponse newBooking = reservationService.createReservation(room.getId(), new ReservationCreateRequest(
                Instant.now().plus(5, ChronoUnit.MINUTES),
                Instant.now().plus(25, ChronoUnit.MINUTES),
                arrangementId,
                5,
                List.of(),
                "Walk-in booking after expiration",
                "walkin@example.com"));

        assertThat(newBooking.status()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void activeReservationExemptFromExpirationAndAutoCompletesAtEndTime() {
        Room room = createTestRoom("Room Active Exemption Test");
        UUID arrangementId = room.getSeatingArrangements().get(0).getId();

        Instant start = Instant.now().plus(2, ChronoUnit.MINUTES);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);

        ReservationResponse created = reservationService.createReservation(room.getId(), new ReservationCreateRequest(
                start,
                end,
                arrangementId,
                8,
                List.of(),
                "Active exemption meeting",
                "organizer@example.com"));

        // Activate reservation (simulating check-in)
        ReservationResponse activated = reservationService.activateReservation(created.id());
        assertThat(activated.status()).isEqualTo(ReservationStatus.ACTIVE);

        // Age start time into the past past grace period, while end time remains in the future
        var entity = reservationRepository.findById(created.id()).orElseThrow();
        entity.setStartTime(Instant.now().minus(20, ChronoUnit.MINUTES));
        reservationRepository.saveAndFlush(entity);

        // Run sweep: active booking must NOT be expired
        ReservationSweepResponse sweep = reservationService.sweepOverdueReservations();
        var reloaded = reservationService.getReservation(created.id());
        assertThat(reloaded.status()).isEqualTo(ReservationStatus.ACTIVE);

        // Now age end time into the past to simulate concluded meeting
        entity = reservationRepository.findById(created.id()).orElseThrow();
        entity.setEndTime(Instant.now().minus(1, ChronoUnit.MINUTES));
        reservationRepository.saveAndFlush(entity);

        // Run sweep again: active booking past end time must transition to COMPLETED
        sweep = reservationService.sweepOverdueReservations();
        assertThat(sweep.completedCount()).isGreaterThanOrEqualTo(1);

        var completedReload = reservationService.getReservation(created.id());
        assertThat(completedReload.status()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    void getRoomReservationsScheduleContainsExpiredStatusAndExcludesExpiredFromConflicts() {
        Room room = createTestRoom("Room Schedule Expired Test");
        UUID arrangementId = room.getSeatingArrangements().get(0).getId();

        Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant end = Instant.now().plus(50, ChronoUnit.MINUTES);

        ReservationResponse created = reservationService.createReservation(room.getId(), new ReservationCreateRequest(
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS),
                arrangementId,
                10,
                List.of(),
                "Schedule expired meeting",
                "organizer@example.com"));

        // Age to past grace period and sweep to expire it
        var entity = reservationRepository.findById(created.id()).orElseThrow();
        entity.setStartTime(start);
        entity.setEndTime(end);
        reservationRepository.saveAndFlush(entity);

        ReservationSweepResponse sweep = reservationService.sweepOverdueReservations();
        assertThat(sweep.expiredCount()).isGreaterThanOrEqualTo(1);

        // Verify schedule includes the reservation with EXPIRED status
        List<ReservationResponse> schedule = reservationService.getReservationsForRoom(
                room.getId(),
                start.minus(1, ChronoUnit.HOURS),
                end.plus(1, ChronoUnit.HOURS));
        assertThat(schedule).extracting(ReservationResponse::id).contains(created.id());
        ReservationResponse inSchedule = schedule.stream()
                .filter(r -> r.id().equals(created.id()))
                .findFirst().orElseThrow();
        assertThat(inSchedule.status()).isEqualTo(ReservationStatus.EXPIRED);

        // Verify terminal state immutability on expired reservation
        assertThatThrownBy(() -> reservationService.activateReservation(created.id()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> reservationService.updateReservationMetadata(
                created.id(),
                new at.mci.igp.raumlotse.dto.ReservationUpdateRequest(12, "Attempted update")))
                .isInstanceOf(ConflictException.class);

        // Verify overlapping new reservation succeeds because expired slot is excluded from conflict detection
        ReservationResponse overlappingBooking = reservationService.createReservation(room.getId(), new ReservationCreateRequest(
                Instant.now().plus(5, ChronoUnit.MINUTES),
                Instant.now().plus(25, ChronoUnit.MINUTES),
                arrangementId,
                5,
                List.of(),
                "Overlapping booking on expired slot",
                "rebooker@example.com"));
        assertThat(overlappingBooking.status()).isEqualTo(ReservationStatus.RESERVED);
    }
}
