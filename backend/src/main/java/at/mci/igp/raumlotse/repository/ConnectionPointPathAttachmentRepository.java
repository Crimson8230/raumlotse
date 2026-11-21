package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.ConnectionPointPathAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectionPointPathAttachmentRepository extends JpaRepository<ConnectionPointPathAttachment, UUID> {

    List<ConnectionPointPathAttachment> findByMapId(UUID mapId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        insert into connection_point_path_attachment (connection_point_id, map_id, waypoint_id)
        values (:pointId, :mapId, :waypointId)
        on conflict (connection_point_id) do update set map_id = excluded.map_id, waypoint_id = excluded.waypoint_id
    """, nativeQuery = true)
    void upsert(@Param("pointId") UUID pointId, @Param("mapId") UUID mapId, @Param("waypointId") UUID waypointId);
}
