package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.MapDetailResponse;
import at.mci.igp.raumlotse.dto.MapSummaryResponse;
import at.mci.igp.raumlotse.service.FloorMapService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FloorMapController {

    private final FloorMapService service;

    public FloorMapController(FloorMapService service) {
        this.service = service;
    }

    @GetMapping("/api/maps")
    public List<MapSummaryResponse> list() {
        return service.list();
    }

    @PutMapping(value = "/api/floors/{floorId}/map", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MapSummaryResponse> createOrReplace(@PathVariable UUID floorId,
            @RequestPart("image") MultipartFile image) throws IOException {
        var result = service.saveForFloor(floorId, image.getBytes());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.map());
    }

    @GetMapping("/api/maps/{mapId}")
    public MapDetailResponse get(@PathVariable UUID mapId) {
        return service.get(mapId);
    }

    @GetMapping("/api/maps/{mapId}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID mapId, WebRequest request) {
        var image = service.image(mapId);
        String etag = "\"" + image.imageVersion() + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(image.bytes());
    }

    @DeleteMapping("/api/maps/{mapId}")
    public ResponseEntity<Void> delete(@PathVariable UUID mapId) {
        service.delete(mapId);
        return ResponseEntity.noContent().build();
    }
}
