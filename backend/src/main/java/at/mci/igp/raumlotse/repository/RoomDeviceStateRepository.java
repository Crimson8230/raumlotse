package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.domain.RoomDeviceState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomDeviceStateRepository extends JpaRepository<RoomDeviceState, UUID> {
    Optional<RoomDeviceState> findByRoomIdAndKind(UUID roomId, RoomDeviceKind kind);
}
