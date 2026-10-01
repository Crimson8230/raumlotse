package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.SeatingArrangement;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.dto.ReservationSweepResponse;
import at.mci.igp.raumlotse.dto.ReservationUpdateRequest;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.repository.EquipmentTypeRepository;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import at.mci.igp.raumlotse.repository.SeatingArrangementRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private SeatingArrangementRepository seatingArrangementRepository;

    @Mock
    private EquipmentTypeRepository equipmentTypeRepository;

    private ReservationService reservationService;
    private Clock clock;
    private Instant fixedNow;

    private Room activeRoom;
    private SeatingArrangement seatingArrangement;
    private UUID roomId;
    private UUID seatingArrangementId;

    @BeforeEach
    void setUp() throws Exception {
        fixedNow = Instant.parse("2026-09-23T10:10:00Z");
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        reservationService = new ReservationService(
                reservationRepository,
                roomRepository,
                seatingArrangementRepository,
                equipmentTypeRepository,
                clock);

        roomId = UUID.randomUUID();
        seatingArrangementId = UUID.randomUUID();

        activeRoom = new Room("Room 101", new Floor(new Building("Main"), "1"));
        setField(activeRoom, "id", roomId);

        seatingArrangement = new SeatingArrangement("Theater", 40);
        setField(seatingArrangement, "id", seatingArrangementId);
        activeRoom.replaceSeatingArrangements(List.of(seatingArrangement));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void createReservation_successful() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        ReservationCreateRequest request = new ReservationCreateRequest(
                start, end, seatingArrangementId, 25, List.of(), "Team Sync", "Jane Doe");

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(activeRoom));
        when(reservationRepository.findConflictingReservations(roomId, start, end)).thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            setField(r, "id", UUID.randomUUID());
            setField(r, "createdAt", Instant.now());
            return r;
        });

        ReservationResponse response = reservationService.createReservation(roomId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(response.roomName()).isEqualTo("Room 101");
        assertThat(response.expectedAttendees()).isEqualTo(25);
        assertThat(response.createdBy()).isEqualTo("Jane Doe");
    }

    @Test
    void createReservation_conflictThrows409() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        ReservationCreateRequest request = new ReservationCreateRequest(
                start, end, seatingArrangementId, 25, List.of(), null, "Jane Doe");

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(activeRoom));
        Reservation existing = new Reservation();
        when(reservationRepository.findConflictingReservations(roomId, start, end)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> reservationService.createReservation(roomId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Scheduling conflict");
    }

    @Test
    void createReservation_backToBackZeroBufferAllowed() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        ReservationCreateRequest request = new ReservationCreateRequest(
                start, end, seatingArrangementId, 25, List.of(), null, "Jane Doe");

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(activeRoom));
        // Half-open interval [start, end) means no conflict if existing end == new start or existing start == new end
        when(reservationRepository.findConflictingReservations(roomId, start, end)).thenReturn(List.of());
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            setField(r, "id", UUID.randomUUID());
            setField(r, "createdAt", Instant.now());
            return r;
        });

        ReservationResponse response = reservationService.createReservation(roomId, request);
        assertThat(response.status()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void createReservation_exceedsSeatingCapacityThrowsIllegalArgument() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        // maxCapacity is 40, requested is 45
        ReservationCreateRequest request = new ReservationCreateRequest(
                start, end, seatingArrangementId, 45, List.of(), null, "Jane Doe");

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(activeRoom));

        assertThatThrownBy(() -> reservationService.createReservation(roomId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void createReservation_roomDeactivatedThrowsConflictException() {
        activeRoom.setStatus(EntityStatus.DEACTIVATED);
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        ReservationCreateRequest request = new ReservationCreateRequest(
                start, end, seatingArrangementId, 20, List.of(), null, "Jane Doe");

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(activeRoom));

        assertThatThrownBy(() -> reservationService.createReservation(roomId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void createReservation_pastStartTimeThrowsIllegalArgument() {
        Instant pastStart = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);
        ReservationCreateRequest request = new ReservationCreateRequest(
                pastStart, end, seatingArrangementId, 20, List.of(), null, "Jane Doe");

        assertThatThrownBy(() -> reservationService.createReservation(roomId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void createReservation_endTimeBeforeStartTimeThrowsIllegalArgument() {
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);
        ReservationCreateRequest request = new ReservationCreateRequest(
                start, end, seatingArrangementId, 20, List.of(), null, "Jane Doe");

        assertThatThrownBy(() -> reservationService.createReservation(roomId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end time");
    }

    @Test
    void getAvailableEquipment_filtersInstalledAndDeactivated() throws Exception {
        UUID micId = UUID.randomUUID();
        EquipmentType mic = new EquipmentType("Microphone");
        setField(mic, "id", micId);
        mic.setStatus(EntityStatus.ACTIVE);
        activeRoom.setEquipmentTypes(List.of(mic));

        UUID projectorId = UUID.randomUUID();
        EquipmentType projector = new EquipmentType("Projector");
        setField(projector, "id", projectorId);
        projector.setStatus(EntityStatus.ACTIVE);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(activeRoom));
        when(equipmentTypeRepository.findByStatus(EntityStatus.ACTIVE)).thenReturn(List.of(mic, projector));

        var result = reservationService.getAvailableEquipment(roomId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(projectorId);
        assertThat(result.get(0).name()).isEqualTo("Projector");
    }

    @Test
    void getAvailableEquipment_emptyWhenAllInstalled() throws Exception {
        UUID micId = UUID.randomUUID();
        EquipmentType mic = new EquipmentType("Microphone");
        setField(mic, "id", micId);
        mic.setStatus(EntityStatus.ACTIVE);
        activeRoom.setEquipmentTypes(List.of(mic));

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(activeRoom));
        when(equipmentTypeRepository.findByStatus(EntityStatus.ACTIVE)).thenReturn(List.of(mic));

        var result = reservationService.getAvailableEquipment(roomId);

        assertThat(result).isEmpty();
    }

    @Test
    void createReservation_deactivatedEquipmentThrowsIllegalArgument() throws Exception {
        UUID deactId = UUID.randomUUID();
        EquipmentType deactivatedEq = new EquipmentType("Old Cam");
        setField(deactivatedEq, "id", deactId);
        deactivatedEq.setStatus(EntityStatus.DEACTIVATED);

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        ReservationCreateRequest request = new ReservationCreateRequest(
                start, end, seatingArrangementId, 20, List.of(deactId), null, "Jane Doe");

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(activeRoom));
        when(reservationRepository.findConflictingReservations(eq(roomId), any(), any())).thenReturn(List.of());
        when(equipmentTypeRepository.findById(deactId)).thenReturn(Optional.of(deactivatedEq));

        assertThatThrownBy(() -> reservationService.createReservation(roomId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void createReservation_alreadyInstalledEquipmentThrowsIllegalArgument() throws Exception {
        UUID micId = UUID.randomUUID();
        EquipmentType mic = new EquipmentType("Microphone");
        setField(mic, "id", micId);
        mic.setStatus(EntityStatus.ACTIVE);
        activeRoom.setEquipmentTypes(List.of(mic));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        ReservationCreateRequest request = new ReservationCreateRequest(
                start, end, seatingArrangementId, 20, List.of(micId), null, "Jane Doe");

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(activeRoom));
        when(reservationRepository.findConflictingReservations(eq(roomId), any(), any())).thenReturn(List.of());
        when(equipmentTypeRepository.findById(micId)).thenReturn(Optional.of(mic));

        assertThatThrownBy(() -> reservationService.createReservation(roomId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already permanently installed");
    }

    @Test
    void getReservationsForRoom_returnsMappedList() throws Exception {
        Reservation r = new Reservation();
        setField(r, "id", UUID.randomUUID());
        setField(r, "room", activeRoom);
        setField(r, "seatingArrangement", seatingArrangement);
        setField(r, "startTime", Instant.now().plus(1, ChronoUnit.DAYS));
        setField(r, "endTime", Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        setField(r, "status", ReservationStatus.RESERVED);
        setField(r, "expectedAttendees", 20);
        setField(r, "createdBy", "Alice");
        setField(r, "createdAt", Instant.now());

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(activeRoom));
        when(reservationRepository.findByRoomIdOrderByStartTimeAsc(roomId)).thenReturn(List.of(r));

        var list = reservationService.getReservationsForRoom(roomId, null, null);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).createdBy()).isEqualTo("Alice");
    }

    @Test
    void updateReservationMetadata_success() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "seatingArrangement", seatingArrangement); // capacity 40
        setField(r, "startTime", Instant.now().plus(1, ChronoUnit.DAYS));
        setField(r, "endTime", Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
        setField(r, "status", ReservationStatus.RESERVED);
        setField(r, "expectedAttendees", 20);
        setField(r, "createdBy", "Alice");
        setField(r, "createdAt", Instant.now());

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReservationUpdateRequest update = new ReservationUpdateRequest(35, "Updated notes");
        ReservationResponse updated = reservationService.updateReservationMetadata(resId, update);

        assertThat(updated.expectedAttendees()).isEqualTo(35);
        assertThat(updated.note()).isEqualTo("Updated notes");
    }

    @Test
    void updateReservationMetadata_exceedsCapacity_throwsIllegalArgument() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "seatingArrangement", seatingArrangement); // capacity 40
        setField(r, "status", ReservationStatus.RESERVED);

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));

        ReservationUpdateRequest update = new ReservationUpdateRequest(50, null);
        assertThatThrownBy(() -> reservationService.updateReservationMetadata(resId, update))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void updateReservationMetadata_activeStatus_throwsConflict() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "seatingArrangement", seatingArrangement);
        setField(r, "status", ReservationStatus.ACTIVE);

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));

        ReservationUpdateRequest update = new ReservationUpdateRequest(20, null);
        assertThatThrownBy(() -> reservationService.updateReservationMetadata(resId, update))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("RESERVED");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ReservationStatus.class, names = {"EXPIRED", "COMPLETED", "CANCELLED"})
    void updateReservationMetadata_terminalStates_throwsConflict(ReservationStatus terminalStatus) throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "seatingArrangement", seatingArrangement);
        setField(r, "status", terminalStatus);

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));

        ReservationUpdateRequest update = new ReservationUpdateRequest(20, "Attempted update");
        assertThatThrownBy(() -> reservationService.updateReservationMetadata(resId, update))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only reservations in RESERVED status can be edited");
    }

    @Test
    void activateReservation_fromReserved_success() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "seatingArrangement", seatingArrangement);
        setField(r, "status", ReservationStatus.RESERVED);
        setField(r, "expectedAttendees", 20);
        setField(r, "createdBy", "Alice");
        setField(r, "createdAt", Instant.now());
        setField(r, "endTime", fixedNow.plus(Duration.ofHours(1)));

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReservationResponse res = reservationService.activateReservation(resId);
        assertThat(res.status()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void activateReservation_invalidPredecessor_throwsConflict() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "status", ReservationStatus.COMPLETED);

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> reservationService.activateReservation(resId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void completeReservation_fromActive_success() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "seatingArrangement", seatingArrangement);
        setField(r, "status", ReservationStatus.ACTIVE);
        setField(r, "expectedAttendees", 20);
        setField(r, "createdBy", "Alice");
        setField(r, "createdAt", Instant.now());

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReservationResponse res = reservationService.completeReservation(resId);
        assertThat(res.status()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    void expireReservation_fromReserved_success() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "seatingArrangement", seatingArrangement);
        setField(r, "status", ReservationStatus.RESERVED);
        setField(r, "expectedAttendees", 20);
        setField(r, "createdBy", "Alice");
        setField(r, "createdAt", Instant.now());

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReservationResponse res = reservationService.expireReservation(resId);
        assertThat(res.status()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void cancelReservation_fromReservedAndActive_success() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "seatingArrangement", seatingArrangement);
        setField(r, "status", ReservationStatus.RESERVED);
        setField(r, "expectedAttendees", 20);
        setField(r, "createdBy", "Alice");
        setField(r, "createdAt", Instant.now());

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReservationResponse res = reservationService.cancelReservation(resId);
        assertThat(res.status()).isEqualTo(ReservationStatus.CANCELLED);

        // Cancel from ACTIVE
        setField(r, "status", ReservationStatus.ACTIVE);
        res = reservationService.cancelReservation(resId);
        assertThat(res.status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancelReservation_terminalState_throwsConflict() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "status", ReservationStatus.CANCELLED);

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> reservationService.cancelReservation(resId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void expireUnattendedReservations_transitionsOverdueToExpired() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "status", ReservationStatus.RESERVED);
        setField(r, "startTime", fixedNow.minus(Duration.ofMinutes(6)));
        setField(r, "endTime", fixedNow.plus(Duration.ofMinutes(30)));
        setField(r, "createdAt", fixedNow.minus(Duration.ofHours(1)));

        when(reservationRepository.findUnattendedReservationsForExpiration(
                eq(ReservationStatus.RESERVED),
                eq(fixedNow.minus(Duration.ofMinutes(5))),
                eq(fixedNow)))
                .thenReturn(List.of(r));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        int expiredCount = reservationService.expireUnattendedReservations();

        assertThat(expiredCount).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(r.getUpdatedAt()).isNotNull();
    }

    @Test
    void activateReservation_rejectsWhenAlreadyExpired() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "status", ReservationStatus.EXPIRED);

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> reservationService.activateReservation(resId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("EXPIRED");
    }

    @Test
    void activateReservation_rejectsWhenEndTimeHasElapsed() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "status", ReservationStatus.RESERVED);
        setField(r, "startTime", fixedNow.minus(Duration.ofMinutes(15)));
        setField(r, "endTime", fixedNow.minus(Duration.ofMinutes(1)));

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> reservationService.activateReservation(resId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("elapsed");
    }

    @Test
    void completeOverdueActiveReservations_transitionsActiveToCompleted() throws Exception {
        UUID resId = UUID.randomUUID();
        Reservation r = new Reservation();
        setField(r, "id", resId);
        setField(r, "room", activeRoom);
        setField(r, "status", ReservationStatus.ACTIVE);
        setField(r, "startTime", fixedNow.minus(Duration.ofHours(2)));
        setField(r, "endTime", fixedNow.minus(Duration.ofMinutes(10)));
        setField(r, "createdAt", fixedNow.minus(Duration.ofHours(3)));

        when(reservationRepository.findByStatusAndEndTimeLessThanEqual(
                eq(ReservationStatus.ACTIVE),
                eq(fixedNow)))
                .thenReturn(List.of(r));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        int completedCount = reservationService.completeOverdueActiveReservations();

        assertThat(completedCount).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(r.getUpdatedAt()).isNotNull();
    }

    @Test
    void sweepOverdueReservations_sweepsBothUnattendedAndActive() throws Exception {
        when(reservationRepository.findUnattendedReservationsForExpiration(any(), any(), any()))
                .thenReturn(List.of());
        when(reservationRepository.findByStatusAndEndTimeLessThanEqual(any(), any()))
                .thenReturn(List.of());

        ReservationSweepResponse response = reservationService.sweepOverdueReservations();

        assertThat(response.expiredCount()).isEqualTo(0);
        assertThat(response.completedCount()).isEqualTo(0);
    }

    @Test
    void expireUnattendedReservations_boundaryExactCutoff() {
        // Cutoff calculated as clock.instant().minus(CHECK_IN_GRACE_PERIOD)
        // For a booking starting at fixedNow - 5m, cutoff is exactly startTime.
        // The repository is queried with graceCutoff = fixedNow - 5m.
        // We verify that the exact boundary passes the expected cutoff timestamp to repository.
        reservationService.expireUnattendedReservations();

        Instant expectedCutoff = fixedNow.minus(Duration.ofMinutes(5));
        org.mockito.Mockito.verify(reservationRepository).findUnattendedReservationsForExpiration(
                eq(ReservationStatus.RESERVED),
                eq(expectedCutoff),
                eq(fixedNow));
    }

    @Test
    void expireUnattendedReservations_handlesOptimisticLockFailureGracefully() throws Exception {
        UUID resId1 = UUID.randomUUID();
        Reservation r1 = new Reservation();
        setField(r1, "id", resId1);
        setField(r1, "room", activeRoom);
        setField(r1, "status", ReservationStatus.RESERVED);

        UUID resId2 = UUID.randomUUID();
        Reservation r2 = new Reservation();
        setField(r2, "id", resId2);
        setField(r2, "room", activeRoom);
        setField(r2, "status", ReservationStatus.RESERVED);

        when(reservationRepository.findUnattendedReservationsForExpiration(any(), any(), any()))
                .thenReturn(List.of(r1, r2));

        // First reservation throws optimistic lock conflict (e.g. concurrent activation), second succeeds
        when(reservationRepository.save(r1)).thenThrow(new OptimisticLockingFailureException("concurrent update"));
        when(reservationRepository.save(r2)).thenReturn(r2);

        int count = reservationService.expireUnattendedReservations();

        assertThat(count).isEqualTo(1);
        assertThat(r2.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }
}
