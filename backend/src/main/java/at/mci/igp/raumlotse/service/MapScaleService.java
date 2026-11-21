package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.FloorMap;
import at.mci.igp.raumlotse.domain.MapReferenceSegment;
import at.mci.igp.raumlotse.dto.ReferenceSegmentRequest;
import at.mci.igp.raumlotse.dto.ReferenceSegmentResponse;
import at.mci.igp.raumlotse.dto.ScaleResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.MapReferenceSegmentRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sets the reference segment of a map and derives the scale (pixels per meter) and meter lengths from it. */
@Service
@Transactional
public class MapScaleService {

    public static final double MAX_LENGTH_METERS = 100000.0;

    public record ReferenceResult(ReferenceSegmentResponse reference, boolean created) {
    }

    private static final Logger log = LoggerFactory.getLogger(MapScaleService.class);

    private final FloorMapRepository maps;
    private final MapReferenceSegmentRepository references;

    public MapScaleService(FloorMapRepository maps, MapReferenceSegmentRepository references) {
        this.maps = maps;
        this.references = references;
    }

    /** Scale of the map; unavailable (no meter values) without a reference segment. */
    public static ScaleResponse scaleOf(FloorMap map, MapReferenceSegment reference) {
        if (reference == null) {
            return new ScaleResponse(false, null, map.getWidthPx(), map.getHeightPx());
        }
        return new ScaleResponse(true, pixelsPerMeter(map, reference), map.getWidthPx(), map.getHeightPx());
    }

    public static double pixelsPerMeter(FloorMap map, MapReferenceSegment reference) {
        return PathGeometry.pixelDistance(reference.getX1(), reference.getY1(), reference.getX2(), reference.getY2(),
                map.getWidthPx(), map.getHeightPx()) / reference.getLengthM();
    }

    /** Length in meters of a line between two relative points; null while the scale is unavailable. */
    public static Double lengthMeters(ScaleResponse scale, double x1, double y1, double x2, double y2) {
        if (!scale.available()) {
            return null;
        }
        return PathGeometry.pixelDistance(x1, y1, x2, y2, scale.widthPx(), scale.heightPx()) / scale.pixelsPerMeter();
    }

    public static ReferenceSegmentResponse toResponse(FloorMap map, MapReferenceSegment reference) {
        return new ReferenceSegmentResponse(reference.getMapId(), reference.getX1(), reference.getY1(),
                reference.getX2(), reference.getY2(), reference.getLengthM(), pixelsPerMeter(map, reference));
    }

    public ReferenceResult set(UUID mapId, ReferenceSegmentRequest request) {
        FloorMap map = requireMap(mapId);
        double length = request.lengthMeters() == null ? Double.NaN : request.lengthMeters();
        if (!Double.isFinite(length) || length <= 0.0 || length > MAX_LENGTH_METERS) {
            reject(mapId, "REFERENCE_LENGTH_INVALID");
            throw new IllegalArgumentException("Die Länge muss größer als 0 und höchstens 100000 Meter sein.");
        }
        double[] c = {request.x1(), request.y1(), request.x2(), request.y2()};
        for (double value : c) {
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                reject(mapId, "REFERENCE_OUT_OF_BOUNDS");
                throw new IllegalArgumentException("Die Endpunkte der Referenzstrecke müssen auf der Karte liegen.");
            }
        }
        if (PathGeometry.coincide(c[0], c[1], c[2], c[3])) {
            reject(mapId, "REFERENCE_ENDPOINTS_IDENTICAL");
            throw new IllegalArgumentException("Die Endpunkte der Referenzstrecke dürfen nicht zusammenfallen.");
        }
        var existing = references.findById(mapId);
        MapReferenceSegment reference;
        if (existing.isPresent()) {
            reference = existing.get();
            reference.update(c[0], c[1], c[2], c[3], length);
        } else {
            reference = new MapReferenceSegment(mapId, c[0], c[1], c[2], c[3], length);
            references.save(reference);
        }
        return new ReferenceResult(toResponse(map, reference), existing.isEmpty());
    }

    public void delete(UUID mapId) {
        requireMap(mapId);
        var reference = references.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Karte " + mapId + " hat keine Referenzstrecke."));
        references.delete(reference);
    }

    private FloorMap requireMap(UUID mapId) {
        return maps.findById(mapId).orElseThrow(() -> new NotFoundException("Karte " + mapId + " nicht gefunden."));
    }

    private static void reject(UUID mapId, String code) {
        log.warn("path_network_rejected code={} mapId={}", code, mapId);
    }
}
