package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.RoomPathAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomPathAttachmentRepository extends JpaRepository<RoomPathAttachment, UUID> {

    List<RoomPathAttachment> findByMapId(UUID mapId);

    /** Attach or re-attach in one statement (last write wins). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        insert into room_path_attachment (room_id, map_id, waypoint_id) values (:roomId, :mapId, :waypointId)
        on conflict (room_id) do update set map_id = excluded.map_id, waypoint_id = excluded.waypoint_id
    """, nativeQuery = true)
    void upsert(@Param("roomId") UUID roomId, @Param("mapId") UUID mapId, @Param("waypointId") UUID waypointId);
}
