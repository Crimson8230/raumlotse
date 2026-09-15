package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.EquipmentTypeRequest;
import at.mci.igp.raumlotse.dto.EquipmentTypeResponse;
import at.mci.igp.raumlotse.service.EquipmentTypeService;
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
@RequestMapping("/api/equipment-types")
public class EquipmentTypeController {

    private final EquipmentTypeService equipmentTypeService;

    public EquipmentTypeController(EquipmentTypeService equipmentTypeService) {
        this.equipmentTypeService = equipmentTypeService;
    }

    @GetMapping
    public List<EquipmentTypeResponse> list(@RequestParam(required = false) String status) {
        return equipmentTypeService.list(StatusFilter.parse(status)).stream()
                .map(EquipmentTypeResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<EquipmentTypeResponse> create(@Valid @RequestBody EquipmentTypeRequest request) {
        var created = equipmentTypeService.create(request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(EquipmentTypeResponse.from(created));
    }

    @PutMapping("/{equipmentTypeId}")
    public EquipmentTypeResponse rename(@PathVariable UUID equipmentTypeId, @Valid @RequestBody EquipmentTypeRequest request) {
        return EquipmentTypeResponse.from(equipmentTypeService.rename(equipmentTypeId, request.name()));
    }

    @PostMapping("/{equipmentTypeId}/deactivate")
    public EquipmentTypeResponse deactivate(@PathVariable UUID equipmentTypeId) {
        return EquipmentTypeResponse.from(equipmentTypeService.deactivate(equipmentTypeId));
    }

    @PostMapping("/{equipmentTypeId}/reactivate")
    public EquipmentTypeResponse reactivate(@PathVariable UUID equipmentTypeId) {
        return EquipmentTypeResponse.from(equipmentTypeService.reactivate(equipmentTypeId));
    }

    @DeleteMapping("/{equipmentTypeId}")
    public ResponseEntity<Void> delete(@PathVariable UUID equipmentTypeId) {
        equipmentTypeService.delete(equipmentTypeId);
        return ResponseEntity.noContent().build();
    }
}
