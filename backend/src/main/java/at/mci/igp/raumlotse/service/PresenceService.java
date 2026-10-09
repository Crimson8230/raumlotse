package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.dto.PresenceEventResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motion-sensor events (feature 014; simulated by administrators until real sensors exist). A motion event only
 * records the latest presence of the booking in use; it never checks a booking in and never switches a device.
 */
@Service
@Transactional
public class PresenceService {
    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final RoomRepository rooms;
    private final ReservationRepository reservations;
    private final Clock clock;

    public PresenceService(RoomRepository rooms, ReservationRepository reservations, Clock clock) {
        this.rooms = rooms;
        this.reservations = reservations;
        this.clock = clock;
    }

    public PresenceEventResponse recordMotion(UUID roomId) {
        if (rooms.findById(roomId).isEmpty()) {
            throw new NotFoundException("Raum " + roomId + " nicht gefunden.");
        }
        Instant now = clock.instant();
        Optional<Reservation> inUse = reservations.findActiveCovering(roomId, now);
        if (inUse.isEmpty()) {
            return new PresenceEventResponse(false, null);
        }
        Reservation reservation = inUse.get();
        reservation.setLastPresenceAt(now);
        reservations.save(reservation);
        log.info("presence_event_recorded roomId={} reservationId={}", roomId, reservation.getId());
        return new PresenceEventResponse(true, now);
    }
}
