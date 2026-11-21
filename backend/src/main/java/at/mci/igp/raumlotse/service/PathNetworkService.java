package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Connection;
import at.mci.igp.raumlotse.domain.ConnectionPoint;
import at.mci.igp.raumlotse.domain.ConnectionPointPathAttachment;
import at.mci.igp.raumlotse.domain.FloorMap;
import at.mci.igp.raumlotse.domain.MapReferenceSegment;
import at.mci.igp.raumlotse.domain.PathSegment;
import at.mci.igp.raumlotse.domain.PathWaypoint;
import at.mci.igp.raumlotse.domain.RoomPathAttachment;
import at.mci.igp.raumlotse.domain.RoomPlacement;
import at.mci.igp.raumlotse.dto.ConnectionGroupResponse;
import at.mci.igp.raumlotse.dto.ConnectionPointAttachmentResponse;
import at.mci.igp.raumlotse.dto.MapNetworkResponse;
import at.mci.igp.raumlotse.dto.NetworkWideResponse;
import at.mci.igp.raumlotse.dto.RoomAttachmentResponse;
import at.mci.igp.raumlotse.dto.ScaleResponse;
import at.mci.igp.raumlotse.dto.SegmentResponse;
import at.mci.igp.raumlotse.dto.SplitResultResponse;
import at.mci.igp.raumlotse.dto.WaypointResponse;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.MapRequestException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ConnectionPointPathAttachmentRepository;
import at.mci.igp.raumlotse.repository.ConnectionPointRepository;
import at.mci.igp.raumlotse.repository.ConnectionRepository;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.MapReferenceSegmentRepository;
import at.mci.igp.raumlotse.repository.PathSegmentRepository;
import at.mci.igp.raumlotse.repository.PathWaypointRepository;
import at.mci.igp.raumlotse.repository.RoomPathAttachmentRepository;
import at.mci.igp.raumlotse.repository.RoomPlacementRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits and reads the walkway network (waypoints, segments, attachments) of floor maps. */
@Service
@Transactional
public class PathNetworkService {

    public record AttachResult<T>(T attachment, boolean created) {
    }

    private static final Logger log = LoggerFactory.getLogger(PathNetworkService.class);

    private final FloorMapRepository maps;
    private final PathWaypointRepository waypoints;
    private final PathSegmentRepository segments;
    private final MapReferenceSegmentRepository references;
    private final RoomPathAttachmentRepository roomAttachments;
    private final ConnectionPointPathAttachmentRepository pointAttachments;
    private final RoomPlacementRepository placements;
    private final RoomRepository rooms;
    private final ConnectionPointRepository connectionPoints;
    private final ConnectionRepository connections;
    private final PathNetworkIssueChecker issueChecker;

    public PathNetworkService(FloorMapRepository maps, PathWaypointRepository waypoints,
            PathSegmentRepository segments, MapReferenceSegmentRepository references,
            RoomPathAttachmentRepository roomAttachments, ConnectionPointPathAttachmentRepository pointAttachments,
            RoomPlacementRepository placements, RoomRepository rooms, ConnectionPointRepository connectionPoints,
            ConnectionRepository connections, PathNetworkIssueChecker issueChecker) {
        this.maps = maps;
        this.waypoints = waypoints;
        this.segments = segments;
        this.references = references;
        this.roomAttachments = roomAttachments;
        this.pointAttachments = pointAttachments;
        this.placements = placements;
        this.rooms = rooms;
        this.connectionPoints = connectionPoints;
        this.connections = connections;
        this.issueChecker = issueChecker;
    }

    // ---- waypoints

    public WaypointResponse addWaypoint(UUID mapId, double x, double y) {
        requireFraction(x, "x");
        requireFraction(y, "y");
        requireMap(mapId);
        requireNoCoincidingWaypoint(mapId, x, y, null);
        return WaypointResponse.from(waypoints.saveAndFlush(new PathWaypoint(mapId, x, y)));
    }

