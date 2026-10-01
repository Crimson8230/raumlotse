package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import java.util.UUID;

public interface RoomDeviceGateway {
    void setState(UUID roomId, RoomDeviceKind kind, boolean state);
}
