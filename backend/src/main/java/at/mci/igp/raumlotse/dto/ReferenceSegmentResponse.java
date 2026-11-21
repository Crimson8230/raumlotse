package at.mci.igp.raumlotse.dto;

import java.util.UUID;

public record ReferenceSegmentResponse(UUID mapId, double x1, double y1, double x2, double y2, double lengthMeters,
        double pixelsPerMeter) {
}
