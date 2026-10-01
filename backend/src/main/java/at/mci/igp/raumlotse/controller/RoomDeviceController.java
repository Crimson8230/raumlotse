package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.dto.RoomDeviceCommandRequest;
import at.mci.igp.raumlotse.dto.RoomDeviceControlsResponse;
import at.mci.igp.raumlotse.dto.RoomDeviceResponse;
import at.mci.igp.raumlotse.service.RoomDeviceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomDeviceController {
    private final RoomDeviceService service;
    public RoomDeviceController(RoomDeviceService service) { this.service = service; }
    @GetMapping("/api/rooms/{roomId}/device-controls")
    public RoomDeviceControlsResponse get(@PathVariable UUID roomId) { return service.getControls(roomId); }
    @PostMapping("/api/rooms/{roomId}/device-controls/{kind}")
    public RoomDeviceResponse set(@PathVariable UUID roomId, @PathVariable RoomDeviceKind kind,
            @Valid @RequestBody RoomDeviceCommandRequest request) { return service.setState(roomId, kind, request); }
}