    public WaypointResponse moveWaypoint(UUID mapId, UUID waypointId, double x, double y) {
        requireFraction(x, "x");
        requireFraction(y, "y");
        PathWaypoint waypoint = requireWaypointOnMap(mapId, waypointId);
        requireNoCoincidingWaypoint(mapId, x, y, waypointId);
        waypoint.moveTo(x, y);
        return WaypointResponse.from(waypoints.saveAndFlush(waypoint));
    }

    public void deleteWaypoint(UUID mapId, UUID waypointId) {
        PathWaypoint waypoint = requireWaypointOnMap(mapId, waypointId);
        waypoints.delete(waypoint);
        waypoints.flush();
    }

    // ---- segments

    public SegmentResponse addSegment(UUID mapId, UUID fromId, UUID toId) {
        FloorMap map = requireMap(mapId);
        if (fromId.equals(toId)) {
            reject("SEGMENT_SELF_LOOP", mapId);
            throw new IllegalArgumentException("Ein Weg braucht zwei verschiedene Wegpunkte.");
        }
        PathWaypoint from = requireWaypointForSegment(mapId, fromId);
        PathWaypoint to = requireWaypointForSegment(mapId, toId);
        if (PathGeometry.coincide(from.getX(), from.getY(), to.getX(), to.getY())) {
            reject("SEGMENT_ZERO_LENGTH", mapId);
            throw new ConflictException("Die Wegpunkte liegen am selben Punkt; ein Weg hätte die Länge 0.",
                    "SEGMENT_ZERO_LENGTH");
        }
        UUID[] pair = PathGeometry.orderedPair(fromId, toId);
        if (segments.existsByAIdAndBId(pair[0], pair[1])) {
            reject("SEGMENT_EXISTS", mapId);
            throw new ConflictException("Zwischen diesen Wegpunkten gibt es bereits einen Weg.", "SEGMENT_EXISTS");
        }
        PathSegment segment = segments.saveAndFlush(new PathSegment(mapId, pair[0], pair[1]));
        ScaleResponse scale = MapScaleService.scaleOf(map, references.findById(mapId).orElse(null));
        return toResponse(segment, Map.of(from.getId(), from, to.getId(), to), scale);
    }

    public void deleteSegment(UUID mapId, UUID segmentId) {
        segments.delete(requireSegmentOnMap(mapId, segmentId));
        segments.flush();
    }

    /** Inserts a waypoint on the segment (position projected onto it) and replaces it by two segments. */
    public SplitResultResponse splitSegment(UUID mapId, UUID segmentId, double x, double y) {
        requireFraction(x, "x");
        requireFraction(y, "y");
        FloorMap map = requireMap(mapId);
        PathSegment segment = requireSegmentOnMap(mapId, segmentId);
        PathWaypoint a = waypoints.findById(segment.getAId()).orElseThrow();
        PathWaypoint b = waypoints.findById(segment.getBId()).orElseThrow();
        var projection = PathGeometry.project(x, y, a.getX(), a.getY(), b.getX(), b.getY());
        requireNoCoincidingWaypoint(mapId, projection.x(), projection.y(), null);
        segments.delete(segment);
        segments.flush();
        PathWaypoint inserted = waypoints.saveAndFlush(new PathWaypoint(mapId, projection.x(), projection.y()));
        Map<UUID, PathWaypoint> byId = Map.of(a.getId(), a, b.getId(), b, inserted.getId(), inserted);
        ScaleResponse scale = MapScaleService.scaleOf(map, references.findById(mapId).orElse(null));
        List<SegmentResponse> created = List.of(a, b).stream().map(end -> {
            UUID[] pair = PathGeometry.orderedPair(end.getId(), inserted.getId());
            return toResponse(segments.saveAndFlush(new PathSegment(mapId, pair[0], pair[1])), byId, scale);
        }).toList();
        return new SplitResultResponse(WaypointResponse.from(inserted), created);
    }

