package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.FloorMap;
import at.mci.igp.raumlotse.domain.FloorMapImage;
import at.mci.igp.raumlotse.exception.MapRequestException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ConnectionRepository;
import at.mci.igp.raumlotse.repository.FloorMapImageRepository;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import at.mci.igp.raumlotse.repository.RoomPlacementRepository;
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
class FloorMapServiceTest {

    @Mock FloorMapRepository maps;
    @Mock FloorMapImageRepository images;
    @Mock FloorRepository floors;
    @Mock RoomPlacementRepository placements;
    @Mock ConnectionRepository connections;

    FloorMapService service;
    Floor floor;
    UUID floorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FloorMapService(maps, images, floors, placements, connections, new MapImageValidator());
        floor = new Floor(new Building("Haus A"), "EG");
        ReflectionTestUtils.setField(floor, "id", floorId);
        when(floors.findById(floorId)).thenReturn(Optional.of(floor));
        when(maps.save(any(FloorMap.class))).thenAnswer(inv -> {
            FloorMap m = inv.getArgument(0);
            if (m.getId() == null) {
                ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
            }
            return m;
        });
    }

    @Test
    void createsMapForFloorWithDerivedName() throws Exception {
        when(maps.findByFloorId(floorId)).thenReturn(Optional.empty());

        var result = service.saveForFloor(floorId, MapImageValidatorTest.image("png", 200, 100));

        assertThat(result.created()).isTrue();
        assertThat(result.map().name()).isEqualTo("Haus A – EG");
        assertThat(result.map().widthPx()).isEqualTo(200);
        assertThat(result.map().imageVersion()).isEqualTo(1);
        assertThat(result.map().aspectRatioChanged()).isNull();
        verify(images).save(any(FloorMapImage.class));
    }

    @Test
    void replacingImageBumpsVersionAndKeepsRatioFlagFalseForSameAspect() throws Exception {
        FloorMap existing = new FloorMap(floor, "image/png", 100, 50);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(maps.findByFloorId(floorId)).thenReturn(Optional.of(existing));
        when(images.findById(existing.getId())).thenReturn(Optional.of(new FloorMapImage(existing.getId(), new byte[] {1})));

        var result = service.saveForFloor(floorId, MapImageValidatorTest.image("png", 400, 200));

        assertThat(result.created()).isFalse();
        assertThat(result.map().imageVersion()).isEqualTo(2);
        assertThat(result.map().widthPx()).isEqualTo(400);
        assertThat(result.map().aspectRatioChanged()).isFalse();
    }

    @Test
    void replacingImageWithDifferentAspectRatioWarns() throws Exception {
        FloorMap existing = new FloorMap(floor, "image/png", 100, 50);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(maps.findByFloorId(floorId)).thenReturn(Optional.of(existing));
        when(images.findById(existing.getId())).thenReturn(Optional.of(new FloorMapImage(existing.getId(), new byte[] {1})));

        var result = service.saveForFloor(floorId, MapImageValidatorTest.image("png", 100, 100));

        assertThat(result.map().aspectRatioChanged()).isTrue();
    }

    @Test
    void invalidImageLeavesCurrentMapUntouched() {
        assertThatThrownBy(() -> service.saveForFloor(floorId, "not an image".getBytes()))
                .isInstanceOf(MapRequestException.class);
        verify(maps, never()).save(any());
        verify(images, never()).save(any());
    }

    @Test
    void unknownFloorIsNotFound() throws Exception {
        UUID other = UUID.randomUUID();
        when(floors.findById(other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveForFloor(other, MapImageValidatorTest.image("png", 10, 10)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getUnknownMapIsNotFound() {
        UUID id = UUID.randomUUID();
        when(maps.findByIdWithFloor(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.image(id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listReportsPlacedRoomCount() {
        FloorMap map = new FloorMap(floor, "image/png", 100, 50);
        ReflectionTestUtils.setField(map, "id", UUID.randomUUID());
        when(maps.findAllWithFloor()).thenReturn(List.of(map));
        when(placements.countByMapId(map.getId())).thenReturn(3L);

        var list = service.list();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).placedRoomCount()).isEqualTo(3);
    }

    @Test
    void deleteRemovesMap() {
        FloorMap map = new FloorMap(floor, "image/png", 100, 50);
        ReflectionTestUtils.setField(map, "id", UUID.randomUUID());
        when(maps.findById(map.getId())).thenReturn(Optional.of(map));

        service.delete(map.getId());

        verify(maps).delete(map);
    }
}
