package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.FloorMap;
import at.mci.igp.raumlotse.domain.RoomPlacement;
import at.mci.igp.raumlotse.dto.MapNetworkResponse;
import at.mci.igp.raumlotse.dto.NeighborEntryResponse;
import at.mci.igp.raumlotse.dto.NeighborsResponse;
import at.mci.igp.raumlotse.dto.NetworkWideResponse;
import at.mci.igp.raumlotse.dto.RoomAttachmentResponse;
import at.mci.igp.raumlotse.dto.RouteResponse;
import at.mci.igp.raumlotse.dto.RouteStepResponse;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.RoomPlacementRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only routing: neighbors of a room and routes between rooms over the path network (feature 024). */
@Service
@Transactional(readOnly = true)
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RoomRepository rooms;
    private final PathNetworkService network;
    private final FloorMapRepository maps;
    private final RoomPlacementRepository placements;
    private final RoomStatusService statuses;

    public RoutingService(RoomRepository rooms, PathNetworkService network, FloorMapRepository maps,
            RoomPlacementRepository placements, RoomStatusService statuses) {
        this.rooms = rooms;
        this.network = network;
        this.maps = maps;
        this.placements = placements;
        this.statuses = statuses;
    }

    public NeighborsResponse neighbors(UUID roomId) {
        if (!rooms.existsById(roomId)) {
            throw new NotFoundException("Raum " + roomId + " nicht gefunden.");
        }
        NetworkWideResponse net = network.network();
        RoutingGraph graph = RoutingGraph.build(net, mapInfos(), false);
        UUID mapId = graph.roomMap(roomId).orElse(null);
        Map<UUID, NeighborCalculator.RoomInfo> infos = mapId == null ? Map.of() : roomInfos(mapId);
        return toResponse(roomId, NeighborCalculator.calculate(graph, roomId, infos));
    }

    /**
     * Neighbors of every room attached on one map, computed from a single graph; for the floor display (026).
     * Not exposed over HTTP in this feature.
     */
    public Map<UUID, NeighborsResponse> neighborsForFloor(UUID mapId) {
        NetworkWideResponse net = network.network();
        MapNetworkResponse mapNetwork = net.maps().stream().filter(m -> m.mapId().equals(mapId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Karte " + mapId + " nicht gefunden."));
        RoutingGraph graph = RoutingGraph.build(net, mapInfos(), false);
        Map<UUID, NeighborCalculator.RoomInfo> infos = roomInfos(mapId);
        Map<UUID, NeighborsResponse> result = new HashMap<>();
        for (RoomAttachmentResponse attachment : mapNetwork.roomAttachments()) {
            UUID roomId = attachment.roomId();
            result.put(roomId, toResponse(roomId, NeighborCalculator.calculate(graph, roomId, infos)));
        }
        return result;
    }

    /** Route between two rooms; unknown rooms are 404, rooms or networks that cannot be routed are 409 with a code. */
    public RouteResponse route(UUID fromRoomId, UUID toRoomId, boolean accessibleOnly) {
        for (UUID id : List.of(fromRoomId, toRoomId)) {
            if (!rooms.existsById(id)) {
                throw new NotFoundException("Raum " + id + " nicht gefunden.");
            }
        }
        RouteFinder.Result result = RouteFinder.find(network.network(), mapInfos(), fromRoomId, toRoomId,
                accessibleOnly);
        if (result.problem() != null) {
            log.info("route_unavailable reason={} accessibleOnly={}", result.problem(), accessibleOnly);
            throw new ConflictException(MESSAGES.get(result.problem()), result.problem().name());
        }
        RouteFinder.Route route = result.route();
        List<RouteStepResponse> steps = route.steps().stream().map(step -> new RouteStepResponse(step.mapId(),
                step.floorName(), step.waypointId(), step.x(), step.y(), step.floorChange() == null ? null
                        : new RouteStepResponse.FloorChange(step.floorChange().connectionId(),
                                step.floorChange().connectionName(), step.floorChange().type().name(),
                                step.floorChange().fromFloorName(), step.floorChange().toFloorName()))).toList();
        return new RouteResponse(fromRoomId, toRoomId, accessibleOnly, route.distanceMeters(),
                route.walkingSeconds(), steps);
    }

    private static final Map<RouteFinder.Problem, String> MESSAGES = Map.of(
            RouteFinder.Problem.ROOM_NOT_ATTACHED, "Mindestens ein Raum ist nicht an das Wegenetz angebunden.",
            RouteFinder.Problem.SCALE_MISSING, "Für mindestens eine Karte auf dem Weg fehlt der Maßstab.",
            RouteFinder.Problem.NOT_CONNECTED, "Die beiden Räume sind nicht über das Wegenetz verbunden.",
            RouteFinder.Problem.NO_ACCESSIBLE_ROUTE, "Es gibt keinen barrierefreien Weg (nur Aufzüge) zwischen den Räumen.");

    private NeighborsResponse toResponse(UUID roomId, NeighborCalculator.Result result) {
        if (!result.available()) {
            log.info("neighbors_unavailable reason={}", result.reason());
            return new NeighborsResponse(roomId, false, result.reason().name(), List.of());
        }
        List<UUID> roomIds = result.entries().stream().map(NeighborCalculator.Entry::roomId)
                .filter(java.util.Objects::nonNull).toList();
        Map<UUID, String> status = safeStatuses(roomIds);
        List<NeighborEntryResponse> entries = result.entries().stream().map(e -> new NeighborEntryResponse(
                e.direction().name(), e.kind().name(), e.roomId(), e.connectionId(), e.name(), e.targetFloorName(),
                e.meters(), e.seconds(), e.roomId() == null ? null : status.get(e.roomId()))).toList();
        return new NeighborsResponse(roomId, true, null, entries);
    }

    /** A failing status lookup must not take the neighbor list down; the entries are then shown without status. */
    private Map<UUID, String> safeStatuses(List<UUID> roomIds) {
        try {
            return statuses.statusOf(roomIds);
        } catch (RuntimeException e) {
            log.warn("neighbor_status_unavailable");
            return Map.of();
        }
    }

    Map<UUID, RoutingGraph.MapInfo> mapInfos() {
        Map<UUID, RoutingGraph.MapInfo> result = new HashMap<>();
        for (FloorMap map : maps.findAllWithFloor()) {
            var floor = map.getFloor();
            result.put(map.getId(), new RoutingGraph.MapInfo(map.getId(), floor.getBuilding().getId(),
                    floor.getPosition(), floor.getName()));
        }
        return result;
    }

    private Map<UUID, NeighborCalculator.RoomInfo> roomInfos(UUID mapId) {
        return placements.findByMapId(mapId).stream().collect(Collectors.toMap(p -> p.getRoom().getId(),
                RoutingService::roomInfo));
    }

    private static NeighborCalculator.RoomInfo roomInfo(RoomPlacement p) {
        return new NeighborCalculator.RoomInfo(p.getRoom().getId(), p.getRoom().getName(), p.getMap().getId(),
                p.getX(), p.getY(), p.getRoom().getStatus() == EntityStatus.ACTIVE);
    }
}
