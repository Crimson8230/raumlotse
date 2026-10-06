package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.FloorMap;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.PlacementResponse;
import at.mci.igp.raumlotse.dto.RoomRefResponse;
import at.mci.igp.raumlotse.exception.MapRequestException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.RoomPlacementRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoomPlacementService {

    public record PlaceResult(PlacementResponse placement, boolean created) {
    }

    private static final Logger log = LoggerFactory.getLogger(RoomPlacementService.class);

    private final FloorMapRepository maps;
    private final RoomRepository rooms;
    private final RoomPlacementRepository placements;

    public RoomPlacementService(FloorMapRepository maps, RoomRepository rooms, RoomPlacementRepository placements) {
        this.maps = maps;
        this.rooms = rooms;
        this.placements = placements;
    }

    /** Places the room on the map or moves it. A room can only be placed on the map of its own floor. */
    public PlaceResult place(UUID mapId, UUID roomId, double x, double y) {
        requireFraction(x, "x");
        requireFraction(y, "y");
        FloorMap map = maps.findByIdWithFloor(mapId)
                .orElseThrow(() -> new NotFoundException("Karte " + mapId + " nicht gefunden."));
        Room room = rooms.findById(roomId).orElseThrow(() -> new NotFoundException("Raum " + roomId + " nicht gefunden."));
        if (!room.getFloor().getId().equals(map.getFloor().getId())) {
            log.warn("placement_rejected code=ROOM_FLOOR_MISMATCH mapId={} roomId={}", mapId, roomId);
            throw new MapRequestException(HttpStatus.UNPROCESSABLE_CONTENT, "ROOM_FLOOR_MISMATCH",
                    "Raum '" + room.getName() + "' liegt nicht auf dem Stockwerk dieser Karte.");
        }
        boolean existed = placements.existsById(roomId);
        placements.upsert(roomId, mapId, x, y);
        var saved = placements.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Raum " + roomId + " wurde während der Platzierung entfernt."));
        return new PlaceResult(PlacementResponse.from(saved), !existed);
    }

    @Transactional(readOnly = true)
    public List<RoomRefResponse> unplacedRooms(UUID mapId) {
        FloorMap map = maps.findByIdWithFloor(mapId)
                .orElseThrow(() -> new NotFoundException("Karte " + mapId + " nicht gefunden."));
        return rooms.findUnplacedActiveByFloorId(map.getFloor().getId()).stream().map(RoomRefResponse::from).toList();
    }

    public void remove(UUID mapId, UUID roomId) {
        var placement = placements.findById(roomId)
                .filter(candidate -> candidate.getMap().getId().equals(mapId))
                .orElseThrow(() -> new NotFoundException("Raum " + roomId + " ist nicht auf Karte " + mapId + "."));
        placements.delete(placement);
    }

    private static void requireFraction(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Position " + name + " muss zwischen 0 und 1 liegen.");
        }
    }
}
