package at.mci.igp.raumlotse.dto;

import java.util.List;
import java.util.UUID;

public record RoomDeviceControlsResponse(UUID roomId, UUID reservationId, List<RoomDeviceResponse> devices) { }
