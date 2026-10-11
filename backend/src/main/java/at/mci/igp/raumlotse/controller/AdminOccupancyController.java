package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.AdminOccupancyResponse;
import at.mci.igp.raumlotse.service.AdminOccupancyService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminOccupancyController {
    private final AdminOccupancyService service;

    public AdminOccupancyController(AdminOccupancyService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/occupancy")
    public ResponseEntity<AdminOccupancyResponse.Snapshot> getSnapshot(@RequestParam(required = false) UUID buildingId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.snapshot(buildingId));
    }

    @GetMapping("/api/admin/occupancy/forecast")
    public ResponseEntity<AdminOccupancyResponse.Forecast> getForecast(
            @RequestParam LocalDate date, @RequestParam(required = false) UUID buildingId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.forecast(date, buildingId));
    }
}
