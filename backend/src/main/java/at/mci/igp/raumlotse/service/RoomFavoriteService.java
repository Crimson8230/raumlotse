package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomFavorite;
import at.mci.igp.raumlotse.dto.FavoriteRoomResponse;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.RoomFavoriteRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personal favorite rooms of the signed-in user (feature 017, FR-006 – FR-010). */
@Service
@Transactional(readOnly = true)
public class RoomFavoriteService {

    public static final int MAX_FAVORITES = 10;

    private static final Logger log = LoggerFactory.getLogger(RoomFavoriteService.class);

    private static final Comparator<FavoriteRoomResponse> BY_BUILDING_THEN_ROOM = Comparator
            .comparing(FavoriteRoomResponse::buildingName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(FavoriteRoomResponse::roomName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(FavoriteRoomResponse::roomId);

    private final RoomFavoriteRepository favoriteRepository;
    private final RoomRepository roomRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public RoomFavoriteService(RoomFavoriteRepository favoriteRepository, RoomRepository roomRepository,
            UserAccountRepository userAccountRepository, Clock clock) {
        this.favoriteRepository = favoriteRepository;
        this.roomRepository = roomRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    /** All favorites, including unavailable rooms, sorted by building, then room name. */
    public List<FavoriteRoomResponse> list(UUID userId) {
        return favoriteRepository.findFavoriteRooms(userId).stream()
                .map(FavoriteRoomResponse::from)
                .sorted(BY_BUILDING_THEN_ROOM)
                .toList();
    }

    public Set<UUID> favoriteRoomIds(UUID userId) {
        return userId == null ? Set.of() : favoriteRepository.findRoomIdsByUserId(userId);
    }

    /**
     * Marks a fully active room; marking an existing favorite is a no-op, also at the limit. The user's account
     * row is locked first so two parallel marks cannot both pass the limit check (research R5a).
     */
    @Transactional
    public void mark(UUID userId, UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .filter(candidate -> candidate.getStatus() == EntityStatus.ACTIVE && candidate.getFloor().isSelectable())
                .orElseThrow(() -> new NotFoundException("Raum nicht gefunden", "ROOM_NOT_FOUND"));
        RoomFavorite.Key key = new RoomFavorite.Key(userId, room.getId());
        if (favoriteRepository.existsById(key)) {
            return;
        }
        userAccountRepository.findByIdForUpdate(userId);
        long count = favoriteRepository.countByIdUserId(userId);
        if (count >= MAX_FAVORITES) {
            log.info("room_favorite_limit_reached limit={}", MAX_FAVORITES);
            throw new ConflictException("Höchstens 10 Favoriten – entferne zuerst einen anderen.",
                    "FAVORITE_LIMIT_REACHED");
        }
        favoriteRepository.save(new RoomFavorite(userId, room.getId(), Instant.now(clock)));
        log.info("room_favorite_marked count={}", count + 1);
    }

    /** Removing a mark never fails, also for unknown rooms or rooms that are not marked. */
    @Transactional
    public void unmark(UUID userId, UUID roomId) {
        RoomFavorite.Key key = new RoomFavorite.Key(userId, roomId);
        if (favoriteRepository.existsById(key)) {
            favoriteRepository.deleteById(key);
        }
    }
}
