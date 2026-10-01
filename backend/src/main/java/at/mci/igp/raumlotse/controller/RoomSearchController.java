package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.RoomResponse;
import at.mci.igp.raumlotse.dto.RoomSearchRequest;
import at.mci.igp.raumlotse.service.RoomSearchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/** Read-only room search (feature 008); covered by the default "authenticated" rule in SecurityConfig. */
@RestController
public class RoomSearchController {

    private final RoomSearchService roomSearchService;

    public RoomSearchController(RoomSearchService roomSearchService) {
        this.roomSearchService = roomSearchService;
    }

    @GetMapping("/api/rooms/search")
    public List<RoomResponse> search(@Valid @ModelAttribute RoomSearchRequest request) {
        return roomSearchService.search(request.toCriteria());
    }

    @GetMapping("/api/rooms/search/seating-arrangements")
    public List<String> seatingArrangementNames() {
        return roomSearchService.listSeatingArrangementNames();
    }
}
