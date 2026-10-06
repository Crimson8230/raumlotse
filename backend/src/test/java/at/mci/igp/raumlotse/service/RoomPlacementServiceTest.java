package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.FloorMap;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomPlacement;
import at.mci.igp.raumlotse.exception.MapRequestException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.RoomPlacementRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomPlacementServiceTest {

    @Mock FloorMapRepository maps;
    @Mock RoomRepository rooms;
    @Mock RoomPlacementRepository placements;

    RoomPlacementService service;
    Floor eg;
    Floor og;
    FloorMap mapEg;
    Room roomEg;
    Room roomOg;

    static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    @BeforeEach
    void setUp() {
        service = new RoomPlacementService(maps, rooms, placements);
        Building building = withId(new Building("Haus A"));
        eg = withId(new Floor(building, "EG"));
        og = withId(new Floor(building, "1. OG"));
        mapEg = withId(new FloorMap(eg, "image/png", 200, 100));
        roomEg = withId(new Room("R-EG", eg));
        roomOg = withId(new Room("R-OG", og));
        when(maps.findByIdWithFloor(mapEg.getId())).thenReturn(Optional.of(mapEg));
        when(rooms.findById(roomEg.getId())).thenReturn(Optional.of(roomEg));
        when(rooms.findById(roomOg.getId())).thenReturn(Optional.of(roomOg));
    }

    @Test
    void placesNewRoomAndReportsCreated() {
        when(placements.existsById(roomEg.getId())).thenReturn(false);
        when(placements.findById(roomEg.getId())).thenReturn(Optional.of(new RoomPlacement(roomEg, mapEg, 0.25, 0.5)));

        var result = service.place(mapEg.getId(), roomEg.getId(), 0.25, 0.5);

        assertThat(result.created()).isTrue();
        assertThat(result.placement().x()).isEqualTo(0.25);
        assertThat(result.placement().room().name()).isEqualTo("R-EG");
        verify(placements).upsert(roomEg.getId(), mapEg.getId(), 0.25, 0.5);
    }

    @Test
    void movingAnAlreadyPlacedRoomReportsNotCreated() {
        when(placements.existsById(roomEg.getId())).thenReturn(true);
        when(placements.findById(roomEg.getId())).thenReturn(Optional.of(new RoomPlacement(roomEg, mapEg, 0.9, 0.1)));

        var result = service.place(mapEg.getId(), roomEg.getId(), 0.9, 0.1);

        assertThat(result.created()).isFalse();
        verify(placements).upsert(roomEg.getId(), mapEg.getId(), 0.9, 0.1);
    }

    @Test
    void roomOfAnotherFloorIsRejectedWith422() {
        assertThatThrownBy(() -> service.place(mapEg.getId(), roomOg.getId(), 0.5, 0.5))
                .isInstanceOfSatisfying(MapRequestException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(ex.getCode()).isEqualTo("ROOM_FLOOR_MISMATCH");
                });
        verify(placements, never()).upsert(any(), any(), anyDouble(), anyDouble());
    }

    @Test
    void unknownMapOrRoomIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(maps.findByIdWithFloor(unknown)).thenReturn(Optional.empty());
        when(rooms.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.place(unknown, roomEg.getId(), 0.5, 0.5)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.place(mapEg.getId(), unknown, 0.5, 0.5)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void coordinatesOutsideUnitSquareAreRejected() {
        for (double[] bad : new double[][] { { -0.01, 0.5 }, { 0.5, 1.01 }, { Double.NaN, 0.5 } }) {
            assertThatThrownBy(() -> service.place(mapEg.getId(), roomEg.getId(), bad[0], bad[1]))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(placements, never()).upsert(any(), any(), anyDouble(), anyDouble());
    }

    @Test
    void removeDeletesThePlacementOfThatMap() {
        var placement = new RoomPlacement(roomEg, mapEg, 0.1, 0.1);
        when(placements.findById(roomEg.getId())).thenReturn(Optional.of(placement));

        service.remove(mapEg.getId(), roomEg.getId());

        verify(placements).delete(placement);
    }

    @Test
    void removeWithoutPlacementIsNotFound() {
        when(placements.findById(roomEg.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(mapEg.getId(), roomEg.getId())).isInstanceOf(NotFoundException.class);
    }
}
