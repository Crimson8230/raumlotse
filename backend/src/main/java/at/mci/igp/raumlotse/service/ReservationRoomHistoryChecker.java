package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.repository.ReservationRepository;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ReservationRoomHistoryChecker implements RoomDependentHistoryChecker {

    private final ReservationRepository reservationRepository;

    public ReservationRoomHistoryChecker(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Override
    public boolean hasDependentHistory(UUID roomId) {
        return reservationRepository.existsByRoomId(roomId);
    }
}
