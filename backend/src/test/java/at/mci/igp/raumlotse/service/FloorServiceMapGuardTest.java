package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.repository.BuildingRepository;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FloorServiceMapGuardTest {

    @Test
    void floorWithMapCannotBeDeleted() {
        var floors = mock(FloorRepository.class);
        var rooms = mock(RoomRepository.class);
        var maps = mock(FloorMapRepository.class);
        var floor = new Floor(new Building("Haus A"), "EG");
        UUID id = UUID.randomUUID();
        when(floors.findById(id)).thenReturn(Optional.of(floor));
        when(rooms.existsByFloorId(id)).thenReturn(false);
        when(maps.existsByFloorId(id)).thenReturn(true);

        var service = new FloorService(floors, mock(BuildingRepository.class), rooms, maps);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Karte");
        verify(floors, never()).delete(any());
    }
}
