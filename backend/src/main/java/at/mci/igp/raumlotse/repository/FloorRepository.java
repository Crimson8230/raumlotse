package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.Floor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorRepository extends JpaRepository<Floor, UUID> {

    boolean existsByBuildingIdAndNameIgnoreCase(UUID buildingId, String name);

    boolean existsByBuildingIdAndNameIgnoreCaseAndIdNot(UUID buildingId, String name, UUID id);

    List<Floor> findByBuildingId(UUID buildingId);

    long countByBuildingId(UUID buildingId);
}
