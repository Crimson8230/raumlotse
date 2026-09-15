package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.RoomUpdateRequest;
import at.mci.igp.raumlotse.dto.SeatingArrangementRequest;
import at.mci.igp.raumlotse.service.BuildingService;
import at.mci.igp.raumlotse.service.FloorService;
import at.mci.igp.raumlotse.service.RoomService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomConcurrentUpdateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RoomService roomService;

    @Autowired
    private BuildingService buildingService;

    @Autowired
    private FloorService floorService;

    /**
     * Simulates two administrators who both loaded the same room (same {@code version}) and
     * then save in sequence: the first save succeeds and bumps the version, the second save —
     * still carrying the stale version it originally read — must be rejected (FR-017,
     * Acceptance Scenario US3.3).
     */
    @Test
    void secondSaveBasedOnStaleVersionIsRejected() {
        var building = buildingService.create("Main");
        var floor = floorService.create(building.getId(), "1");
        Room created = roomService.create(new RoomCreateRequest(
                "Room 101", floor.getId(), List.of(new SeatingArrangementRequest("Theater", 40)), List.of()));
        Long staleVersion = created.getVersion();

        RoomUpdateRequest firstEdit = new RoomUpdateRequest(
                "Room 101 (renamed by admin A)",
                floor.getId(),
                List.of(new SeatingArrangementRequest("Theater", 40)),
                List.of(),
                staleVersion);
        Room afterFirstSave = roomService.update(created.getId(), firstEdit);
        assertThat(afterFirstSave.getVersion()).isNotEqualTo(staleVersion);

        RoomUpdateRequest secondEditBasedOnStaleRead = new RoomUpdateRequest(
                "Room 101 (renamed by admin B)",
                floor.getId(),
                List.of(new SeatingArrangementRequest("Theater", 40)),
                List.of(),
                staleVersion);

        assertThatThrownBy(() -> roomService.update(created.getId(), secondEditBasedOnStaleRead))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
