package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.FloorRequest;
import at.mci.igp.raumlotse.dto.FloorResponse;
import at.mci.igp.raumlotse.service.FloorService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FloorController {

    private final FloorService floorService;

    public FloorController(FloorService floorService) {
        this.floorService = floorService;
    }

    @GetMapping("/api/buildings/{buildingId}/floors")
    public List<FloorResponse> listByBuilding(@PathVariable UUID buildingId, @RequestParam(required = false) String status) {
        return floorService.listByBuilding(buildingId, StatusFilter.parse(status)).stream()
                .map(FloorResponse::from)
                .toList();
    }

    @PostMapping("/api/buildings/{buildingId}/floors")
    public ResponseEntity<FloorResponse> create(@PathVariable UUID buildingId, @Valid @RequestBody FloorRequest request) {
        var created = floorService.create(buildingId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(FloorResponse.from(created));
    }

    @PutMapping("/api/floors/{floorId}")
    public FloorResponse rename(@PathVariable UUID floorId, @Valid @RequestBody FloorRequest request) {
        return FloorResponse.from(floorService.rename(floorId, request.name()));
    }

    @PostMapping("/api/floors/{floorId}/deactivate")
    public FloorResponse deactivate(@PathVariable UUID floorId) {
        return FloorResponse.from(floorService.deactivate(floorId));
    }

    @PostMapping("/api/floors/{floorId}/reactivate")
    public FloorResponse reactivate(@PathVariable UUID floorId) {
        return FloorResponse.from(floorService.reactivate(floorId));
    }

    @DeleteMapping("/api/floors/{floorId}")
    public ResponseEntity<Void> delete(@PathVariable UUID floorId) {
        floorService.delete(floorId);
        return ResponseEntity.noContent().build();
    }
}
