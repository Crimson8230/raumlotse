package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.BuildingRequest;
import at.mci.igp.raumlotse.dto.BuildingResponse;
import at.mci.igp.raumlotse.service.BuildingService;
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
@RequestMapping("/api/buildings")
public class BuildingController {

    private final BuildingService buildingService;

    public BuildingController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @GetMapping
    public List<BuildingResponse> list(@RequestParam(required = false) String status) {
        return buildingService.list(StatusFilter.parse(status)).stream()
                .map(BuildingResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<BuildingResponse> create(@Valid @RequestBody BuildingRequest request) {
        var created = buildingService.create(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(BuildingResponse.from(created));
    }

    @PutMapping("/{buildingId}")
    public BuildingResponse rename(@PathVariable UUID buildingId, @Valid @RequestBody BuildingRequest request) {
        return BuildingResponse.from(buildingService.rename(buildingId, request.name()));
    }

    @PostMapping("/{buildingId}/deactivate")
    public BuildingResponse deactivate(@PathVariable UUID buildingId) {
        return BuildingResponse.from(buildingService.deactivate(buildingId));
    }

    @PostMapping("/{buildingId}/reactivate")
    public BuildingResponse reactivate(@PathVariable UUID buildingId) {
        return BuildingResponse.from(buildingService.reactivate(buildingId));
    }

    @DeleteMapping("/{buildingId}")
    public ResponseEntity<Void> delete(@PathVariable UUID buildingId) {
        buildingService.delete(buildingId);
        return ResponseEntity.noContent().build();
    }
}
