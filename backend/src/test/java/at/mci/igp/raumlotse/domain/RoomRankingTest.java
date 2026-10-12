package at.mci.igp.raumlotse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RoomRankingTest {

    private static final RoomSearchCriteria NO_CRITERIA = new RoomSearchCriteria(null, null, null);

    private static Room room(String building, String name, int... capacities) {
        Room room = new Room(name, new Floor(new Building(building), "EG"));
        List<SeatingArrangement> arrangements = new ArrayList<>();
        for (int capacity : capacities) {
            arrangements.add(new SeatingArrangement("Layout " + capacity, capacity));
        }
        room.replaceSeatingArrangements(arrangements);
        return room;
    }

    private static Room withId(Room room, UUID id) {
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private static List<String> rank(RoomRanking ranking, Room... rooms) {
        return List.of(rooms).stream().sorted(ranking).map(Room::getName).toList();
    }

    @Test
    void withoutRankingDataRoomsAreOrderedByBuildingThenNameCaseInsensitive() {
        Room b1 = room("haus 2", "A", 10);
        Room a2 = room("Haus 1", "b", 10);
        Room a1 = room("Haus 1", "A", 10);

        assertThat(rank(new RoomRanking(NO_CRITERIA, Set.of(), Map.of()), b1, a2, a1))
                .containsExactly("A", "b", "A");
        assertThat(List.of(b1, a2, a1).stream().sorted(new RoomRanking(NO_CRITERIA, Set.of(), Map.of())).toList())
                .containsExactly(a1, a2, b1);
    }

    @Test
    void roomsWithIdenticalBuildingAndNameAreOrderedByIdIndependentOfInputOrder() {
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Room first = withId(room("Haus 1", "Seminar", 10), lower);
        Room second = withId(room("Haus 1", "Seminar", 10), higher);
        RoomRanking ranking = new RoomRanking(NO_CRITERIA, Set.of(), Map.of());

        assertThat(List.of(second, first).stream().sorted(ranking).toList()).containsExactly(first, second);
        assertThat(List.of(first, second).stream().sorted(ranking).toList()).containsExactly(first, second);
    }

    @Test
    void roomsWithoutIdSortWithoutErrorAndAfterRoomsWithId() {
        Room unsaved = room("Haus 1", "Seminar", 10);
        Room saved = withId(room("Haus 1", "Seminar", 10), UUID.randomUUID());
        RoomRanking ranking = new RoomRanking(NO_CRITERIA, Set.of(), Map.of());

        assertThat(List.of(unsaved, saved).stream().sorted(ranking).toList()).containsExactly(saved, unsaved);
    }

    // --- US1: size bands (FR-003) ---

    private static RoomRanking groupOf(Integer minPersons) {
        return new RoomRanking(new RoomSearchCriteria(minPersons, null, null), Set.of(), Map.of());
    }

    private static Room roomWithLayouts(String name, Object... nameCapacityPairs) {
        Room room = new Room(name, new Floor(new Building("Haus 1"), "EG"));
        List<SeatingArrangement> arrangements = new ArrayList<>();
        for (int i = 0; i < nameCapacityPairs.length; i += 2) {
            arrangements.add(new SeatingArrangement((String) nameCapacityPairs[i], (Integer) nameCapacityPairs[i + 1]));
        }
        room.replaceSeatingArrangements(arrangements);
        return room;
    }

    @Test
    void smallestFittingRoomFirst() {
        assertThat(rank(groupOf(4), room("Haus 1", "C", 100), room("Haus 1", "B", 30), room("Haus 1", "A", 6)))
                .containsExactly("A", "B", "C");
    }

    @Test
    void capacityIsJudgedByTheSmallestSuitableArrangement() {
        Room d = roomWithLayouts("D", "Theater", 80, "Workshop", 10);
        Room e = roomWithLayouts("E", "Theater", 20);

        assertThat(rank(groupOf(8), e, d)).containsExactly("D", "E");
    }

    @Test
    void onlyArrangementsMatchingTheLayoutFilterCount() {
        Room d = roomWithLayouts("D", "Theater", 80, "Workshop", 10);
        Room e = roomWithLayouts("E", "Theater", 20);
        RoomRanking theater = new RoomRanking(
                new RoomSearchCriteria(8, null, null, "theater", Set.of()), Set.of(), Map.of());

        assertThat(rank(theater, d, e)).containsExactly("E", "D");
    }

    @Test
    void roomsInTheSameBandCountAsEqualOnSize() {
        // 21 and 24 seats for 20 persons are both at most 25 % larger: the name decides.
        assertThat(rank(groupOf(20), room("Haus 1", "G", 24), room("Haus 1", "F", 21)))
                .containsExactly("F", "G");
        assertThat(rank(groupOf(20), room("Haus 1", "F", 24), room("Haus 1", "G", 21)))
                .containsExactly("F", "G");
    }

    @Test
    void roomsMoreThanTwiceTheGroupSizeAreOrderedByExactSurplus() {
        assertThat(rank(groupOf(20), room("Haus 1", "A", 90), room("Haus 1", "B", 45)))
                .containsExactly("B", "A");
    }

    @Test
    void withoutMinPersonsCapacityDoesNotMatter() {
        assertThat(rank(groupOf(null), room("Haus 1", "B", 6), room("Haus 1", "A", 100)))
                .containsExactly("A", "B");
    }

    @Test
    void withOnlyMaxPersonsCapacityDoesNotMatter() {
        RoomRanking maxOnly = new RoomRanking(new RoomSearchCriteria(null, 200, null), Set.of(), Map.of());

        assertThat(rank(maxOnly, room("Haus 1", "B", 6), room("Haus 1", "A", 100))).containsExactly("A", "B");
    }

    @Test
    void bandLimitsAreInclusive() {
        // Group 20: band 1 up to 25 seats, band 2 up to 30, band 3 up to 40, band 4 above.
        assertThat(rank(groupOf(20), room("Haus 1", "A", 26), room("Haus 1", "B", 25))).containsExactly("B", "A");
        assertThat(rank(groupOf(20), room("Haus 1", "A", 31), room("Haus 1", "B", 30))).containsExactly("B", "A");
        assertThat(rank(groupOf(20), room("Haus 1", "A", 41), room("Haus 1", "B", 40))).containsExactly("B", "A");
        assertThat(rank(groupOf(20), room("Haus 1", "B", 25), room("Haus 1", "A", 21))).containsExactly("A", "B");
    }

    // --- US2: favorites are pinned (FR-002 step 1, FR-009) ---

    @Test
    void favoritesComeFirstRegardlessOfSize() {
        Room c = withId(room("Haus 1", "C", 100), UUID.randomUUID());
        Room a = withId(room("Haus 1", "A", 6), UUID.randomUUID());
        Room b = withId(room("Haus 1", "B", 30), UUID.randomUUID());
        RoomRanking ranking = new RoomRanking(new RoomSearchCriteria(4, null, null), Set.of(c.getId()), Map.of());

        assertThat(rank(ranking, a, b, c)).containsExactly("C", "A", "B");
    }

    @Test
    void severalFavoritesAreOrderedAmongThemselvesByTheRemainingKeys() {
        Room large = withId(room("Haus 1", "A", 100), UUID.randomUUID());
        Room small = withId(room("Haus 1", "B", 6), UUID.randomUUID());
        Room other = withId(room("Haus 1", "C", 5), UUID.randomUUID());
        RoomRanking ranking = new RoomRanking(new RoomSearchCriteria(4, null, null),
                Set.of(large.getId(), small.getId()), Map.of());

        assertThat(rank(ranking, large, other, small)).containsExactly("B", "A", "C");
    }

    // --- US3: unrequested specialised equipment (FR-004) ---

    private static EquipmentType equipment(String name, boolean specialised) {
        EquipmentType type = new EquipmentType(name);
        ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
        type.setSpecialised(specialised);
        return type;
    }

    private static final EquipmentType WHITEBOARD = equipment("Whiteboard", false);
    private static final EquipmentType PCS = equipment("PC-Arbeitsplätze", true);
    private static final EquipmentType LAB = equipment("Laborplätze", true);
    private static final EquipmentType AIRCON = equipment("Klimaanlage", false);
    private static final EquipmentType SOCKETS = equipment("Steckdosen am Platz", false);

    private static Room equipped(String name, int capacity, EquipmentType... equipment) {
        Room room = room("Haus 1", name, capacity);
        room.setEquipmentTypes(List.of(equipment));
        return room;
    }

    private static RoomRanking requiring(Integer minPersons, EquipmentType... required) {
        Set<UUID> ids = new java.util.HashSet<>();
        for (EquipmentType type : required) {
            ids.add(type.getId());
        }
        return new RoomRanking(new RoomSearchCriteria(minPersons, null, null, null, ids), Set.of(), Map.of());
    }

    @Test
    void roomWithoutUnneededSpecialisedEquipmentComesFirstInTheSameBand() {
        Room withPcs = equipped("A", 20, WHITEBOARD, PCS);
        Room plain = equipped("B", 20, WHITEBOARD);

        assertThat(rank(requiring(15, WHITEBOARD), withPcs, plain)).containsExactly("B", "A");
    }

    @Test
    void aBetterSizeBandWinsOverFewerSpecialisedItems() {
        Room smallLab = equipped("A", 21, WHITEBOARD, PCS, LAB);
        Room largePlain = equipped("B", 40, WHITEBOARD);

        assertThat(rank(requiring(20, WHITEBOARD), largePlain, smallLab)).containsExactly("A", "B");
    }

    @Test
    void withoutRequiredEquipmentFewerSpecialisedTypesComeFirst() {
        Room two = equipped("A", 20, PCS, LAB);
        Room one = equipped("B", 20, PCS);
        Room none = equipped("C", 20, WHITEBOARD);

        assertThat(rank(requiring(18), two, one, none)).containsExactly("C", "B", "A");
    }

    @Test
    void sameBandRoomWithPcsRanksBelowAPlainRoom() {
        Room k = equipped("K", 21, WHITEBOARD, PCS);
        Room l = equipped("L", 24, WHITEBOARD);

        assertThat(rank(requiring(20, WHITEBOARD), k, l)).containsExactly("L", "K");
    }

    @Test
    void generalEquipmentNeverLowersARoom() {
        Room comfortable = equipped("A", 24, WHITEBOARD, AIRCON, SOCKETS);
        Room bare = equipped("B", 24, WHITEBOARD);

        assertThat(rank(requiring(20, WHITEBOARD), bare, comfortable)).containsExactly("A", "B");
    }

    @Test
    void newlyCreatedEquipmentTypesAreGeneral() {
        EquipmentType fresh = new EquipmentType("Beamer 4K");

        assertThat(fresh.isSpecialised()).isFalse();
    }

    @Test
    void requiredSpecialisedEquipmentDoesNotCountAgainstTheRoom() {
        Room lab = equipped("A", 20, LAB);
        Room pcRoom = equipped("B", 20, PCS);

        assertThat(rank(requiring(18), lab, pcRoom)).containsExactly("A", "B");
        assertThat(rank(requiring(18, PCS), lab, pcRoom)).containsExactly("B", "A");
    }

    @Test
    void deactivatedSpecialisedEquipmentStillCounts() {
        EquipmentType oldLab = equipment("Altes Labor", true);
        oldLab.setStatus(EntityStatus.DEACTIVATED);
        Room withOldLab = equipped("A", 20, oldLab);
        Room plain = equipped("B", 20);

        assertThat(rank(requiring(18), withOldLab, plain)).containsExactly("B", "A");
    }

    // --- US4: popularity (FR-005) ---

    @Test
    void morePopularRoomsComeFirstAmongEquallyFittingRooms() {
        Room a = withId(room("Haus 1", "A", 20), UUID.randomUUID());
        Room b = withId(room("Haus 1", "B", 20), UUID.randomUUID());
        RoomRanking ranking = new RoomRanking(new RoomSearchCriteria(18, null, null), Set.of(),
                Map.of(a.getId(), 3L, b.getId(), 12L));

        assertThat(rank(ranking, a, b)).containsExactly("B", "A");
    }

    @Test
    void popularityNeverOverridesFavoritesSizeOrSpecialisedEquipment() {
        Room favorite = withId(room("Haus 1", "F", 100), UUID.randomUUID());
        Room fitting = withId(equipped("A", 20, WHITEBOARD), UUID.randomUUID());
        Room popularLab = withId(equipped("B", 20, PCS), UUID.randomUUID());
        Room popularLarge = withId(room("Haus 1", "C", 60), UUID.randomUUID());
        RoomRanking ranking = new RoomRanking(new RoomSearchCriteria(18, null, null), Set.of(favorite.getId()),
                Map.of(popularLab.getId(), 50L, popularLarge.getId(), 99L));

        assertThat(rank(ranking, popularLarge, popularLab, fitting, favorite)).containsExactly("F", "A", "B", "C");
    }

    @Test
    void roomsWithoutBookingsCountAsZero() {
        Room booked = withId(room("Haus 1", "B", 20), UUID.randomUUID());
        Room never = withId(room("Haus 1", "A", 20), UUID.randomUUID());
        RoomRanking ranking = new RoomRanking(NO_CRITERIA, Set.of(), Map.of(booked.getId(), 1L));

        assertThat(rank(ranking, never, booked)).containsExactly("B", "A");
    }

    @Test
    void withoutCriteriaFavoritesThenPopularityThenName() {
        Room favorite = withId(room("Haus 1", "Z", 10), UUID.randomUUID());
        Room popular = withId(room("Haus 1", "Y", 10), UUID.randomUUID());
        Room quiet = withId(room("Haus 1", "A", 10), UUID.randomUUID());
        RoomRanking ranking = new RoomRanking(NO_CRITERIA, Set.of(favorite.getId()), Map.of(popular.getId(), 5L));

        assertThat(rank(ranking, quiet, popular, favorite)).containsExactly("Z", "Y", "A");
    }
}