    // ---- attachments

    public AttachResult<RoomAttachmentResponse> attachRoom(UUID mapId, UUID roomId, UUID waypointId) {
        requireMap(mapId);
        if (!rooms.existsById(roomId)) {
            throw new NotFoundException("Raum " + roomId + " nicht gefunden.");
        }
        placements.findById(roomId).filter(p -> p.getMap().getId().equals(mapId)).orElseThrow(() -> {
            reject("ROOM_NOT_PLACED_ON_MAP", mapId);
            return new MapRequestException(HttpStatus.UNPROCESSABLE_CONTENT, "ROOM_NOT_PLACED_ON_MAP",
                    "Der Raum ist nicht auf dieser Karte platziert.");
        });
        requireWaypointForAttachment(mapId, waypointId);
        boolean existed = roomAttachments.existsById(roomId);
        roomAttachments.upsert(roomId, mapId, waypointId);
        return new AttachResult<>(RoomAttachmentResponse.from(roomAttachments.findById(roomId).orElseThrow()),
                !existed);
    }

    public void removeRoomAttachment(UUID mapId, UUID roomId) {
        var attachment = roomAttachments.findById(roomId).filter(a -> a.getMapId().equals(mapId))
                .orElseThrow(() -> new NotFoundException("Raum " + roomId + " ist auf Karte " + mapId
                        + " nicht angebunden."));
        roomAttachments.delete(attachment);
    }

    public AttachResult<ConnectionPointAttachmentResponse> attachConnectionPoint(UUID mapId, UUID pointId,
            UUID waypointId) {
        requireMap(mapId);
        ConnectionPoint point = connectionPoints.findById(pointId)
                .orElseThrow(() -> new NotFoundException("Verbindungspunkt " + pointId + " nicht gefunden."));
        if (!point.getMap().getId().equals(mapId)) {
            reject("POINT_NOT_ON_MAP", mapId);
            throw new MapRequestException(HttpStatus.UNPROCESSABLE_CONTENT, "POINT_NOT_ON_MAP",
                    "Der Verbindungspunkt liegt nicht auf dieser Karte.");
        }
        requireWaypointForAttachment(mapId, waypointId);
        boolean existed = pointAttachments.existsById(pointId);
        pointAttachments.upsert(pointId, mapId, waypointId);
        return new AttachResult<>(
                ConnectionPointAttachmentResponse.from(pointAttachments.findById(pointId).orElseThrow()), !existed);
    }

    public void removeConnectionPointAttachment(UUID mapId, UUID pointId) {
        var attachment = pointAttachments.findById(pointId).filter(a -> a.getMapId().equals(mapId))
                .orElseThrow(() -> new NotFoundException("Verbindungspunkt " + pointId + " ist auf Karte " + mapId
                        + " nicht angebunden."));
        pointAttachments.delete(attachment);
    }

    // ---- read model

    @Transactional(readOnly = true)
    public MapNetworkResponse mapNetwork(UUID mapId) {
        FloorMap map = requireMap(mapId);
        return build(map, waypoints.findByMapId(mapId), segments.findByMapId(mapId),
                references.findById(mapId).orElse(null), placements.findByMapId(mapId),
                roomAttachments.findByMapId(mapId), connectionPoints.findByMapId(mapId),
                pointAttachments.findByMapId(mapId));
    }

