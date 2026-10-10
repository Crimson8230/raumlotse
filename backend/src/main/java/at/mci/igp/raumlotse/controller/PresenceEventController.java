package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.PresenceEventResponse;
import at.mci.igp.raumlotse.service.PresenceService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Simulated motion sensor (feature 014). Under /api/admin, so RoleAccessFilter admits administrators only. */
@RestController
public class PresenceEventController {
    private final PresenceService service;

    public PresenceEventController(PresenceService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/rooms/{roomId}/presence-events")
    public PresenceEventResponse simulateMotion(@PathVariable UUID roomId) {
        return service.recordMotion(roomId);
    }
}
