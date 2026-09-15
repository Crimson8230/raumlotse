package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.RoomResponse;
import at.mci.igp.raumlotse.dto.RoomUpdateRequest;
import at.mci.igp.raumlotse.service.RoomService;
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
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> list(@RequestParam(required = false) String status) {
        return roomService.list(parseListFilter(status)).stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/{roomId}")
    public RoomResponse get(@PathVariable UUID roomId) {
        return RoomResponse.from(roomService.get(roomId));
    }

    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomCreateRequest request) {
        var created = roomService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(created));
    }

    @PutMapping("/{roomId}")
    public RoomResponse update(@PathVariable UUID roomId, @Valid @RequestBody RoomUpdateRequest request) {
        return RoomResponse.from(roomService.update(roomId, request));
    }

    @PostMapping("/{roomId}/deactivate")
    public RoomResponse deactivate(@PathVariable UUID roomId) {
        return RoomResponse.from(roomService.deactivate(roomId));
    }

    @PostMapping("/{roomId}/reactivate")
    public RoomResponse reactivate(@PathVariable UUID roomId) {
        return RoomResponse.from(roomService.reactivate(roomId));
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> delete(@PathVariable UUID roomId) {
        roomService.delete(roomId);
        return ResponseEntity.noContent().build();
    }

    /** Unlike the catalogs, Room defaults to "active" when the status filter is omitted (FR-011). */
    private EntityStatus parseListFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return EntityStatus.ACTIVE;
        }
        if ("all".equalsIgnoreCase(raw)) {
            return null;
        }
        return EntityStatus.valueOf(raw.toUpperCase());
    }
}
