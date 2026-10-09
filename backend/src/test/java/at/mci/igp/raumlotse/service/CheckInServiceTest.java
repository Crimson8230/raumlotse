package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.CheckInMethod;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.CheckInPreviewResponse;
import at.mci.igp.raumlotse.dto.CheckInPreviewResponse.Outcome;
import at.mci.igp.raumlotse.dto.CheckInResultResponse;
import at.mci.igp.raumlotse.dto.DeviceStatesResponse;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import static org.mockito.Mockito.times;
import at.mci.igp.raumlotse.exception.CheckInRejectedException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {
    /** 10:00 in Europe/Berlin; the Berlin day runs from 22:00Z the evening before. */
    private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");
    private static final Instant DAY_START = Instant.parse("2026-10-08T22:00:00Z");
    private static final Instant DAY_END = Instant.parse("2026-10-09T22:00:00Z");

    @Mock RoomRepository roomRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock RoomAutomationService automation;
    @Mock RoomStatusService roomStatus;
    @Mock CheckInSettingsService settings;

    private final UUID ownerId = UUID.randomUUID();
    private final Actor owner = new Actor(ownerId, false);
    private final Actor stranger = new Actor(UUID.randomUUID(), false);
    private final Actor admin = new Actor(UUID.randomUUID(), true);
    private final List<Reservation> today = new ArrayList<>();
    private Room room;
    private UUID roomId;

    @BeforeEach
    void setUp() throws Exception {
        roomId = UUID.randomUUID();
        room = new Room("Seminarraum 1", new Floor(new Building("Main"), "1"));
        setField(room, "id", roomId);
        lenient().when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        lenient().when(reservationRepository.findCheckInCandidates(roomId, DAY_START, DAY_END)).thenReturn(today);
        lenient().when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(automation.prepare(any())).thenReturn(new RoomAutomationService.AutomationResult(List.of()));
        lenient().when(settings.current()).thenReturn(CheckInPolicy.DEFAULT);
        lenient().when(roomStatus.devicesFor(roomId)).thenReturn(new DeviceStatesResponse(true, true, "UNLOCKED"));
    }

    private CheckInService serviceAt(Instant now) {
        return new CheckInService(roomRepository, reservationRepository, new ReservationAccessPolicy(),
                automation, roomStatus, settings, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Reservation booking(Instant start, ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setRoom(room);
        reservation.setStartTime(start);
        reservation.setEndTime(start.plus(Duration.ofHours(1)));
        reservation.setStatus(status);
        reservation.setReservedFor("Projektgruppe");
        reservation.setCreatedByUserId(ownerId);
        today.add(reservation);
        return reservation;
    }

    @Test
    void ownerWithinWindowChecksInAndRecordsMethodActorAndTime() {
        Reservation reservation = booking(NOW.minus(Duration.ofMinutes(2)), ReservationStatus.RESERVED);

        CheckInResultResponse result = serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner);

        assertThat(result.reservationId()).isEqualTo(reservation.getId());
        assertThat(result.alreadyActive()).isFalse();
        assertThat(result.checkInMethod()).isEqualTo(CheckInMethod.QR);
        assertThat(result.checkedInAt()).isEqualTo(NOW);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.getCheckInMethod()).isEqualTo(CheckInMethod.QR);
        assertThat(reservation.getCheckedInByUserId()).isEqualTo(ownerId);
        assertThat(reservation.getCheckedInAt()).isEqualTo(NOW);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void windowIncludesStartAndStartPlusGracePeriod() {
        Instant start = NOW.minus(Duration.ofMinutes(5));
        booking(start, ReservationStatus.RESERVED);
        assertThat(serviceAt(start).preview(roomId, owner).outcome()).isEqualTo(Outcome.READY);
        assertThat(serviceAt(NOW).preview(roomId, owner).outcome()).isEqualTo(Outcome.READY);
        assertThat(serviceAt(NOW.plusNanos(1)).preview(roomId, owner).outcome()).isEqualTo(Outcome.EXPIRED);
    }

    @Test
    void windowClosesAtEndTimeEvenWithinGracePeriod() {
        Reservation shortBooking = booking(NOW.minus(Duration.ofMinutes(3)), ReservationStatus.RESERVED);
        shortBooking.setEndTime(NOW);

        assertThat(serviceAt(NOW).preview(roomId, owner).outcome()).isEqualTo(Outcome.EXPIRED);
    }

    @Test
    void tooEarlyNamesWhenCheckInOpensAndChangesNothing() {
        Reservation reservation = booking(NOW.plus(Duration.ofMinutes(20)), ReservationStatus.RESERVED);

        CheckInPreviewResponse preview = serviceAt(NOW).preview(roomId, owner);
        assertThat(preview.outcome()).isEqualTo(Outcome.TOO_EARLY);
        assertThat(preview.checkInOpensAt()).isEqualTo(reservation.getStartTime().minus(Duration.ofMinutes(10)));
        assertThat(preview.reservation().id()).isEqualTo(reservation.getId());

        assertThatThrownBy(() -> serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner))
                .isInstanceOfSatisfying(CheckInRejectedException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(Outcome.TOO_EARLY));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void expiredBookingIsReportedAsExpired() {
        booking(NOW.minus(Duration.ofMinutes(30)), ReservationStatus.EXPIRED);

        assertThatThrownBy(() -> serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner))
                .isInstanceOfSatisfying(CheckInRejectedException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(Outcome.EXPIRED));
    }

    @Test
    void anotherUsersBookingIsNotConfirmedAndNotRevealed() {
        Reservation reservation = booking(NOW, ReservationStatus.RESERVED);

        CheckInPreviewResponse preview = serviceAt(NOW).preview(roomId, stranger);
        assertThat(preview.outcome()).isEqualTo(Outcome.NO_MATCH);
        assertThat(preview.reservation()).isNull();

        assertThatThrownBy(() -> serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, stranger))
                .isInstanceOfSatisfying(CheckInRejectedException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(Outcome.NO_MATCH));
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void administratorMayConfirmSomeoneElsesBooking() {
        Reservation reservation = booking(NOW, ReservationStatus.RESERVED);

        serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, admin);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.getCheckedInByUserId()).isEqualTo(admin.userId());
    }

    @Test
    void alreadyActiveBookingIsIdempotent() {
        Reservation reservation = booking(NOW.minus(Duration.ofMinutes(10)), ReservationStatus.ACTIVE);
        reservation.recordCheckIn(CheckInMethod.NFC, ownerId, NOW.minus(Duration.ofMinutes(9)));

        CheckInResultResponse result = serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner);

        assertThat(result.alreadyActive()).isTrue();
        assertThat(result.checkInMethod()).isEqualTo(CheckInMethod.NFC);
        assertThat(result.checkedInAt()).isEqualTo(NOW.minus(Duration.ofMinutes(9)));
        verify(reservationRepository, never()).save(any());
        assertThat(serviceAt(NOW).preview(roomId, owner).outcome()).isEqualTo(Outcome.ALREADY_ACTIVE);
    }

    @Test
    void unknownOrInactiveRoomIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(roomRepository.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> serviceAt(NOW).preview(unknown, owner)).isInstanceOf(NotFoundException.class);

        room.setStatus(EntityStatus.DEACTIVATED);
        assertThatThrownBy(() -> serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void previewNeverSaves() {
        booking(NOW, ReservationStatus.RESERVED);

        CheckInPreviewResponse preview = serviceAt(NOW).preview(roomId, owner);

        assertThat(preview.roomName()).isEqualTo("Seminarraum 1");
        assertThat(preview.reservation().reservedFor()).isEqualTo("Projektgruppe");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void onlyTodaysBookingsInEuropeBerlinAreConsidered() {
        when(reservationRepository.findCheckInCandidates(eq(roomId), any(), any())).thenReturn(List.of());

        assertThat(serviceAt(NOW).preview(roomId, owner).outcome()).isEqualTo(Outcome.NO_MATCH);
        verify(reservationRepository).findCheckInCandidates(roomId, DAY_START, DAY_END);
    }

    @Test
    void reservedBookingInWindowWinsOverAnEarlierExpiredOne() {
        booking(NOW.minus(Duration.ofHours(2)), ReservationStatus.EXPIRED);
        Reservation current = booking(NOW.minus(Duration.ofMinutes(1)), ReservationStatus.RESERVED);

        CheckInPreviewResponse preview = serviceAt(NOW).preview(roomId, owner);

        assertThat(preview.outcome()).isEqualTo(Outcome.READY);
        assertThat(preview.reservation().id()).isEqualTo(current.getId());
    }

    @Test
    void acceptedCheckInPreparesTheRoomOnceAndReportsDevices() {
        Reservation reservation = booking(NOW, ReservationStatus.RESERVED);

        CheckInResultResponse result = serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner);

        verify(automation, times(1)).prepare(reservation);
        assertThat(result.devices()).isEqualTo(new DeviceStatesResponse(true, true, "UNLOCKED"));
        assertThat(result.failedDevices()).isEmpty();
    }

    @Test
    void failedDevicesAreReportedButTheCheckInStands() {
        Reservation reservation = booking(NOW, ReservationStatus.RESERVED);
        when(automation.prepare(reservation))
                .thenReturn(new RoomAutomationService.AutomationResult(List.of(RoomDeviceKind.DOOR)));

        CheckInResultResponse result = serviceAt(NOW).checkIn(roomId, CheckInMethod.NFC, owner);

        assertThat(result.failedDevices()).containsExactly(RoomDeviceKind.DOOR);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void repeatedOrRejectedCheckInsNeverSwitchDevices() {
        booking(NOW.minus(Duration.ofMinutes(10)), ReservationStatus.ACTIVE);
        serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner);
        assertThatThrownBy(() -> serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, stranger))
                .isInstanceOf(CheckInRejectedException.class);

        verify(automation, never()).prepare(any());
    }

    @Test
    void nfcCheckInRecordsNfcAndBehavesLikeQr() {
        Reservation reservation = booking(NOW, ReservationStatus.RESERVED);

        CheckInResultResponse result = serviceAt(NOW).checkIn(roomId, CheckInMethod.NFC, owner);

        assertThat(result.checkInMethod()).isEqualTo(CheckInMethod.NFC);
        assertThat(reservation.getCheckInMethod()).isEqualTo(CheckInMethod.NFC);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    @Test
    void checkInOpensTenMinutesBeforeTheStart() {
        Reservation reservation = booking(NOW.plus(Duration.ofMinutes(10)), ReservationStatus.RESERVED);

        CheckInResultResponse result = serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner);

        assertThat(result.alreadyActive()).isFalse();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.getCheckedInAt()).isEqualTo(NOW);
    }

    @Test
    void moreThanTenMinutesEarlyIsTooEarlyAndNamesWhenCheckInOpens() {
        Reservation reservation = booking(NOW.plus(Duration.ofMinutes(10)).plusNanos(1), ReservationStatus.RESERVED);

        CheckInPreviewResponse preview = serviceAt(NOW).preview(roomId, owner);

        assertThat(preview.outcome()).isEqualTo(Outcome.TOO_EARLY);
        assertThat(preview.checkInOpensAt()).isEqualTo(reservation.getStartTime().minus(Duration.ofMinutes(10)));
    }

    @Test
    void earlyCheckInWaitsWhileAnotherReservationOfTheRoomIsOngoing() {
        Reservation reservation = booking(NOW.plus(Duration.ofMinutes(5)), ReservationStatus.RESERVED);
        Reservation ongoing = new Reservation();
        ongoing.setId(UUID.randomUUID());
        ongoing.setStatus(ReservationStatus.ACTIVE);
        ongoing.setStartTime(NOW.minus(Duration.ofMinutes(55)));
        ongoing.setEndTime(NOW.plus(Duration.ofMinutes(5)));
        when(reservationRepository.findCovering(roomId, NOW)).thenReturn(List.of(ongoing));

        CheckInPreviewResponse preview = serviceAt(NOW).preview(roomId, owner);
        assertThat(preview.outcome()).isEqualTo(Outcome.TOO_EARLY);
        assertThat(preview.checkInOpensAt()).isEqualTo(ongoing.getEndTime());

        assertThatThrownBy(() -> serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner))
                .isInstanceOfSatisfying(CheckInRejectedException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(Outcome.TOO_EARLY);
                    assertThat(ex.getMessage()).contains("noch belegt");
                });
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void afterTheStartTheOwnBookingIsNotBlockedByOtherReservations() {
        Reservation reservation = booking(NOW.minus(Duration.ofMinutes(1)), ReservationStatus.RESERVED);

        serviceAt(NOW).checkIn(roomId, CheckInMethod.QR, owner);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        verify(reservationRepository, never()).findCovering(any(), any());
    }

    @Test
    void theWindowFollowsTheAdministratorsSettings() {
        when(settings.current()).thenReturn(new CheckInPolicy(Duration.ZERO, Duration.ofMinutes(15)));
        Reservation upcoming = booking(NOW.plus(Duration.ofMinutes(1)), ReservationStatus.RESERVED);

        CheckInPreviewResponse early = serviceAt(NOW).preview(roomId, owner);
        assertThat(early.outcome()).isEqualTo(Outcome.TOO_EARLY);
        assertThat(early.checkInOpensAt()).isEqualTo(upcoming.getStartTime());

        assertThat(serviceAt(upcoming.getStartTime().plus(Duration.ofMinutes(15))).preview(roomId, owner).outcome())
                .isEqualTo(Outcome.READY);
        assertThat(serviceAt(upcoming.getStartTime().plus(Duration.ofMinutes(15)).plusNanos(1)).preview(roomId, owner)
                .outcome()).isEqualTo(Outcome.EXPIRED);
    }

    @Test
    void previewExplainsWhyCheckInIsNotPossibleYet() {
        booking(NOW.plus(Duration.ofMinutes(5)), ReservationStatus.RESERVED);
        Reservation ongoing = new Reservation();
        ongoing.setId(UUID.randomUUID());
        ongoing.setStatus(ReservationStatus.ACTIVE);
        ongoing.setStartTime(NOW.minus(Duration.ofMinutes(55)));
        ongoing.setEndTime(NOW.plus(Duration.ofMinutes(5)));
        when(reservationRepository.findCovering(roomId, NOW)).thenReturn(List.of(ongoing));

        assertThat(serviceAt(NOW).preview(roomId, owner).detail())
                .isEqualTo("Der Raum ist noch belegt. Der Check-in ist ab 10:05 Uhr möglich.");
    }

    @Test
    void previewOfAnUpcomingBookingNamesOnlyTheTime() {
        booking(NOW.plus(Duration.ofMinutes(30)), ReservationStatus.RESERVED);

        assertThat(serviceAt(NOW).preview(roomId, owner).detail()).isEqualTo("Der Check-in ist ab 10:20 Uhr möglich.");
    }

    @Test
    void previewOfAReadyBookingHasNoExplanation() {
        booking(NOW, ReservationStatus.RESERVED);

        assertThat(serviceAt(NOW).preview(roomId, owner).detail()).isNull();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
