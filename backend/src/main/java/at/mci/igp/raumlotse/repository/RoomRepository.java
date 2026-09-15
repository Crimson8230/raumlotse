package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Room;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    boolean existsByFloor_Building_IdAndNameIgnoreCase(UUID buildingId, String name);

    boolean existsByFloor_Building_IdAndNameIgnoreCaseAndIdNot(UUID buildingId, String name, UUID id);

    List<Room> findByStatus(EntityStatus status);

    boolean existsByFloorId(UUID floorId);
}
