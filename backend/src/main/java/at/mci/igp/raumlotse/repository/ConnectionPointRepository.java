package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.ConnectionPoint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionPointRepository extends JpaRepository<ConnectionPoint, UUID> {

    List<ConnectionPoint> findByMapId(UUID mapId);
}
