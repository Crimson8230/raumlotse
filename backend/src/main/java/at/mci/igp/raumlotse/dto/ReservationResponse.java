package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID roomId,
        String roomName,
        Instant startTime,
        Instant endTime,
        ReservationStatus status,
        SeatingArrangementResponse seatingArrangement,
        Integer expectedAttendees,
        List<EquipmentTypeResponse> additionalEquipment,
        String note,
        String createdBy,
        String reservedFor,
        Instant createdAt,
        boolean ownedByMe) {

    public ReservationResponse(
            UUID id,
            UUID roomId,
            String roomName,
            Instant startTime,
            Instant endTime,
            ReservationStatus status,
            SeatingArrangementResponse seatingArrangement,
            Integer expectedAttendees,
            List<EquipmentTypeResponse> additionalEquipment,
            String note,
            String createdBy,
            String reservedFor,
            Instant createdAt) {
        this(id, roomId, roomName, startTime, endTime, status, seatingArrangement, expectedAttendees,
                additionalEquipment, note, createdBy, reservedFor, createdAt, false);
    }

    public ReservationResponse(
            UUID id,
            UUID roomId,
            String roomName,
            Instant startTime,
            Instant endTime,
            ReservationStatus status,
            SeatingArrangementResponse seatingArrangement,
            Integer expectedAttendees,
            List<EquipmentTypeResponse> additionalEquipment,
            String note,
            String createdBy,
            Instant createdAt) {
        this(id, roomId, roomName, startTime, endTime, status, seatingArrangement, expectedAttendees,
                additionalEquipment, note, createdBy, createdBy, createdAt, false);
    }

    /** Full details. {@code ownedByMe} tells the caller whether it created the reservation. */
    public static ReservationResponse from(Reservation reservation, boolean ownedByMe) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getRoom().getId(),
                reservation.getRoom().getName(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                SeatingArrangementResponse.from(reservation.getSeatingArrangement()),
                reservation.getExpectedAttendees(),
                reservation.getAdditionalEquipment().stream().map(EquipmentTypeResponse::from).toList(),
                reservation.getNote(),
                reservation.getCreatedBy(),
                reservation.getReservedFor(),
                reservation.getCreatedAt(),
                ownedByMe);
    }

    public static ReservationResponse from(Reservation reservation) {
        return from(reservation, false);
    }

    /** Occupancy only: no person, note, equipment or creation data of somebody else's reservation. */
    public static ReservationResponse redacted(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getRoom().getId(),
                reservation.getRoom().getName(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                false);
    }
}
