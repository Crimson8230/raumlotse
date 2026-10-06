package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.FloorMap;
import at.mci.igp.raumlotse.domain.FloorMapImage;
import at.mci.igp.raumlotse.dto.ConnectionResponse;
import at.mci.igp.raumlotse.dto.MapDetailResponse;
import at.mci.igp.raumlotse.dto.MapSummaryResponse;
import at.mci.igp.raumlotse.dto.PlacementResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ConnectionRepository;
import at.mci.igp.raumlotse.repository.FloorMapImageRepository;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import at.mci.igp.raumlotse.repository.RoomPlacementRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FloorMapService {

    /** Relative aspect-ratio deviation above which a replacement is reported as "changed". */
    private static final double ASPECT_TOLERANCE = 0.01;

    public record SaveResult(MapSummaryResponse map, boolean created) {
    }

    public record MapImage(String contentType, long imageVersion, byte[] bytes) {
    }

    private final FloorMapRepository maps;
    private final FloorMapImageRepository images;
    private final FloorRepository floors;
    private final RoomPlacementRepository placements;
    private final ConnectionRepository connections;
    private final MapImageValidator validator;

    public FloorMapService(FloorMapRepository maps, FloorMapImageRepository images, FloorRepository floors,
            RoomPlacementRepository placements, ConnectionRepository connections, MapImageValidator validator) {
        this.maps = maps;
        this.images = images;
        this.floors = floors;
        this.placements = placements;
        this.connections = connections;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<MapSummaryResponse> list() {
        return maps.findAllWithFloor().stream()
                .map(map -> MapSummaryResponse.from(map, placements.countByMapId(map.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MapDetailResponse get(UUID mapId) {
        FloorMap map = findWithFloorOrThrow(mapId);
        List<PlacementResponse> placed = placements.findByMapId(mapId).stream().map(PlacementResponse::from).toList();
        List<ConnectionResponse> reachable = connections.findByPointOnMap(mapId).stream()
                .map(ConnectionResponse::from).toList();
        return MapDetailResponse.from(map, placed, reachable);
    }

    @Transactional(readOnly = true)
    public MapImage image(UUID mapId) {
        FloorMap map = findWithFloorOrThrow(mapId);
        FloorMapImage image = images.findById(mapId)
                .orElseThrow(() -> new NotFoundException("Karte " + mapId + " hat kein Bild."));
        return new MapImage(map.getContentType(), map.getImageVersion(), image.getImage());
    }

    /** Creates the floor's map or replaces its image. Validation happens before anything is changed. */
    public SaveResult saveForFloor(UUID floorId, byte[] bytes) {
        var floor = floors.findById(floorId)
                .orElseThrow(() -> new NotFoundException("Stockwerk " + floorId + " nicht gefunden."));
        MapImageValidator.ValidatedImage validated = validator.validate(bytes);

        var existing = maps.findByFloorId(floorId);
        if (existing.isEmpty()) {
            FloorMap created = maps.save(new FloorMap(floor, validated.contentType(), validated.widthPx(),
                    validated.heightPx()));
            images.save(new FloorMapImage(created.getId(), bytes));
            return new SaveResult(MapSummaryResponse.from(created, 0), true);
        }
        FloorMap map = existing.get();
        boolean aspectChanged = aspectRatioDiffers(map.getWidthPx(), map.getHeightPx(), validated.widthPx(),
                validated.heightPx());
        map.replaceImage(validated.contentType(), validated.widthPx(), validated.heightPx());
        FloorMap saved = maps.save(map);
        FloorMapImage image = images.findById(saved.getId()).orElseGet(() -> new FloorMapImage(saved.getId(), bytes));
        image.setImage(bytes);
        images.save(image);
        return new SaveResult(MapSummaryResponse.from(saved, placements.countByMapId(saved.getId()), aspectChanged),
                false);
    }

    public void delete(UUID mapId) {
        FloorMap map = maps.findById(mapId).orElseThrow(() -> new NotFoundException("Karte " + mapId + " nicht gefunden."));
        maps.delete(map);
    }

    private FloorMap findWithFloorOrThrow(UUID mapId) {
        return maps.findByIdWithFloor(mapId).orElseThrow(() -> new NotFoundException("Karte " + mapId + " nicht gefunden."));
    }

    private static boolean aspectRatioDiffers(int oldW, int oldH, int newW, int newH) {
        double oldRatio = (double) oldW / oldH;
        double newRatio = (double) newW / newH;
        return Math.abs(oldRatio - newRatio) / oldRatio > ASPECT_TOLERANCE;
    }
}
