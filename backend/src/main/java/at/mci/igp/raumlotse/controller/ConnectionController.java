package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.ConnectionRequest;
import at.mci.igp.raumlotse.dto.ConnectionResponse;
import at.mci.igp.raumlotse.dto.PositionRequest;
import at.mci.igp.raumlotse.service.ConnectionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectionController {

    private final ConnectionService service;

    public ConnectionController(ConnectionService service) {
        this.service = service;
    }

    @GetMapping("/api/connections")
    public List<ConnectionResponse> list() {
        return service.list();
    }

    @PostMapping("/api/connections")
    public ResponseEntity<ConnectionResponse> create(@Valid @RequestBody ConnectionRequest request) {
        var created = service.create(request.name(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConnectionResponse.from(created));
    }

    @PutMapping("/api/connections/{connectionId}")
    public ConnectionResponse update(@PathVariable UUID connectionId, @Valid @RequestBody ConnectionRequest request) {
        return ConnectionResponse.from(service.update(connectionId, request.name(), request.type()));
    }

    @DeleteMapping("/api/connections/{connectionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID connectionId) {
        service.delete(connectionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/connections/{connectionId}/points/{mapId}")
    public ResponseEntity<ConnectionResponse> putPoint(@PathVariable UUID connectionId, @PathVariable UUID mapId,
            @Valid @RequestBody PositionRequest position) {
        var result = service.putPoint(connectionId, mapId, position.x(), position.y());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.connection());
    }

    @DeleteMapping("/api/connections/{connectionId}/points/{mapId}")
    public ResponseEntity<Void> removePoint(@PathVariable UUID connectionId, @PathVariable UUID mapId) {
        service.removePoint(connectionId, mapId);
        return ResponseEntity.noContent().build();
    }
}
