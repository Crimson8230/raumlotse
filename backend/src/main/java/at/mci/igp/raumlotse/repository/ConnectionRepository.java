package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.Connection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query("select distinct c from Connection c left join fetch c.points order by c.name")
    List<Connection> findAllWithPoints();

    @Query("""
        select distinct c from Connection c left join fetch c.points
        where c.id in (select p.connection.id from ConnectionPoint p where p.map.id = :mapId)
        order by c.name
    """)
    List<Connection> findByPointOnMap(@Param("mapId") UUID mapId);
}