    @Transactional(readOnly = true)
    public NetworkWideResponse network() {
        var allWaypoints = waypoints.findAll().stream().collect(Collectors.groupingBy(PathWaypoint::getMapId));
        var allSegments = segments.findAll().stream().collect(Collectors.groupingBy(PathSegment::getMapId));
        var allReferences = references.findAll().stream()
                .collect(Collectors.toMap(MapReferenceSegment::getMapId, r -> r));
        var allPlacements = placements.findAll().stream().collect(Collectors.groupingBy(p -> p.getMap().getId()));
        var allRoomAttachments = roomAttachments.findAll().stream()
                .collect(Collectors.groupingBy(RoomPathAttachment::getMapId));
        var allPoints = connectionPoints.findAll();
        var pointsByMap = allPoints.stream().collect(Collectors.groupingBy(p -> p.getMap().getId()));
        var allPointAttachments = pointAttachments.findAll();
        var pointAttachmentsByMap = allPointAttachments.stream()
                .collect(Collectors.groupingBy(ConnectionPointPathAttachment::getMapId));
        List<MapNetworkResponse> result = maps.findAllWithFloor().stream().map(map -> build(map,
                allWaypoints.getOrDefault(map.getId(), List.of()), allSegments.getOrDefault(map.getId(), List.of()),
                allReferences.get(map.getId()), allPlacements.getOrDefault(map.getId(), List.of()),
                allRoomAttachments.getOrDefault(map.getId(), List.of()),
                pointsByMap.getOrDefault(map.getId(), List.of()),
                pointAttachmentsByMap.getOrDefault(map.getId(), List.of()))).toList();
        Map<UUID, UUID> attachedWaypoint = allPointAttachments.stream().collect(
                Collectors.toMap(ConnectionPointPathAttachment::getConnectionPointId,
                        ConnectionPointPathAttachment::getWaypointId));
        List<ConnectionGroupResponse> groups = connections.findAllWithPoints().stream()
                .map(connection -> group(connection, attachedWaypoint)).toList();
        return new NetworkWideResponse(result, groups);
    }

    private static ConnectionGroupResponse group(Connection connection, Map<UUID, UUID> attachedWaypoint) {
        List<ConnectionGroupResponse.Point> points = connection.getPoints().stream()
                .map(p -> new ConnectionGroupResponse.Point(p.getId(), p.getMap().getId(),
                        attachedWaypoint.get(p.getId())))
                .sorted(Comparator.comparing(p -> p.connectionPointId().toString())).toList();
        boolean fullyAttached = !points.isEmpty() && points.stream().allMatch(p -> p.waypointId() != null);
        return new ConnectionGroupResponse(connection.getId(), connection.getName(), connection.getType().name(),
                fullyAttached, points);
    }

    private MapNetworkResponse build(FloorMap map, List<PathWaypoint> mapWaypoints, List<PathSegment> mapSegments,
            MapReferenceSegment reference, List<RoomPlacement> mapPlacements, List<RoomPathAttachment> mapRoomAtt,
            List<ConnectionPoint> mapPoints, List<ConnectionPointPathAttachment> mapPointAtt) {
        ScaleResponse scale = MapScaleService.scaleOf(map, reference);
        Map<UUID, PathWaypoint> byId = new HashMap<>();
        mapWaypoints.forEach(w -> byId.put(w.getId(), w));
        var waypointResponses = mapWaypoints.stream().sorted(Comparator.comparing(w -> w.getId().toString()))
                .map(WaypointResponse::from).toList();
        var segmentResponses = mapSegments.stream().sorted(Comparator.comparing(s -> s.getId().toString()))
                .map(s -> toResponse(s, byId, scale)).toList();
        var roomAttResponses = mapRoomAtt.stream().sorted(Comparator.comparing(a -> a.getRoomId().toString()))
                .map(RoomAttachmentResponse::from).toList();
        var pointAttResponses = mapPointAtt.stream()
                .sorted(Comparator.comparing(a -> a.getConnectionPointId().toString()))
                .map(ConnectionPointAttachmentResponse::from).toList();
        Set<UUID> placedRooms = mapPlacements.stream().map(RoomPlacement::getRoomId).collect(Collectors.toSet());
        Set<UUID> pointIds = mapPoints.stream().map(ConnectionPoint::getId).collect(Collectors.toSet());
        var issues = issueChecker.check(new PathNetworkIssueChecker.Input(scale.available(), mapWaypoints,
                mapSegments, placedRooms,
                mapRoomAtt.stream().collect(Collectors.toMap(RoomPathAttachment::getRoomId,
                        RoomPathAttachment::getWaypointId)),
                pointIds,
                mapPointAtt.stream().collect(Collectors.toMap(ConnectionPointPathAttachment::getConnectionPointId,
                        ConnectionPointPathAttachment::getWaypointId))));
        return new MapNetworkResponse(map.getId(), scale,
                reference == null ? null : MapScaleService.toResponse(map, reference), waypointResponses,
                segmentResponses, roomAttResponses, pointAttResponses, issues);
    }

