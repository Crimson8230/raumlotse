package at.mci.igp.raumlotse.dto;

import java.util.List;

public record SplitResultResponse(WaypointResponse waypoint, List<SegmentResponse> segments) {
}
