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
        int expectedAttendees,
        List<EquipmentTypeResponse> additionalEquipment,
        String note,
        String createdBy,
        Instant createdAt) {

    public static ReservationResponse from(Reservation reservation) {
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
                reservation.getCreatedAt());
    }
}
