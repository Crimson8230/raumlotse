package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Deterministic local adapter used until a physical device integration is available. */
@Component
public class PersistedRoomDeviceGateway implements RoomDeviceGateway {
    @Override
    public void setState(UUID roomId, RoomDeviceKind kind, boolean state) {
        // The persisted state is written by RoomDeviceService after this acknowledgement.
    }
}
