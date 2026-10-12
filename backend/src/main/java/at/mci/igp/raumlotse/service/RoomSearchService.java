package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomRanking;
import at.mci.igp.raumlotse.domain.RoomSearchCriteria;
import at.mci.igp.raumlotse.dto.RoomBookingCount;
import at.mci.igp.raumlotse.dto.RoomResponse;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoomSearchService {

    private static final Logger log = LoggerFactory.getLogger(RoomSearchService.class);

    /** Bookings within this window before the search count toward a room's popularity (FR-005). */
    static final Duration POPULARITY_WINDOW = Duration.ofDays(90);

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final RoomFavoriteService roomFavoriteService;
    private final Clock clock;

    public RoomSearchService(RoomRepository roomRepository, ReservationRepository reservationRepository,
            RoomFavoriteService roomFavoriteService, Clock clock) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.roomFavoriteService = roomFavoriteService;
        this.clock = clock;
    }

    /** Ranked without personal favorites. */
    public List<RoomResponse> search(RoomSearchCriteria criteria) {
        return search(criteria, null);
    }

    /** Filters as in feature 008, then orders by suitability for {@code userId} (feature 017, FR-002). */
    public List<RoomResponse> search(RoomSearchCriteria criteria, UUID userId) {
        validate(criteria);
        List<Room> candidates = criteria.buildingId() == null
                ? roomRepository.findSearchCandidates()
                : roomRepository.findSearchCandidatesInBuilding(criteria.buildingId());
        Set<UUID> occupied = criteria.hasTimeWindow()
                ? reservationRepository.findOccupiedRoomIds(criteria.from(), criteria.to())
                : Set.of();
        Set<UUID> favorites = roomFavoriteService.favoriteRoomIds(userId);
        List<Room> matching = candidates.stream()
                .filter(room -> criteria.matches(room, occupied))
                .filter(room -> !criteria.favoritesOnly() || (room.getId() != null && favorites.contains(room.getId())))
                .toList();
        List<RoomResponse> result = matching.stream()
                .sorted(new RoomRanking(criteria, favorites, popularity(matching)))
                .map(RoomResponse::from)
                .toList();
        log.info("room_search result_count={} favorites_only={} favorites_count={}", result.size(),
                criteria.favoritesOnly(), favorites.size());
        return result;
    }

    private Map<UUID, Long> popularity(List<Room> matching) {
        if (matching.size() < 2) {
            return Map.of();
        }
        Instant now = Instant.now(clock);
        return reservationRepository.countRecentByRoom(now.minus(POPULARITY_WINDOW), now).stream()
                .collect(Collectors.toMap(RoomBookingCount::roomId, RoomBookingCount::count));
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
            throw new IllegalArgumentException("Min. Personen darf nicht größer als Max. Personen sein.");
        }
        if ((criteria.from() == null) != (criteria.to() == null)) {
            throw new IllegalArgumentException("„from“ und „to“ müssen gemeinsam angegeben werden.");
        }
        if (criteria.hasTimeWindow() && !criteria.to().isAfter(criteria.from())) {
            throw new IllegalArgumentException("„to“ muss nach „from“ liegen.");
        }
    }
}
