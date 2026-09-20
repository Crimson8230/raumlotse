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
import at.mci.igp.raumlotse.dto.ReservationUpdateRequest;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.EquipmentTypeRepository;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import at.mci.igp.raumlotse.repository.SeatingArrangementRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;

    public ReservationService(
            ReservationRepository reservationRepository,
            RoomRepository roomRepository,
            SeatingArrangementRepository seatingArrangementRepository,
            EquipmentTypeRepository equipmentTypeRepository) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.equipmentTypeRepository = equipmentTypeRepository;
    }

    public ReservationResponse createReservation(UUID roomId, ReservationCreateRequest request) {
        if (request.createdBy() == null || request.createdBy().isBlank()) {
            throw new IllegalArgumentException("Creator identity ('createdBy') cannot be blank.");
        }
        if (request.startTime() == null || request.endTime() == null) {
            throw new IllegalArgumentException("Start time and end time are required.");
        }
        if (!request.startTime().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Reservation start time must be in the future.");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("Reservation end time must be strictly after start time.");
        }
        if (request.expectedAttendees() == null || request.expectedAttendees() < 1) {
            throw new IllegalArgumentException("Expected attendees must be a positive integer greater than or equal to 1.");
        }

        // Concurrency control: acquire pessimistic write lock on the target Room
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Room " + roomId + " not found."));

        if (room.getStatus() != EntityStatus.ACTIVE) {
            throw new ConflictException("Room '" + room.getName() + "' is deactivated and cannot be reserved.");
        }

        // Validate seating arrangement belongs to this room
        SeatingArrangement seatingArrangement = room.getSeatingArrangements().stream()
                .filter(sa -> sa.getId().equals(request.seatingArrangementId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Seating arrangement " + request.seatingArrangementId() + " not found for room '" + room.getName() + "'."));

        if (request.expectedAttendees() > seatingArrangement.getMaxCapacity()) {
            throw new IllegalArgumentException("Expected attendees (" + request.expectedAttendees()
                    + ") cannot exceed arrangement capacity (" + seatingArrangement.getMaxCapacity() + ").");
        }

        // Turnover buffer: isolated calculation defaulting to zero Duration
        Duration turnoverBuffer = calculateTurnoverBuffer(room, seatingArrangement);
        Instant checkStart = request.startTime().minus(turnoverBuffer);
        Instant checkEnd = request.endTime().plus(turnoverBuffer);

        // Conflict check against active and reserved bookings for this room
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(roomId, checkStart, checkEnd);
        if (!conflicts.isEmpty()) {
            throw new ConflictException("Scheduling conflict: The room is already reserved during this time.");
        }

        Reservation reservation = new Reservation();
        reservation.setRoom(room);
        reservation.setSeatingArrangement(seatingArrangement);
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setExpectedAttendees(request.expectedAttendees());
        reservation.setNote(request.note());
        reservation.setCreatedBy(request.createdBy().trim());

        if (request.additionalEquipmentTypeIds() != null && !request.additionalEquipmentTypeIds().isEmpty()) {
            List<EquipmentType> additionalEquipment = resolveAdditionalEquipment(room, request.additionalEquipmentTypeIds());
            reservation.setAdditionalEquipment(additionalEquipment);
        }

        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<EquipmentTypeResponse> getAvailableEquipment(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room " + roomId + " not found."));

        List<UUID> installedIds = room.getEquipmentTypes().stream()
                .map(eq -> eq.getId())
                .toList();

        return equipmentTypeRepository.findByStatus(EntityStatus.ACTIVE).stream()
                .filter(eq -> !installedIds.contains(eq.getId()))
                .map(EquipmentTypeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsForRoom(UUID roomId, Instant from, Instant to) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room " + roomId + " not found."));
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
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(UUID reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        return ReservationResponse.from(reservation);
    }

    public ReservationResponse updateReservationMetadata(UUID reservationId, ReservationUpdateRequest request) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ConflictException("Only reservations in RESERVED status can be edited.");
        }
        if (request.expectedAttendees() != null) {
            if (request.expectedAttendees() < 1) {
                throw new IllegalArgumentException("Expected attendees must be a positive integer greater than or equal to 1.");
            }
            int maxCapacity = reservation.getSeatingArrangement().getMaxCapacity();
            if (request.expectedAttendees() > maxCapacity) {
                throw new IllegalArgumentException("Expected attendees (" + request.expectedAttendees()
                        + ") cannot exceed arrangement capacity (" + maxCapacity + ").");
            }
            reservation.setExpectedAttendees(request.expectedAttendees());
        }
        if (request.note() != null) {
            reservation.setNote(request.note());
        }
        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved);
    }

    public ReservationResponse activateReservation(UUID reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ConflictException("Reservation cannot be activated from status: " + reservation.getStatus());
        }
        reservation.setStatus(ReservationStatus.ACTIVE);
        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved);
    }

    public ReservationResponse completeReservation(UUID reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ConflictException("Reservation cannot be completed from status: " + reservation.getStatus());
        }
        reservation.setStatus(ReservationStatus.COMPLETED);
        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved);
    }

    public ReservationResponse expireReservation(UUID reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new ConflictException("Reservation cannot be expired from status: " + reservation.getStatus());
        }
        reservation.setStatus(ReservationStatus.EXPIRED);
        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved);
    }

    public ReservationResponse cancelReservation(UUID reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        if (reservation.getStatus() == ReservationStatus.COMPLETED
                || reservation.getStatus() == ReservationStatus.EXPIRED
                || reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ConflictException("Reservation is in terminal state " + reservation.getStatus() + " and cannot be cancelled.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(reservation);
        return ReservationResponse.from(saved);
    }

    private Reservation findReservationOrThrow(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation " + reservationId + " not found."));
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
                    .orElseThrow(() -> new NotFoundException("Equipment type " + id + " not found."));
            if (eq.getStatus() != EntityStatus.ACTIVE) {
                throw new IllegalArgumentException("Equipment type '" + eq.getName() + "' is deactivated and cannot be reserved.");
            }
            if (installedIds.contains(id)) {
                throw new IllegalArgumentException("Equipment type '" + eq.getName() + "' is already permanently installed in this room.");
            }
            return eq;
        }).toList();
    }
}
