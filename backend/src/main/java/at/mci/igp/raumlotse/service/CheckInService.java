package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.CheckInMethod;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.CheckInPreviewResponse;
import at.mci.igp.raumlotse.dto.CheckInPreviewResponse.Booking;
import at.mci.igp.raumlotse.dto.CheckInPreviewResponse.Outcome;
import at.mci.igp.raumlotse.dto.CheckInResultResponse;
import at.mci.igp.raumlotse.exception.CheckInRejectedException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * On-site check-in by QR code or NFC (feature 014). The code only identifies the room, so the booking is the one of
 * today's reservations of that room that the caller may manage (owner or administrator) and whose check-in window
 * {@code startTime <= now <= startTime + grace period, now < endTime} is open.
 */
@Service
@Transactional
public class CheckInService {
    private static final Logger log = LoggerFactory.getLogger(CheckInService.class);

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationAccessPolicy accessPolicy;
    private final RoomAutomationService roomAutomation;
    private final RoomStatusService roomStatus;
    private final CheckInSettingsService settings;
    private final Clock clock;

    public CheckInService(RoomRepository roomRepository, ReservationRepository reservationRepository,
            ReservationAccessPolicy accessPolicy, RoomAutomationService roomAutomation, RoomStatusService roomStatus,
            CheckInSettingsService settings, Clock clock) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.accessPolicy = accessPolicy;
        this.roomAutomation = roomAutomation;
        this.roomStatus = roomStatus;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CheckInPreviewResponse preview(UUID roomId, Actor actor) {
        Room room = activeRoom(roomId);
        Instant now = clock.instant();
        Classification result = classify(roomId, candidates(roomId, actor, now), now);
        Booking booking = result.outcome() == Outcome.NO_MATCH || result.outcome() == Outcome.EXPIRED
                ? null : Booking.from(result.reservation());
        return new CheckInPreviewResponse(roomId, room.getName(), result.outcome(), booking, result.opensAt(),
                result.detail());
    }

    public CheckInResultResponse checkIn(UUID roomId, CheckInMethod method, Actor actor) {
        activeRoom(roomId);
        Instant now = clock.instant();
        Classification result = classify(roomId, candidates(roomId, actor, now), now);
        switch (result.outcome()) {
            case ALREADY_ACTIVE -> {
                return response(result.reservation(), true, List.of());
            }
            case READY -> {
                Reservation reservation = result.reservation();
                reservation.setStatus(ReservationStatus.ACTIVE);
                reservation.recordCheckIn(method, actor.userId(), now);
                Reservation saved = reservationRepository.save(reservation);
                log.info("check_in_accepted roomId={} reservationId={} method={}", roomId, saved.getId(), method);
                RoomAutomationService.AutomationResult automation = roomAutomation.prepare(saved);
                return response(saved, false, automation.failedDevices());
            }
            default -> {
                log.info("check_in_rejected roomId={} method={} reason={}", roomId, method, result.outcome());
                throw new CheckInRejectedException(result.outcome(), result.detail());
            }
        }
    }

    private CheckInResultResponse response(Reservation reservation, boolean alreadyActive,
            List<RoomDeviceKind> failedDevices) {
        return new CheckInResultResponse(reservation.getId(), reservation.getStatus().name(), alreadyActive,
                reservation.getCheckInMethod(), reservation.getCheckedInAt(),
                roomStatus.devicesFor(reservation.getRoom().getId()), failedDevices);
    }

    private Room activeRoom(UUID roomId) {
        return roomRepository.findById(roomId)
                .filter(room -> room.getStatus() == EntityStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Raum " + roomId + " nicht gefunden."));
    }

    /** Today's reservations (Europe/Berlin) of the room that the caller may manage. */
    private List<Reservation> candidates(UUID roomId, Actor actor, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, AdminStatisticsPeriod.ZONE);
        Instant dayStart = today.atStartOfDay(AdminStatisticsPeriod.ZONE).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(AdminStatisticsPeriod.ZONE).toInstant();
        return reservationRepository.findCheckInCandidates(roomId, dayStart, dayEnd).stream()
                .filter(reservation -> accessPolicy.canManage(reservation, actor))
                .toList();
    }

    private record Classification(Outcome outcome, Reservation reservation, Instant opensAt, String detail) {
        static Classification of(Outcome outcome, Reservation reservation, String detail) {
            return new Classification(outcome, reservation, null, detail);
        }
    }

    private Classification classify(UUID roomId, List<Reservation> reservations, Instant now) {
        CheckInPolicy policy = settings.current();
        Optional<Reservation> active = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.ACTIVE && now.isBefore(r.getEndTime()))
                .findFirst();
        if (active.isPresent()) {
            return Classification.of(Outcome.ALREADY_ACTIVE, active.get(), null);
        }
        Optional<Reservation> inWindow = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED && CheckInWindow.timeOpen(r, now, policy))
                .findFirst();
        if (inWindow.isPresent()) {
            Reservation booking = inWindow.get();
            if (!CheckInWindow.beforeStart(booking, now)) {
                return Classification.of(Outcome.READY, booking, null);
            }
            Optional<Instant> occupiedUntil = CheckInWindow.occupiedUntil(booking, reservationRepository.findCovering(roomId, now));
            if (occupiedUntil.isEmpty()) {
                return Classification.of(Outcome.READY, booking, null);
            }
            Instant opensAt = CheckInWindow.opensAt(booking, occupiedUntil, policy);
            return new Classification(Outcome.TOO_EARLY, booking, opensAt, CheckInWindow.tooEarlyMessage(opensAt, true));
        }
        Optional<Reservation> upcoming = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED && CheckInWindow.beforeStart(r, now))
                .min(Comparator.comparing(Reservation::getStartTime));
        if (upcoming.isPresent()) {
            Instant opensAt = CheckInWindow.earliest(upcoming.get(), policy);
            return new Classification(Outcome.TOO_EARLY, upcoming.get(), opensAt, CheckInWindow.tooEarlyMessage(opensAt, false));
        }
        Optional<Reservation> missed = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.EXPIRED
                        || (r.getStatus() == ReservationStatus.RESERVED && CheckInWindow.missed(r, now, policy)))
                .max(Comparator.comparing(Reservation::getStartTime));
        return missed.map(r -> Classification.of(Outcome.EXPIRED, r,
                        "Die Buchung ist abgelaufen, weil nicht rechtzeitig eingecheckt wurde."))
                .orElseGet(() -> Classification.of(Outcome.NO_MATCH, null,
                        "Für diesen Raum gibt es heute keine passende Buchung."));
    }
}
