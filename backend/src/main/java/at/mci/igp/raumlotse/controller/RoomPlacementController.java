package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.PlacementResponse;
import at.mci.igp.raumlotse.dto.PositionRequest;
import at.mci.igp.raumlotse.dto.RoomRefResponse;
import at.mci.igp.raumlotse.service.RoomPlacementService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomPlacementController {

    private final RoomPlacementService service;

    public RoomPlacementController(RoomPlacementService service) {
        this.service = service;
    }

    @GetMapping("/api/maps/{mapId}/unplaced-rooms")
    public List<RoomRefResponse> unplacedRooms(@PathVariable UUID mapId) {
        return service.unplacedRooms(mapId);
    }

    @PutMapping("/api/maps/{mapId}/placements/{roomId}")
    public ResponseEntity<PlacementResponse> place(@PathVariable UUID mapId, @PathVariable UUID roomId,
            @Valid @RequestBody PositionRequest position) {
        var result = service.place(mapId, roomId, position.x(), position.y());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.placement());
    }

    @DeleteMapping("/api/maps/{mapId}/placements/{roomId}")
    public ResponseEntity<Void> remove(@PathVariable UUID mapId, @PathVariable UUID roomId) {
        service.remove(mapId, roomId);
        return ResponseEntity.noContent().build();
    }
}
