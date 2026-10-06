package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.RoomPlacement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomPlacementRepository extends JpaRepository<RoomPlacement, UUID> {

    @Query("select p from RoomPlacement p join fetch p.room r where p.map.id = :mapId order by r.name")
    List<RoomPlacement> findByMapId(@Param("mapId") UUID mapId);

    long countByMapId(UUID mapId);

    /** Insert-or-move in one statement so concurrent first placements of a room cannot conflict (last write wins). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        insert into room_placement (room_id, map_id, x, y) values (:roomId, :mapId, :x, :y)
        on conflict (room_id) do update set map_id = excluded.map_id, x = excluded.x, y = excluded.y
    """, nativeQuery = true)
    void upsert(@Param("roomId") UUID roomId, @Param("mapId") UUID mapId, @Param("x") double x, @Param("y") double y);
}
