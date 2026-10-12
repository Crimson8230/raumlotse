package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomFavorite;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomFavoriteRepository extends JpaRepository<RoomFavorite, RoomFavorite.Key> {

    @Query("select f.id.roomId from RoomFavorite f where f.id.userId = :userId")
    Set<UUID> findRoomIdsByUserId(@Param("userId") UUID userId);

    long countByIdUserId(UUID userId);

    /** The user's favorite rooms with floor and building, whatever their status (for the "Nicht verfügbar" list). */
    @Query("""
        select r from Room r
        join fetch r.floor fl
        join fetch fl.building b
        where r.id in (select f.id.roomId from RoomFavorite f where f.id.userId = :userId)
    """)
    List<Room> findFavoriteRooms(@Param("userId") UUID userId);
}
