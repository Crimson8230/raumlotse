package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.PathSegment;
import java.util.UUID;

/** {@code lengthMeters} is null while the map has no scale. */
public record SegmentResponse(UUID id, UUID mapId, UUID fromWaypointId, UUID toWaypointId, Double lengthMeters) {

    public static SegmentResponse from(PathSegment segment, Double lengthMeters) {
        return new SegmentResponse(segment.getId(), segment.getMapId(), segment.getAId(), segment.getBId(),
                lengthMeters);
    }
}
