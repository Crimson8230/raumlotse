package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.CheckInMethod;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.CheckInResultResponse;
import at.mci.igp.raumlotse.dto.DeviceStatesResponse;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.CheckInService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.RoomService;
import at.mci.igp.raumlotse.service.RoomDeviceGateway;
import at.mci.igp.raumlotse.controller.CheckInController;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.CheckInRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.mockito.Mockito.doAnswer;
import at.mci.igp.raumlotse.service.RoomStatusService;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Feature 014 end to end against PostgreSQL: on-site check-in prepares the room through the stub device gateway, the
 * regular sweep releases it at the end, and a following booking already in use keeps it running.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PresenceCheckInIntegrationTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired BuildingService buildings;
    @Autowired FloorService floors;
    @Autowired RoomService rooms;
    @Autowired ReservationService reservationService;
    @Autowired ReservationRepository reservationRepository;
    @Autowired CheckInService checkIns;
    @Autowired RoomStatusService roomStatus;
    @Autowired CheckInController checkInController;
    /** Behaves like the stub gateway (acknowledges everything) unless a test makes a device fail. */
    @MockitoBean RoomDeviceGateway gateway;

    private static final Actor VIEWER = new Actor(UUID.randomUUID(), false);
    private static final DeviceStatesResponse PREPARED = new DeviceStatesResponse(true, true, "UNLOCKED");

    @Test
    void checkInPreparesTheRoomAndTheSweepReleasesItAtTheEnd() {
        Room room = createRoom("Lifecycle");
        ReservationCreateRequest request = bookingRequest(room, "Lifecycle Group");
        UUID reservationId = TestActors.create(jdbc, reservationService, room.getId(), request).id();
        moveTo(reservationId, Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.HOURS));

        CheckInResultResponse result = checkIns.checkIn(room.getId(), CheckInMethod.QR, TestActors.ownerOf(request));

        assertThat(result.failedDevices()).isEmpty();
        assertThat(result.devices()).isEqualTo(PREPARED);
        var occupied = roomStatus.status(room.getId(), VIEWER);
        assertThat(occupied.status()).isEqualTo("OCCUPIED");
        assertThat(occupied.devices()).isEqualTo(PREPARED);
        Reservation stored = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(stored.getCheckInMethod()).isEqualTo(CheckInMethod.QR);
        assertThat(stored.getCheckedInByUserId()).isEqualTo(TestActors.ownerOf(request).userId());

        moveTo(reservationId, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.SECONDS));
        reservationService.sweepOverdueReservations();

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.COMPLETED);
        var released = roomStatus.status(room.getId(), VIEWER);
        assertThat(released.status()).isEqualTo("AVAILABLE");
        assertThat(released.devices()).isEqualTo(new DeviceStatesResponse(false, false, "UNLOCKED"));
    }

    @Test
    void aFollowingBookingAlreadyInUseKeepsTheRoomRunning() {
        Room room = createRoom("Back to back");
        ReservationCreateRequest firstRequest = bookingRequest(room, "First Group");
        UUID first = TestActors.create(jdbc, reservationService, room.getId(), firstRequest).id();
        ReservationCreateRequest secondRequest = new ReservationCreateRequest(
                firstRequest.endTime(), firstRequest.endTime().plus(1, ChronoUnit.HOURS),
                firstRequest.seatingArrangementId(), 2, List.of(), null, "Second Group");
        UUID second = TestActors.create(jdbc, reservationService, room.getId(), secondRequest).id();

        moveTo(first, Instant.now().minus(2, ChronoUnit.MINUTES), Instant.now().plus(30, ChronoUnit.MINUTES));
        checkIns.checkIn(room.getId(), CheckInMethod.QR, TestActors.ownerOf(firstRequest));

        // The first booking is over but not yet swept while the second one has just been checked in.
        Instant boundary = Instant.now().minus(1, ChronoUnit.SECONDS);
        moveTo(first, boundary.minus(1, ChronoUnit.HOURS), boundary);
        moveTo(second, boundary, boundary.plus(1, ChronoUnit.HOURS));
        checkIns.checkIn(room.getId(), CheckInMethod.NFC, TestActors.ownerOf(secondRequest));

        reservationService.sweepOverdueReservations();

        assertThat(reservationRepository.findById(first).orElseThrow().getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        var status = roomStatus.status(room.getId(), VIEWER);
        assertThat(status.status()).isEqualTo("OCCUPIED");
        assertThat(status.devices()).isEqualTo(PREPARED);
    }

    @Test
    void aDeviceThatDoesNotAcknowledgeNeverUndoesTheCheckInOrTheRelease() {
        Room room = createRoom("Failing door");
        ReservationCreateRequest request = bookingRequest(room, "Failing Door Group");
        UUID reservationId = TestActors.create(jdbc, reservationService, room.getId(), request).id();
        moveTo(reservationId, Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.HOURS));
        doThrow(new IllegalStateException("door controller offline"))
                .when(gateway).setState(eq(room.getId()), eq(RoomDeviceKind.DOOR), any(Boolean.class));
        doThrow(new IllegalStateException("light controller offline"))
                .when(gateway).setState(room.getId(), RoomDeviceKind.LIGHTING, false);

        CheckInResultResponse result = checkIns.checkIn(room.getId(), CheckInMethod.NFC, TestActors.ownerOf(request));

        assertThat(result.failedDevices()).containsExactly(RoomDeviceKind.DOOR);
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.ACTIVE);
        assertThat(roomStatus.status(room.getId(), VIEWER).devices())
                .isEqualTo(new DeviceStatesResponse(true, true, "LOCKED"));

        moveTo(reservationId, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.SECONDS));
        reservationService.sweepOverdueReservations();

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.COMPLETED);
        assertThat(roomStatus.status(room.getId(), VIEWER).devices())
                .isEqualTo(new DeviceStatesResponse(true, false, "LOCKED"));
    }

    @Test
    void aCheckInThatLosesARaceAnswersAlreadyActiveWhenTheDevicesAlreadyExist() throws Exception {
        assertRaceLoserSeesAlreadyActive(true);
    }

    @Test
    void aCheckInThatLosesARaceAnswersAlreadyActiveOnTheRoomsFirstCheckIn() throws Exception {
        assertRaceLoserSeesAlreadyActive(false);
    }

    /**
     * Check-in A is held inside the device gateway (after it read the booking, before it commits) until check-in B of
     * the same booking has committed. A then fails at commit and must answer alreadyActive, not a generic conflict.
     * Without pre-existing device rows, A's conflict is the rooms device-state unique key instead of the version.
     */
    private void assertRaceLoserSeesAlreadyActive(boolean deviceRowsExist) throws Exception {
        Room room = createRoom("Race " + deviceRowsExist);
        ReservationCreateRequest request = bookingRequest(room, "Race Group " + deviceRowsExist);
        UUID reservationId = TestActors.create(jdbc, reservationService, room.getId(), request).id();
        moveTo(reservationId, Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now().plus(1, ChronoUnit.HOURS));
        if (deviceRowsExist) {
            for (String kind : List.of("LIGHTING", "VENTILATION", "DOOR")) {
                jdbc.update("insert into room_device_state(room_id, kind) values (?, ?)", room.getId(), kind);
            }
        }
        CountDownLatch firstIsWaiting = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstCall.compareAndSet(true, false)) {
                firstIsWaiting.countDown();
                assertThat(releaseFirst.await(30, TimeUnit.SECONDS)).isTrue();
            }
            return null;
        }).when(gateway).setState(eq(room.getId()), eq(RoomDeviceKind.LIGHTING), eq(true));
        var owner = TestActors.creatorOf(request);
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(owner.userId(), owner.displayName()), null, List.of());

        CompletableFuture<CheckInResultResponse> first = CompletableFuture.supplyAsync(() ->
                checkInController.checkIn(room.getId(), new CheckInRequest(CheckInMethod.QR), authentication));
        assertThat(firstIsWaiting.await(30, TimeUnit.SECONDS)).isTrue();
        CheckInResultResponse second =
                checkInController.checkIn(room.getId(), new CheckInRequest(CheckInMethod.NFC), authentication);
        releaseFirst.countDown();
        CheckInResultResponse loser = first.get(30, TimeUnit.SECONDS);

        assertThat(second.alreadyActive()).isFalse();
        assertThat(loser.alreadyActive()).isTrue();
        assertThat(loser.checkInMethod()).isEqualTo(CheckInMethod.NFC);
        Reservation stored = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(stored.getCheckInMethod()).isEqualTo(CheckInMethod.NFC);
        assertThat(roomStatus.status(room.getId(), VIEWER).devices()).isEqualTo(PREPARED);
    }

    private Room createRoom(String name) {
        var building = buildings.create("Check-in " + name + " " + UUID.randomUUID());
        var floor = floors.create(building.getId(), "1");
        return rooms.create(new RoomCreateRequest("Check-in " + name, floor.getId(),
                List.of(new SeatingArrangementRequest("Standard", 10)), List.of()));
    }

    private static ReservationCreateRequest bookingRequest(Room room, String reservedFor) {
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        return new ReservationCreateRequest(start, start.plus(1, ChronoUnit.HOURS),
                room.getSeatingArrangements().get(0).getId(), 2, List.of(), null, reservedFor);
    }

    /** Bookings can only be created in the future; tests shift them to the moment they need afterwards. */
    private void moveTo(UUID reservationId, Instant start, Instant end) {
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservationRepository.saveAndFlush(reservation);
    }
}
