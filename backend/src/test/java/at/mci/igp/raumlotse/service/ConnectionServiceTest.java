package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Connection;
import at.mci.igp.raumlotse.domain.ConnectionType;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.FloorMap;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ConnectionRepository;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
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
class ConnectionServiceTest {

    @Mock ConnectionRepository connections;
    @Mock FloorMapRepository maps;

    ConnectionService service;
    FloorMap map0;
    FloorMap map1;
    FloorMap map2;
    Connection elevator;

    static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    FloorMap map(String floorName) {
        var floor = withId(new Floor(withId(new Building("Haus A")), floorName));
        return withId(new FloorMap(floor, "image/png", 100, 50));
    }

    @BeforeEach
    void setUp() {
        service = new ConnectionService(connections, maps);
        map0 = map("EG");
        map1 = map("1. OG");
        map2 = map("2. OG");
        elevator = withId(new Connection("Aufzug A", ConnectionType.ELEVATOR));
        for (FloorMap m : new FloorMap[] { map0, map1, map2 }) {
            when(maps.findById(m.getId())).thenReturn(Optional.of(m));
        }
        when(connections.findById(elevator.getId())).thenReturn(Optional.of(elevator));
        when(connections.save(any(Connection.class))).thenAnswer(inv -> {
            Connection c = inv.getArgument(0);
            if (c.getId() == null) {
                ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            }
            return c;
        });
    }

    @Test
    void createsConnectionWithTrimmedName() {
        when(connections.existsByNameIgnoreCase("Treppe Nord")).thenReturn(false);
        var created = service.create("  Treppe Nord ", ConnectionType.STAIRS);
        assertThat(created.getName()).isEqualTo("Treppe Nord");
        assertThat(created.getType()).isEqualTo(ConnectionType.STAIRS);
        assertThat(created.isIncomplete()).isTrue();
    }

    @Test
    void duplicateNameIgnoringCaseIsAConflict() {
        when(connections.existsByNameIgnoreCase("aufzug a")).thenReturn(true);
        assertThatThrownBy(() -> service.create("aufzug a", ConnectionType.ELEVATOR))
                .isInstanceOf(ConflictException.class);
        verify(connections, never()).save(any());
    }

    @Test
    void renameAndRetypeChecksUniquenessAgainstOthers() {
        when(connections.existsByNameIgnoreCaseAndIdNot("Aufzug B", elevator.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.update(elevator.getId(), "Aufzug B", ConnectionType.ELEVATOR))
                .isInstanceOf(ConflictException.class);

        when(connections.existsByNameIgnoreCaseAndIdNot("Aufzug C", elevator.getId())).thenReturn(false);
        var updated = service.update(elevator.getId(), "Aufzug C", ConnectionType.STAIRS);
        assertThat(updated.getName()).isEqualTo("Aufzug C");
        assertThat(updated.getType()).isEqualTo(ConnectionType.STAIRS);
    }

    @Test
    void firstPointCreatesSecondPointOnAnotherMapCompletesTheConnection() {
        var first = service.putPoint(elevator.getId(), map0.getId(), 0.2, 0.3);
        assertThat(first.created()).isTrue();
        assertThat(first.connection().incomplete()).isTrue();

        var second = service.putPoint(elevator.getId(), map1.getId(), 0.6, 0.7);
        assertThat(second.created()).isTrue();
        assertThat(second.connection().incomplete()).isFalse();
        assertThat(second.connection().points()).hasSize(2);
    }

    @Test
    void secondPointOnTheSameMapMovesTheExistingOne() {
        service.putPoint(elevator.getId(), map0.getId(), 0.2, 0.3);
        var moved = service.putPoint(elevator.getId(), map0.getId(), 0.9, 0.1);

        assertThat(moved.created()).isFalse();
        assertThat(moved.connection().points()).hasSize(1);
        assertThat(moved.connection().points().get(0).x()).isEqualTo(0.9);
    }

    @Test
    void thirdMapExtendsTheConnection() {
        service.putPoint(elevator.getId(), map0.getId(), 0.1, 0.1);
        service.putPoint(elevator.getId(), map1.getId(), 0.1, 0.1);
        var third = service.putPoint(elevator.getId(), map2.getId(), 0.1, 0.1);
        assertThat(third.connection().points()).hasSize(3);
        assertThat(third.connection().incomplete()).isFalse();
    }

    @Test
    void removingAPointLeavesTheConnectionIncompleteWhenFewerThanTwoRemain() {
        service.putPoint(elevator.getId(), map0.getId(), 0.1, 0.1);
        service.putPoint(elevator.getId(), map1.getId(), 0.1, 0.1);

        service.removePoint(elevator.getId(), map1.getId());

        assertThat(elevator.getPoints()).hasSize(1);
        assertThat(elevator.isIncomplete()).isTrue();
    }

    @Test
    void removingAPointThatDoesNotExistIsNotFound() {
        assertThatThrownBy(() -> service.removePoint(elevator.getId(), map0.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void coordinatesOutsideUnitSquareAreRejected() {
        assertThatThrownBy(() -> service.putPoint(elevator.getId(), map0.getId(), 1.1, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.putPoint(elevator.getId(), map0.getId(), 0.5, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(elevator.getPoints()).isEmpty();
    }

    @Test
    void unknownConnectionOrMapIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(connections.findById(unknown)).thenReturn(Optional.empty());
        when(maps.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.putPoint(unknown, map0.getId(), 0.1, 0.1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.putPoint(elevator.getId(), unknown, 0.1, 0.1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(unknown)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesTheConnection() {
        service.delete(elevator.getId());
        verify(connections).delete(elevator);
    }
}
