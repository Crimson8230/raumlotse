package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.PathWaypoint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PathWaypointRepository extends JpaRepository<PathWaypoint, UUID> {

    List<PathWaypoint> findByMapId(UUID mapId);

    long countByMapId(UUID mapId);
}
