package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.SeatingArrangement;
import at.mci.igp.raumlotse.dto.EquipmentTypeResponse;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.dto.ReservationSweepResponse;
import at.mci.igp.raumlotse.dto.ReservationUpdateRequest;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.EquipmentTypeRepository;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import at.mci.igp.raumlotse.repository.SeatingArrangementRepository;
import at.mci.igp.raumlotse.config.ReservationPolicyConstants;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@Transactional
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final Clock clock;
    private final BookingConfirmationQueueService confirmationQueue;
    private final ReservationAccessPolicy accessPolicy;

    @Autowired
    public ReservationService(
            ReservationRepository reservationRepository,
            RoomRepository roomRepository,
            SeatingArrangementRepository seatingArrangementRepository,
            EquipmentTypeRepository equipmentTypeRepository,
            Clock clock,
            BookingConfirmationQueueService confirmationQueue,
            ReservationAccessPolicy accessPolicy) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.equipmentTypeRepository = equipmentTypeRepository;
        this.clock = clock;
        this.confirmationQueue = confirmationQueue;
        this.accessPolicy = accessPolicy;
    }

    public ReservationService(
            ReservationRepository reservationRepository,
            RoomRepository roomRepository,
            SeatingArrangementRepository seatingArrangementRepository,
            EquipmentTypeRepository equipmentTypeRepository,
            Clock clock) {
        this(reservationRepository, roomRepository, seatingArrangementRepository, equipmentTypeRepository, clock, null,
                new ReservationAccessPolicy());
    }

    public ReservationService(
            ReservationRepository reservationRepository,
            RoomRepository roomRepository,
            SeatingArrangementRepository seatingArrangementRepository,
            EquipmentTypeRepository equipmentTypeRepository) {
        this(reservationRepository, roomRepository, seatingArrangementRepository, equipmentTypeRepository,
                Clock.systemUTC(), null, new ReservationAccessPolicy());
    }

    public ReservationResponse createReservation(UUID roomId, ReservationCreateRequest request, AuthenticatedUser user) {
        if (user == null || user.userId() == null || user.displayName() == null || user.displayName().isBlank()) {
            throw new IllegalArgumentException("Die Identität der angemeldeten Person ist erforderlich.");
        }
        String effectiveCreatedBy = user.displayName().trim();
        if (request.reservedFor() == null || request.reservedFor().isBlank()) {
            throw new IllegalArgumentException("Die Person („reservedFor“) darf nicht leer sein.");
        }
        if (request.reservedFor().trim().length() > 255) {
            throw new IllegalArgumentException("Die Person („reservedFor“) darf höchstens 255 Zeichen lang sein.");
        }
        if (request.startTime() == null || request.endTime() == null) {
            throw new IllegalArgumentException("Beginn und Ende sind erforderlich.");
        }
        if (!request.startTime().isAfter(clock.instant())) {
            throw new IllegalArgumentException("Der Beginn der Reservierung muss in der Zukunft liegen.");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("Das Ende der Reservierung muss nach dem Beginn liegen.");
        }
        if (request.expectedAttendees() == null || request.expectedAttendees() < 1) {
            throw new IllegalArgumentException("Die Teilnehmerzahl muss eine ganze Zahl ab 1 sein.");
        }

        // Concurrency control: acquire pessimistic write lock on the target Room
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Raum " + roomId + " nicht gefunden."));

        if (room.getStatus() != EntityStatus.ACTIVE) {
            throw new ConflictException("Raum '" + room.getName() + "' ist deaktiviert und kann nicht reserviert werden.");
        }

        // Validate seating arrangement belongs to this room
        SeatingArrangement seatingArrangement = room.getSeatingArrangements().stream()
                .filter(sa -> sa.getId().equals(request.seatingArrangementId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Sitzordnung " + request.seatingArrangementId() + " nicht gefunden für Raum '" + room.getName() + "'."));

        if (request.expectedAttendees() > seatingArrangement.getMaxCapacity()) {
            throw new IllegalArgumentException("Die Teilnehmerzahl (" + request.expectedAttendees()
                    + ") überschreitet die Kapazität der Sitzordnung (" + seatingArrangement.getMaxCapacity() + ").");
        }

        // Turnover buffer: isolated calculation defaulting to zero Duration
        Duration turnoverBuffer = calculateTurnoverBuffer(room, seatingArrangement);
        Instant checkStart = request.startTime().minus(turnoverBuffer);
        Instant checkEnd = request.endTime().plus(turnoverBuffer);

        // Conflict check against active and reserved bookings for this room
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(roomId, checkStart, checkEnd);
        if (!conflicts.isEmpty()) {
            throw new ConflictException("Terminkonflikt: Der Raum ist in diesem Zeitraum bereits reserviert.");
        }

        Reservation reservation = new Reservation();
        reservation.setRoom(room);
        reservation.setSeatingArrangement(seatingArrangement);
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setExpectedAttendees(request.expectedAttendees());
        reservation.setNote(request.note());
        reservation.setCreatedBy(effectiveCreatedBy);
        reservation.setCreatedByUserId(user.userId());
        reservation.setReservedFor(request.reservedFor().trim());

        if (request.additionalEquipmentTypeIds() != null && !request.additionalEquipmentTypeIds().isEmpty()) {
            List<EquipmentType> additionalEquipment = resolveAdditionalEquipment(room, request.additionalEquipmentTypeIds());
            reservation.setAdditionalEquipment(additionalEquipment);
        }

        Reservation saved = reservationRepository.save(reservation);
        if (Boolean.TRUE.equals(request.emailNotification())) {
            if (confirmationQueue == null) {
                throw new IllegalStateException("Booking confirmation queue is unavailable.");
            }
            confirmationQueue.enqueue(saved);
        }
        return ReservationResponse.from(saved, true);
    }

    @Transactional(readOnly = true)
    public List<EquipmentTypeResponse> getAvailableEquipment(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Raum " + roomId + " nicht gefunden."));

        List<UUID> installedIds = room.getEquipmentTypes().stream()
                .map(eq -> eq.getId())
                .toList();

        return equipmentTypeRepository.findByStatus(EntityStatus.ACTIVE).stream()
                .filter(eq -> !installedIds.contains(eq.getId()))
                .map(EquipmentTypeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsForRoom(UUID roomId, Instant from, Instant to, Actor actor) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Raum " + roomId + " nicht gefunden."));
        List<Reservation> reservations;
        if (from == null && to == null) {
            reservations = reservationRepository.findByRoomIdOrderByStartTimeAsc(roomId);
        } else if (from != null && to == null) {
            reservations = reservationRepository.findByRoomIdAndEndTimeGreaterThanEqualOrderByStartTimeAsc(roomId, from);
        } else if (from == null && to != null) {
            reservations = reservationRepository.findByRoomIdAndStartTimeLessThanEqualOrderByStartTimeAsc(roomId, to);
        } else {
            reservations = reservationRepository.findByRoomIdAndEndTimeGreaterThanEqualAndStartTimeLessThanEqualOrderByStartTimeAsc(roomId, from, to);
        }
        return reservations.stream()
                .map(reservation -> accessPolicy.view(reservation, actor))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(UUID reservationId, Actor actor) {
        Reservation reservation = findManageableOrThrow(reservationId, actor, "read");
        return accessPolicy.view(reservation, actor);
    }

    public ReservationResponse updateReservationMetadata(UUID reservationId, ReservationUpdateRequest request,
            Actor actor) {
        Reservation reservation = findManageableOrThrow(reservationId, actor, "update");
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ConflictException("Nur reservierte Buchungen können bearbeitet werden.");
        }
        if (request.expectedAttendees() != null) {
            if (request.expectedAttendees() < 1) {
                throw new IllegalArgumentException("Die Teilnehmerzahl muss eine ganze Zahl ab 1 sein.");
            }
            int maxCapacity = reservation.getSeatingArrangement().getMaxCapacity();
            if (request.expectedAttendees() > maxCapacity) {
                throw new IllegalArgumentException("Die Teilnehmerzahl (" + request.expectedAttendees()
                        + ") überschreitet die Kapazität der Sitzordnung (" + maxCapacity + ").");
            }
            reservation.setExpectedAttendees(request.expectedAttendees());
        }
        if (request.note() != null) {
            reservation.setNote(request.note());
        }
        if (request.reservedFor() != null) {
            String trimmed = request.reservedFor().trim();
            if (trimmed.isEmpty() || trimmed.length() > 255) {
                throw new IllegalArgumentException("„Reserviert für“ darf nicht leer sein und höchstens 255 Zeichen lang sein.");
            }
            reservation.setReservedFor(trimmed);
        }
        Reservation saved = reservationRepository.save(reservation);
        return accessPolicy.view(saved, actor);
    }

    public ReservationResponse activateReservation(UUID reservationId, Actor actor) {
        Reservation reservation = findManageableOrThrow(reservationId, actor, "activate");
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ConflictException("Die Reservierung kann im Status nicht eingecheckt werden: " + reservation.getStatus());
        }
        Instant now = clock.instant();
        if (reservation.getEndTime() != null && !now.isBefore(reservation.getEndTime())) {
            throw new ConflictException("Der Reservierungszeitraum ist bereits vorbei; die Reservierung kann nicht eingecheckt werden.");
        }
        reservation.setStatus(ReservationStatus.ACTIVE);
        Reservation saved = reservationRepository.save(reservation);
        return accessPolicy.view(saved, actor);
    }

    public int expireUnattendedReservations() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(ReservationPolicyConstants.CHECK_IN_GRACE_PERIOD);
        List<Reservation> candidates = reservationRepository.findUnattendedReservationsForExpiration(
                ReservationStatus.RESERVED, cutoff, now);

        int count = 0;
        for (Reservation reservation : candidates) {
            try {
                reservation.setStatus(ReservationStatus.EXPIRED);
                reservation.setUpdatedAt(now);
                reservationRepository.save(reservation);
                count++;
                log.info("Auto-expired unattended reservation id={} roomId={} startTime={}",
                        reservation.getId(),
                        reservation.getRoom() != null ? reservation.getRoom().getId() : null,
                        reservation.getStartTime());
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Concurrent update detected when expiring reservation id={}, skipping", reservation.getId());
            }
        }
        return count;
    }

    public int completeOverdueActiveReservations() {
        Instant now = clock.instant();
        List<Reservation> candidates = reservationRepository.findByStatusAndEndTimeLessThanEqual(
                ReservationStatus.ACTIVE, now);

        int count = 0;
        for (Reservation reservation : candidates) {
            try {
                reservation.setStatus(ReservationStatus.COMPLETED);
                reservation.setUpdatedAt(now);
                reservationRepository.save(reservation);
                count++;
                log.info("Auto-completed concluded active reservation id={} roomId={} endTime={}",
                        reservation.getId(),
                        reservation.getRoom() != null ? reservation.getRoom().getId() : null,
                        reservation.getEndTime());
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Concurrent update detected when completing reservation id={}, skipping", reservation.getId());
            }
        }
        return count;
    }

    public ReservationSweepResponse sweepOverdueReservations() {
        int expired = expireUnattendedReservations();
        int completed = completeOverdueActiveReservations();
        return new ReservationSweepResponse(expired, completed);
    }

    public ReservationResponse completeReservation(UUID reservationId, Actor actor) {
        Reservation reservation = findManageableOrThrow(reservationId, actor, "complete");
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ConflictException("Die Reservierung kann im Status nicht abgeschlossen werden: " + reservation.getStatus());
        }
        reservation.setStatus(ReservationStatus.COMPLETED);
        Reservation saved = reservationRepository.save(reservation);
        return accessPolicy.view(saved, actor);
    }

    public ReservationResponse expireReservation(UUID reservationId, Actor actor) {
        Reservation reservation = findManageableOrThrow(reservationId, actor, "expire");
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ConflictException("Die Reservierung kann im Status nicht als abgelaufen markiert werden: " + reservation.getStatus());
        }
        reservation.setStatus(ReservationStatus.EXPIRED);
        Reservation saved = reservationRepository.save(reservation);
        return accessPolicy.view(saved, actor);
    }

    public ReservationResponse cancelReservation(UUID reservationId, Actor actor) {
        Reservation reservation = findManageableOrThrow(reservationId, actor, "cancel");
        if (reservation.getStatus() == ReservationStatus.COMPLETED
                || reservation.getStatus() == ReservationStatus.EXPIRED
                || reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ConflictException("Die Reservierung ist bereits im Endzustand " + reservation.getStatus() + " und kann nicht storniert werden.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        return accessPolicy.view(saved, actor);
    }

    private Reservation findManageableOrThrow(UUID reservationId, Actor actor, String action) {
        Reservation reservation = findReservationOrThrow(reservationId);
        accessPolicy.requireManage(reservation, actor, action);
        return reservation;
    }

    private Reservation findReservationOrThrow(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservierung " + reservationId + " nicht gefunden."));
    }

    private Duration calculateTurnoverBuffer(Room room, SeatingArrangement arrangement) {
        return Duration.ZERO;
    }

    private List<EquipmentType> resolveAdditionalEquipment(Room room, List<UUID> equipmentTypeIds) {
        List<UUID> installedIds = room.getEquipmentTypes().stream()
                .map(eq -> eq.getId())
                .toList();

        return equipmentTypeIds.stream().map(id -> {
            EquipmentType eq = equipmentTypeRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Ausstattungstyp " + id + " nicht gefunden."));
            if (eq.getStatus() != EntityStatus.ACTIVE) {
                throw new IllegalArgumentException("Ausstattungstyp '" + eq.getName() + "' ist deaktiviert und kann nicht reserviert werden.");
            }
            if (installedIds.contains(id)) {
                throw new IllegalArgumentException("Ausstattungstyp '" + eq.getName() + "' ist in diesem Raum bereits fest installiert.");
            }
            return eq;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyUpcomingReservations(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        Instant now = clock.instant();
        List<ReservationStatus> statuses = List.of(ReservationStatus.RESERVED, ReservationStatus.ACTIVE);
        return reservationRepository
                .findTop10ByCreatedByUserIdAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc(userId, statuses, now)
                .stream()
                .map(reservation -> ReservationResponse.from(reservation, true))
                .toList();
    }
}