    // ---- helpers

    private static SegmentResponse toResponse(PathSegment segment, Map<UUID, PathWaypoint> byId, ScaleResponse scale) {
        PathWaypoint a = byId.get(segment.getAId());
        PathWaypoint b = byId.get(segment.getBId());
        Double length = a == null || b == null ? null
                : MapScaleService.lengthMeters(scale, a.getX(), a.getY(), b.getX(), b.getY());
        return SegmentResponse.from(segment, length);
    }

    private FloorMap requireMap(UUID mapId) {
        return maps.findById(mapId).orElseThrow(() -> new NotFoundException("Karte " + mapId + " nicht gefunden."));
    }

    private PathWaypoint requireWaypointOnMap(UUID mapId, UUID waypointId) {
        requireMap(mapId);
        return waypoints.findById(waypointId).filter(w -> w.getMapId().equals(mapId))
                .orElseThrow(() -> new NotFoundException("Wegpunkt " + waypointId + " ist nicht auf Karte " + mapId
                        + "."));
    }

    private PathSegment requireSegmentOnMap(UUID mapId, UUID segmentId) {
        requireMap(mapId);
        return segments.findById(segmentId).filter(s -> s.getMapId().equals(mapId))
                .orElseThrow(() -> new NotFoundException("Weg " + segmentId + " ist nicht auf Karte " + mapId + "."));
    }

    private PathWaypoint requireWaypointForSegment(UUID mapId, UUID waypointId) {
        return requireSameMapWaypoint(mapId, waypointId, "WAYPOINT_MAP_MISMATCH");
    }

    private PathWaypoint requireWaypointForAttachment(UUID mapId, UUID waypointId) {
        return requireSameMapWaypoint(mapId, waypointId, "WAYPOINT_MAP_MISMATCH");
    }

    private PathWaypoint requireSameMapWaypoint(UUID mapId, UUID waypointId, String code) {
        PathWaypoint waypoint = waypoints.findById(waypointId)
                .orElseThrow(() -> new NotFoundException("Wegpunkt " + waypointId + " nicht gefunden."));
        if (!waypoint.getMapId().equals(mapId)) {
            reject(code, mapId);
            throw new MapRequestException(HttpStatus.UNPROCESSABLE_CONTENT, code,
                    "Der Wegpunkt gehört zu einer anderen Karte.");
        }
        return waypoint;
    }

    private void requireNoCoincidingWaypoint(UUID mapId, double x, double y, UUID ignoreId) {
        for (PathWaypoint other : waypoints.findByMapId(mapId)) {
            if (!other.getId().equals(ignoreId) && PathGeometry.coincide(x, y, other.getX(), other.getY())) {
                reject("WAYPOINT_COINCIDES", mapId);
                throw new ConflictException("An dieser Stelle liegt bereits ein Wegpunkt (id=" + other.getId()
                        + "). Bitte diesen verwenden.", "WAYPOINT_COINCIDES");
            }
        }
    }

    private static void requireFraction(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Position " + name + " muss zwischen 0 und 1 liegen.");
        }
    }

    private static void reject(String code, UUID mapId) {
        log.warn("path_network_rejected code={} mapId={}", code, mapId);
    }
}
