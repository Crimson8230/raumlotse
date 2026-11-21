package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.PathSegment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PathSegmentRepository extends JpaRepository<PathSegment, UUID> {

    List<PathSegment> findByMapId(UUID mapId);

    @Query("select count(s) > 0 from PathSegment s where s.aId = :aId and s.bId = :bId")
    boolean existsByAIdAndBId(@Param("aId") UUID aId, @Param("bId") UUID bId);

    @Query("select s from PathSegment s where s.mapId = :mapId and (s.aId = :waypointId or s.bId = :waypointId)")
    List<PathSegment> findByWaypoint(@Param("mapId") UUID mapId, @Param("waypointId") UUID waypointId);
}
