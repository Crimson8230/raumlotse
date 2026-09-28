package at.mci.igp.raumlotse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RoomSearchCriteriaTest {

    private static Room roomWithCapacities(int... capacities) {
        Floor floor = new Floor(new Building("Haus 1"), "EG");
        Room room = new Room("Room", floor);
        room.replaceSeatingArrangements(Arrays.stream(capacities)
                .mapToObj(capacity -> new SeatingArrangement("Layout " + capacity, capacity))
                .toList());
        return room;
    }

    private static RoomSearchCriteria persons(Integer min, Integer max) {
        return new RoomSearchCriteria(min, max, null);
    }

    @Test
    void noCriteriaMatchesEveryRoom() {
        assertThat(persons(null, null).matches(roomWithCapacities(15))).isTrue();
    }

    @Test
    void minPersonsMatchesWhenSomeArrangementIsLargeEnough() {
        assertThat(persons(20, null).matches(roomWithCapacities(15, 40))).isTrue();
        assertThat(persons(20, null).matches(roomWithCapacities(15))).isFalse();
    }

    @Test
    void maxPersonsMatchesWhenSomeArrangementIsSmallEnough() {
        assertThat(persons(null, 50).matches(roomWithCapacities(120, 40))).isTrue();
        assertThat(persons(null, 50).matches(roomWithCapacities(120))).isFalse();
    }

    @Test
    void personRangeMustBeSatisfiedByASingleArrangement() {
        assertThat(persons(20, 50).matches(roomWithCapacities(10, 60))).isFalse();
        assertThat(persons(20, 50).matches(roomWithCapacities(10, 30, 60))).isTrue();
    }

    @Test
    void boundariesAreInclusive() {
        assertThat(persons(40, null).matches(roomWithCapacities(40))).isTrue();
        assertThat(persons(null, 40).matches(roomWithCapacities(40))).isTrue();
        assertThat(persons(40, 40).matches(roomWithCapacities(40))).isTrue();
    }

    private static Room roomWithArrangements(SeatingArrangement... arrangements) {
        Room room = new Room("Room", new Floor(new Building("Haus 1"), "EG"));
        room.replaceSeatingArrangements(List.of(arrangements));
        return room;
    }

    private static EquipmentType equipment(UUID id, String name) {
        EquipmentType type = new EquipmentType(name);
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }

    private static RoomSearchCriteria layout(String seatingArrangement, Integer minPersons) {
        return new RoomSearchCriteria(minPersons, null, null, seatingArrangement, Set.of());
    }

    private static RoomSearchCriteria equipmentIds(UUID... ids) {
        return new RoomSearchCriteria(null, null, null, null, Set.of(ids));
    }

    @Test
    void seatingArrangementMatchesTrimmedAndCaseInsensitively() {
        Room room = roomWithArrangements(new SeatingArrangement("U-Shape", 20));

        assertThat(layout(" u-shape ", null).matches(room)).isTrue();
        assertThat(layout("Theater", null).matches(room)).isFalse();
    }

    @Test
    void blankSeatingArrangementIsTreatedAsAbsent() {
        Room room = roomWithArrangements(new SeatingArrangement("Theater", 20));

        assertThat(layout("   ", null).seatingArrangement()).isNull();
        assertThat(layout("   ", null).matches(room)).isTrue();
    }

    @Test
    void layoutAndPersonRangeMustBeSatisfiedByTheSameArrangement() {
        Room room = roomWithArrangements(new SeatingArrangement("Theater", 60), new SeatingArrangement("U-Shape", 20));

        assertThat(layout("U-Shape", 30).matches(room)).isFalse();
        assertThat(layout("U-Shape", 20).matches(room)).isTrue();
    }

    @Test
    void equipmentFilterRequiresAllSelectedTypes() {
        UUID projector = UUID.randomUUID();
        UUID whiteboard = UUID.randomUUID();
        Room both = roomWithCapacities(20);
        both.setEquipmentTypes(List.of(equipment(projector, "Projector"), equipment(whiteboard, "Whiteboard")));
        Room projectorOnly = roomWithCapacities(20);
        projectorOnly.setEquipmentTypes(List.of(equipment(projector, "Projector")));

        assertThat(equipmentIds(projector, whiteboard).matches(both)).isTrue();
        assertThat(equipmentIds(projector).matches(both)).isTrue();
        assertThat(equipmentIds(projector, whiteboard).matches(projectorOnly)).isFalse();
    }

    @Test
    void unknownEquipmentIdNeverMatches() {
        Room room = roomWithCapacities(20);
        room.setEquipmentTypes(List.of(equipment(UUID.randomUUID(), "Projector")));

        assertThat(equipmentIds(UUID.randomUUID()).matches(room)).isFalse();
    }

    @Test
    void nullEquipmentSetMeansNoEquipmentFilter() {
        assertThat(new RoomSearchCriteria(null, null, null, null, null).equipmentTypeIds()).isEmpty();
    }

    private static Room reachableRoom(boolean reachable) {
        Room room = roomWithCapacities(20);
        room.getFloor().setGroundFloor(reachable);
        return room;
    }

    @Test
    void barrierFreeFilterKeepsOnlyReachableRooms() {
        RoomSearchCriteria barrierFree = new RoomSearchCriteria(null, null, null, null, Set.of(), true);

        assertThat(barrierFree.matches(reachableRoom(true))).isTrue();
        assertThat(barrierFree.matches(reachableRoom(false))).isFalse();
    }

    @Test
    void withoutBarrierFreeFilterReachabilityDoesNotMatter() {
        RoomSearchCriteria any = new RoomSearchCriteria(null, null, null, null, Set.of(), false);

        assertThat(any.matches(reachableRoom(false))).isTrue();
    }

    private static RoomSearchCriteria window(java.time.Instant from, java.time.Instant to) {
        return new RoomSearchCriteria(null, null, null, null, Set.of(), false, from, to);
    }

    @Test
    void occupiedRoomDoesNotMatchWhenATimeWindowIsGiven() {
        Room room = roomWithCapacities(20);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(room, "id", id);
        java.time.Instant from = java.time.Instant.parse("2026-10-05T09:00:00Z");

        assertThat(window(from, from.plusSeconds(3600)).matches(room, Set.of(id))).isFalse();
        assertThat(window(from, from.plusSeconds(3600)).matches(room, Set.of(UUID.randomUUID()))).isTrue();
    }

    @Test
    void occupancyIsIgnoredWithoutATimeWindow() {
        Room room = roomWithCapacities(20);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(room, "id", id);

        assertThat(window(null, null).matches(room, Set.of(id))).isTrue();
        assertThat(window(null, null).hasTimeWindow()).isFalse();
    }
}
