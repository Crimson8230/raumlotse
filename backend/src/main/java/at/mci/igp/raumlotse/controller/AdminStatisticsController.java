package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.AdminStatisticsResponse;
import at.mci.igp.raumlotse.service.AdminStatisticsService;
import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminStatisticsController {
    private final AdminStatisticsService service;

    public AdminStatisticsController(AdminStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/statistics")
    public ResponseEntity<AdminStatisticsResponse> getStatistics(
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getStatistics(from, to));
    }
}
