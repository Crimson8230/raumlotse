package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.RoomService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReservationCreationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationService reservationService;

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
                List.of(new SeatingArrangementRequest("Theater", 50)),
                List.of()));
    }

    @Test
    void createsReservationEndToEndAndRejectsOverlap() {
        Room room = createTestRoom("Room 201");
        UUID arrangementId = room.getSeatingArrangements().get(0).getId();

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        ReservationCreateRequest request1 = new ReservationCreateRequest(
                start, end, arrangementId, 30, List.of(), "Design Review", "Alice");

        ReservationResponse response1 = reservationService.createReservation(room.getId(), request1);

        assertThat(response1.id()).isNotNull();
        assertThat(response1.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(response1.roomName()).isEqualTo("Room 201");
        assertThat(response1.createdBy()).isEqualTo("Alice");

        // Overlapping attempt (1 hour into the first reservation)
        Instant overlapStart = start.plus(1, ChronoUnit.HOURS);
        Instant overlapEnd = overlapStart.plus(2, ChronoUnit.HOURS);
        ReservationCreateRequest request2 = new ReservationCreateRequest(
                overlapStart, overlapEnd, arrangementId, 20, List.of(), "Conflict attempt", "Bob");

        assertThatThrownBy(() -> reservationService.createReservation(room.getId(), request2))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Scheduling conflict");
    }

    @Test
    void concurrentOverlappingReservationAttemptsAreSerialized() throws Exception {
        Room room = createTestRoom("Room 202");
        UUID arrangementId = room.getSeatingArrangements().get(0).getId();

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                latch.await();
                reservationService.createReservation(room.getId(), new ReservationCreateRequest(
                        start, end, arrangementId, 25, List.of(), null, "User A"));
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                // other exceptions
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                latch.await();
                reservationService.createReservation(room.getId(), new ReservationCreateRequest(
                        start, end, arrangementId, 25, List.of(), null, "User B"));
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                // other exceptions
            }
        });

        latch.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }
}
