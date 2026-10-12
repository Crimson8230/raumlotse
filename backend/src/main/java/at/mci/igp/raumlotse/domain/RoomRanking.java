package at.mci.igp.raumlotse.domain;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orders search results by suitability (feature 017, FR-002). Keys are compared lexicographically:
 * <ol>
 *   <li>favorite of the searching user (favorites first)</li>
 *   <li>size band relative to the group size, then exact seat surplus within band 4 (FR-003)</li>
 *   <li>number of assigned specialised equipment types that were not required (FR-004)</li>
 *   <li>popularity: counted bookings of all users in the last 90 days, more first (FR-005)</li>
 *   <li>building name, room name (case-insensitive), then room id; a room without id sorts last (SC-001)</li>
 * </ol>
 * Only meant for rooms that already passed {@link RoomSearchCriteria#matches}.
 */
public final class RoomRanking implements Comparator<Room> {

    private static final Comparator<Room> BY_BUILDING_NAME_ID = Comparator
            .comparing((Room room) -> room.getFloor().getBuilding().getName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Room::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Room::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<Keys> BY_KEYS = Comparator
            .comparing(Keys::notFavorite)
            .thenComparingInt(Keys::sizeBand)
            .thenComparingInt(Keys::bandSurplus)
            .thenComparingInt(Keys::specialisedSurplus)
            .thenComparing(Keys::popularity, Comparator.reverseOrder());

    private final RoomSearchCriteria criteria;
    private final Set<UUID> favoriteRoomIds;
    private final Map<UUID, Long> popularityByRoomId;
    private final Map<Room, Keys> keys = new IdentityHashMap<>();

    public RoomRanking(RoomSearchCriteria criteria, Set<UUID> favoriteRoomIds, Map<UUID, Long> popularityByRoomId) {
        this.criteria = criteria;
        this.favoriteRoomIds = favoriteRoomIds == null ? Set.of() : favoriteRoomIds;
        this.popularityByRoomId = popularityByRoomId == null ? Map.of() : popularityByRoomId;
    }

    @Override
    public int compare(Room left, Room right) {
        int byKeys = BY_KEYS.compare(keysOf(left), keysOf(right));
        return byKeys != 0 ? byKeys : BY_BUILDING_NAME_ID.compare(left, right);
    }

    private Keys keysOf(Room room) {
        return keys.computeIfAbsent(room, this::computeKeys);
    }

    private Keys computeKeys(Room room) {
        int surplus = seatSurplus(room);
        int band = sizeBand(surplus);
        return new Keys(!isFavorite(room), band, band == 4 ? surplus : 0, specialisedSurplus(room),
                popularity(room));
    }

    /** Immutable sets reject {@code contains(null)}; a room without id is never a favorite. */
    private boolean isFavorite(Room room) {
        return room.getId() != null && favoriteRoomIds.contains(room.getId());
    }

    /**
     * Seats left over in the room's smallest arrangement that satisfies the arrangement filters, or -1 when there
     * is no group size to fit against (no {@code minPersons}).
     */
    private int seatSurplus(Room room) {
        Integer group = criteria.minPersons();
        if (group == null) {
            return -1;
        }
        return room.getSeatingArrangements().stream()
                .filter(criteria::arrangementMatches)
                .mapToInt(arrangement -> arrangement.getMaxCapacity() - group)
                .min()
                .orElse(-1);
    }

    /** Band 1 ≤ 25 %, 2 ≤ 50 %, 3 ≤ 100 % larger than needed, else 4; 0 without group size (integer math, R2). */
    private int sizeBand(int surplus) {
        if (surplus < 0) {
            return 0;
        }
        int group = criteria.minPersons();
        if (4 * surplus <= group) {
            return 1;
        }
        if (2 * surplus <= group) {
            return 2;
        }
        return surplus <= group ? 3 : 4;
    }

    /** Assigned specialised types the search did not require; general types never count (FR-004). */
    private int specialisedSurplus(Room room) {
        return (int) room.getEquipmentTypes().stream()
                .filter(EquipmentType::isSpecialised)
                .filter(type -> type.getId() == null || !criteria.equipmentTypeIds().contains(type.getId()))
                .count();
    }

    /** Immutable maps reject {@code get(null)}; a room without id or bookings has popularity 0. */
    private long popularity(Room room) {
        return room.getId() == null ? 0 : popularityByRoomId.getOrDefault(room.getId(), 0L);
    }

    private record Keys(boolean notFavorite, int sizeBand, int bandSurplus, int specialisedSurplus, long popularity) {
    }
}
