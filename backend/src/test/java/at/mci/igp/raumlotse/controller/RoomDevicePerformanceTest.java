package at.mci.igp.raumlotse.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.dto.RoomDeviceControlsResponse;
import at.mci.igp.raumlotse.service.RoomDeviceService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoomDevicePerformanceTest {
    @Test
    void controllerCapabilityDispatchStaysWithinResponseBudget() {
        RoomDeviceService service = mock(RoomDeviceService.class);
        RoomDeviceController controller = new RoomDeviceController(service);
        UUID roomId = UUID.randomUUID();
        when(service.getControls(roomId)).thenReturn(new RoomDeviceControlsResponse(roomId, UUID.randomUUID(), List.of()));

        long started = System.nanoTime();
        for (int i = 0; i < 100; i++) controller.get(roomId);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(1000);
    }
}
