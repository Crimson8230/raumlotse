package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.FloorMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FloorMapRepository extends JpaRepository<FloorMap, UUID> {

    Optional<FloorMap> findByFloorId(UUID floorId);

    boolean existsByFloorId(UUID floorId);

    @Query("""
        select m from FloorMap m join fetch m.floor f join fetch f.building b
        order by b.name, f.name
    """)
    List<FloorMap> findAllWithFloor();

    @Query("select m from FloorMap m join fetch m.floor f join fetch f.building where m.id = :id")
    Optional<FloorMap> findByIdWithFloor(UUID id);
}
