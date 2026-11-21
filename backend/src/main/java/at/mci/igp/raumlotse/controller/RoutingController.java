package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.NeighborsResponse;
import at.mci.igp.raumlotse.dto.RouteResponse;
import at.mci.igp.raumlotse.service.RoutingService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only routing endpoints (feature 024); nothing here changes data. */
@RestController
public class RoutingController {

    private final RoutingService routing;

    public RoutingController(RoutingService routing) {
        this.routing = routing;
    }

    @GetMapping("/api/routes")
    public RouteResponse route(@RequestParam UUID fromRoomId, @RequestParam UUID toRoomId,
            @RequestParam(defaultValue = "false") boolean accessibleOnly) {
        return routing.route(fromRoomId, toRoomId, accessibleOnly);
    }

    @GetMapping("/api/rooms/{roomId}/neighbors")
    public NeighborsResponse neighbors(@PathVariable UUID roomId) {
        return routing.neighbors(roomId);
    }
}
