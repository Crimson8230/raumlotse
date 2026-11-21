package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.PathSegment;
import at.mci.igp.raumlotse.domain.PathWaypoint;
import at.mci.igp.raumlotse.dto.PathIssuesResponse;
import at.mci.igp.raumlotse.dto.PathIssuesResponse.OutsideAttachment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Finds gaps in the network of one map: missing scale, separate network parts, unattached rooms and points. */
@Component
public class PathNetworkIssueChecker {

    public record Input(boolean scaleAvailable, List<PathWaypoint> waypoints, List<PathSegment> segments,
            Set<UUID> placedRoomIds, Map<UUID, UUID> roomAttachments, Set<UUID> connectionPointIds,
            Map<UUID, UUID> connectionPointAttachments) {
    }

    public PathIssuesResponse check(Input in) {
        Map<UUID, UUID> parent = new HashMap<>();
        for (PathWaypoint waypoint : in.waypoints()) {
            parent.put(waypoint.getId(), waypoint.getId());
        }
        for (PathSegment segment : in.segments()) {
            if (parent.containsKey(segment.getAId()) && parent.containsKey(segment.getBId())) {
                parent.put(find(parent, segment.getAId()), find(parent, segment.getBId()));
            }
        }
        Map<UUID, List<UUID>> byRoot = new LinkedHashMap<>();
        for (PathWaypoint waypoint : in.waypoints()) {
            byRoot.computeIfAbsent(find(parent, waypoint.getId()), key -> new ArrayList<>()).add(waypoint.getId());
        }
        List<List<UUID>> components = new ArrayList<>(byRoot.values());
        components.forEach(component -> component.sort(Comparator.comparing(UUID::toString)));
        components.sort(Comparator.<List<UUID>>comparingInt(List::size).reversed()
                .thenComparing(component -> component.get(0).toString()));

        UUID mainRoot = components.isEmpty() ? null : find(parent, components.get(0).get(0));
        List<OutsideAttachment> outside = new ArrayList<>();
        if (components.size() > 1) {
            in.roomAttachments().forEach((roomId, waypointId) -> {
                if (parent.containsKey(waypointId) && !find(parent, waypointId).equals(mainRoot)) {
                    outside.add(new OutsideAttachment("ROOM", roomId));
                }
            });
            in.connectionPointAttachments().forEach((pointId, waypointId) -> {
                if (parent.containsKey(waypointId) && !find(parent, waypointId).equals(mainRoot)) {
                    outside.add(new OutsideAttachment("CONNECTION_POINT", pointId));
                }
            });
            outside.sort(Comparator.comparing(OutsideAttachment::kind).thenComparing(a -> a.id().toString()));
        }
        return new PathIssuesResponse(!in.scaleAvailable(), components.size(), components,
                missing(in.placedRoomIds(), in.roomAttachments()),
                missing(in.connectionPointIds(), in.connectionPointAttachments()), outside);
    }

    private static List<UUID> missing(Set<UUID> all, Map<UUID, UUID> attached) {
        return all.stream().filter(id -> !attached.containsKey(id)).sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    private static UUID find(Map<UUID, UUID> parent, UUID id) {
        UUID root = id;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        UUID current = id;
        while (!current.equals(root)) {
            UUID next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }
}
