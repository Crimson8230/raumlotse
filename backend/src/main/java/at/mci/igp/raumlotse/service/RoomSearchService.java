package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomSearchCriteria;
import at.mci.igp.raumlotse.dto.RoomResponse;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomSearchService {

    private static final Logger log = LoggerFactory.getLogger(RoomSearchService.class);

    private static final Comparator<Room> BY_BUILDING_THEN_NAME = Comparator
            .comparing((Room room) -> room.getFloor().getBuilding().getName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Room::getName, String.CASE_INSENSITIVE_ORDER);

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;

    public RoomSearchService(RoomRepository roomRepository, ReservationRepository reservationRepository) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
    }

    public List<RoomResponse> search(RoomSearchCriteria criteria) {
        validate(criteria);
        List<Room> candidates = criteria.buildingId() == null
                ? roomRepository.findSearchCandidates()
                : roomRepository.findSearchCandidatesInBuilding(criteria.buildingId());
        Set<UUID> occupied = criteria.hasTimeWindow()
                ? reservationRepository.findOccupiedRoomIds(criteria.from(), criteria.to())
                : Set.of();
        List<RoomResponse> result = candidates.stream()
                .filter(room -> criteria.matches(room, occupied))
                .sorted(BY_BUILDING_THEN_NAME)
                .map(RoomResponse::from)
                .toList();
        log.info("room_search result_count={}", result.size());
        return result;
    }

    /** Distinct names across fully active rooms, case-insensitive; the first spelling seen wins (FR-006). */
    public List<String> listSeatingArrangementNames() {
        Map<String, String> byKey = new LinkedHashMap<>();
        roomRepository.findSearchCandidates().forEach(room -> room.getSeatingArrangements()
                .forEach(arrangement -> byKey.putIfAbsent(
                        arrangement.getName().toLowerCase(Locale.ROOT), arrangement.getName())));
        return byKey.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void validate(RoomSearchCriteria criteria) {
        if (criteria.minPersons() != null && criteria.maxPersons() != null
                && criteria.minPersons() > criteria.maxPersons()) {
            throw new IllegalArgumentException("minPersons must not be greater than maxPersons.");
        }
        if ((criteria.from() == null) != (criteria.to() == null)) {
            throw new IllegalArgumentException("from and to must be given together.");
        }
        if (criteria.hasTimeWindow() && !criteria.to().isAfter(criteria.from())) {
            throw new IllegalArgumentException("to must be after from.");
        }
    }
}
