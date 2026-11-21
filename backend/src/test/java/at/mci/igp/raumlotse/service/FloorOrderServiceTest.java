package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.BuildingRepository;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FloorOrderServiceTest {

    @Mock FloorRepository floors;
    @Mock BuildingRepository buildings;
    @Mock RoomRepository rooms;
    @Mock FloorMapRepository floorMaps;

    FloorService service;
    Building building;
    UUID buildingId = UUID.randomUUID();
    List<Floor> stored = new ArrayList<>();

    Floor floor(String name, int position) {
        Floor f = new Floor(building, name);
        ReflectionTestUtils.setField(f, "id", UUID.randomUUID());
        f.setPosition(position);
        f.setGroundFloor(position == 0);
        stored.add(f);
        return f;
    }

    @BeforeEach
    void setUp() {
        building = new Building("Haus A");
        ReflectionTestUtils.setField(building, "id", buildingId);
        service = new FloorService(floors, buildings, rooms, floorMaps);
        when(buildings.findById(buildingId)).thenReturn(Optional.of(building));
        when(buildings.existsById(buildingId)).thenReturn(true);
        when(floors.findByBuildingId(buildingId)).thenAnswer(i -> new ArrayList<>(stored));
        when(floors.save(any(Floor.class))).thenAnswer(i -> {
            Floor f = i.getArgument(0);
            if (f.getId() == null) {
                ReflectionTestUtils.setField(f, "id", UUID.randomUUID());
            }
            if (!stored.contains(f)) {
                stored.add(f);
            }
            return f;
        });
    }

    @Test
    void firstFloorOfBuildingBecomesGroundFloor() {
        Floor created = service.create(buildingId, "EG", null);
        assertThat(created.getPosition()).isZero();
        assertThat(created.isGroundFloor()).isTrue();
    }

    @Test
    void newFloorIsAppendedOnTop() {
        floor("EG", 0);
        floor("1.OG", 1);
        Floor created = service.create(buildingId, "2.OG", true);
        assertThat(created.getPosition()).isEqualTo(2);
        assertThat(created.isGroundFloor()).isFalse();
    }

    @Test
    void deleteClosesTheGap() {
        Floor eg = floor("EG", 0);
        Floor og1 = floor("1.OG", 1);
        Floor og2 = floor("2.OG", 2);
        when(floors.findById(eg.getId())).thenReturn(Optional.of(eg));
        service.delete(eg.getId());
        stored.remove(eg);
        assertThat(og1.getPosition()).isZero();
        assertThat(og1.isGroundFloor()).isTrue();
        assertThat(og2.getPosition()).isEqualTo(1);
    }

    @Test
    void reorderAppliesGivenOrderBottomFirst() {
        Floor a = floor("A", 0);
        Floor b = floor("B", 1);
        Floor c = floor("C", 2);
        List<Floor> result = service.reorder(buildingId, List.of(c.getId(), a.getId(), b.getId()));
        assertThat(result).extracting(Floor::getName).containsExactly("C", "A", "B");
        assertThat(c.getPosition()).isZero();
        assertThat(c.isGroundFloor()).isTrue();
        assertThat(a.isGroundFloor()).isFalse();
        assertThat(b.getPosition()).isEqualTo(2);
    }

    @Test
    void reorderRejectsMissingDuplicateOrForeignFloors() {
        Floor a = floor("A", 0);
        Floor b = floor("B", 1);
        assertThatThrownBy(() -> service.reorder(buildingId, List.of(a.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reorder(buildingId, List.of(a.getId(), a.getId())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reorder(buildingId, List.of(a.getId(), b.getId(), UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reorderOfUnknownBuildingIsNotFound() {
        UUID other = UUID.randomUUID();
        when(buildings.existsById(other)).thenReturn(false);
        assertThatThrownBy(() -> service.reorder(other, List.of())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void groundFloorInputIsIgnoredOnUpdate() {
        Floor a = floor("A", 0);
        Floor b = floor("B", 1);
        when(floors.findById(b.getId())).thenReturn(Optional.of(b));
        Floor updated = service.update(b.getId(), "B2", true);
        assertThat(updated.isGroundFloor()).isFalse();
        assertThat(updated.getName()).isEqualTo("B2");
        assertThat(a.isGroundFloor()).isTrue();
    }

    @Test
    void listIsOrderedFromGroundFloorUp() {
        floor("C", 2);
        floor("A", 0);
        floor("B", 1);
        assertThat(service.listByBuilding(buildingId, null)).extracting(Floor::getName).containsExactly("A", "B", "C");
    }
}
