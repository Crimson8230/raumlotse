package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.AttachmentRequest;
import at.mci.igp.raumlotse.dto.ConnectionPointAttachmentResponse;
import at.mci.igp.raumlotse.dto.MapNetworkResponse;
import at.mci.igp.raumlotse.dto.NetworkWideResponse;
import at.mci.igp.raumlotse.dto.PositionRequest;
import at.mci.igp.raumlotse.dto.ReferenceSegmentRequest;
import at.mci.igp.raumlotse.dto.ReferenceSegmentResponse;
import at.mci.igp.raumlotse.dto.RoomAttachmentResponse;
import at.mci.igp.raumlotse.dto.SegmentRequest;
import at.mci.igp.raumlotse.dto.SegmentResponse;
import at.mci.igp.raumlotse.dto.SplitResultResponse;
import at.mci.igp.raumlotse.dto.WaypointResponse;
import at.mci.igp.raumlotse.service.MapScaleService;
import at.mci.igp.raumlotse.service.PathNetworkService;
import jakarta.validation.Valid;
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
public class PathNetworkController {

    private final PathNetworkService network;
    private final MapScaleService scale;

    public PathNetworkController(PathNetworkService network, MapScaleService scale) {
        this.network = network;
        this.scale = scale;
    }

    @GetMapping("/api/path-network")
    public NetworkWideResponse all() {
        return network.network();
    }

    @GetMapping("/api/maps/{mapId}/path-network")
    public MapNetworkResponse one(@PathVariable UUID mapId) {
        return network.mapNetwork(mapId);
    }

    @PostMapping("/api/maps/{mapId}/waypoints")
    public ResponseEntity<WaypointResponse> addWaypoint(@PathVariable UUID mapId,
            @Valid @RequestBody PositionRequest position) {
        return ResponseEntity.status(HttpStatus.CREATED).body(network.addWaypoint(mapId, position.x(), position.y()));
    }

    @PutMapping("/api/maps/{mapId}/waypoints/{waypointId}")
    public WaypointResponse moveWaypoint(@PathVariable UUID mapId, @PathVariable UUID waypointId,
            @Valid @RequestBody PositionRequest position) {
        return network.moveWaypoint(mapId, waypointId, position.x(), position.y());
    }

    @DeleteMapping("/api/maps/{mapId}/waypoints/{waypointId}")
    public ResponseEntity<Void> deleteWaypoint(@PathVariable UUID mapId, @PathVariable UUID waypointId) {
        network.deleteWaypoint(mapId, waypointId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/maps/{mapId}/segments")
    public ResponseEntity<SegmentResponse> addSegment(@PathVariable UUID mapId,
            @Valid @RequestBody SegmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(network.addSegment(mapId, request.fromWaypointId(), request.toWaypointId()));
    }

    @DeleteMapping("/api/maps/{mapId}/segments/{segmentId}")
    public ResponseEntity<Void> deleteSegment(@PathVariable UUID mapId, @PathVariable UUID segmentId) {
        network.deleteSegment(mapId, segmentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/maps/{mapId}/segments/{segmentId}/split")
    public ResponseEntity<SplitResultResponse> split(@PathVariable UUID mapId, @PathVariable UUID segmentId,
            @Valid @RequestBody PositionRequest position) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(network.splitSegment(mapId, segmentId, position.x(), position.y()));
    }

    @PutMapping("/api/maps/{mapId}/reference-segment")
    public ResponseEntity<ReferenceSegmentResponse> setReference(@PathVariable UUID mapId,
            @Valid @RequestBody ReferenceSegmentRequest request) {
        var result = scale.set(mapId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.reference());
    }

    @DeleteMapping("/api/maps/{mapId}/reference-segment")
    public ResponseEntity<Void> deleteReference(@PathVariable UUID mapId) {
        scale.delete(mapId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/maps/{mapId}/room-attachments/{roomId}")
    public ResponseEntity<RoomAttachmentResponse> attachRoom(@PathVariable UUID mapId, @PathVariable UUID roomId,
            @Valid @RequestBody AttachmentRequest request) {
        var result = network.attachRoom(mapId, roomId, request.waypointId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.attachment());
    }

    @DeleteMapping("/api/maps/{mapId}/room-attachments/{roomId}")
    public ResponseEntity<Void> removeRoomAttachment(@PathVariable UUID mapId, @PathVariable UUID roomId) {
        network.removeRoomAttachment(mapId, roomId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/maps/{mapId}/connection-point-attachments/{connectionPointId}")
    public ResponseEntity<ConnectionPointAttachmentResponse> attachConnectionPoint(@PathVariable UUID mapId,
            @PathVariable UUID connectionPointId, @Valid @RequestBody AttachmentRequest request) {
        var result = network.attachConnectionPoint(mapId, connectionPointId, request.waypointId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.attachment());
    }

    @DeleteMapping("/api/maps/{mapId}/connection-point-attachments/{connectionPointId}")
    public ResponseEntity<Void> removeConnectionPointAttachment(@PathVariable UUID mapId,
            @PathVariable UUID connectionPointId) {
        network.removeConnectionPointAttachment(mapId, connectionPointId);
        return ResponseEntity.noContent().build();
    }
}
