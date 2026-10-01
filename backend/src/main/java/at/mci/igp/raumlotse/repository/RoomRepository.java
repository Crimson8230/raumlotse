package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Room;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    boolean existsByFloor_Building_IdAndNameIgnoreCase(UUID buildingId, String name);

    boolean existsByFloor_Building_IdAndNameIgnoreCaseAndIdNot(UUID buildingId, String name, UUID id);

    List<Room> findByStatus(EntityStatus status);

    boolean existsByFloorId(UUID floorId);

    // Search candidates (feature 008): room, floor, and building all active. Two methods instead of a nullable
    // parameter avoid null-typed parameter binding issues in Hibernate.
    @Query("""
        select r from Room r
        join fetch r.floor f
        join fetch f.building b
        where r.status = at.mci.igp.raumlotse.domain.EntityStatus.ACTIVE
          and f.status = at.mci.igp.raumlotse.domain.EntityStatus.ACTIVE
          and b.status = at.mci.igp.raumlotse.domain.EntityStatus.ACTIVE
    """)
    List<Room> findSearchCandidates();

    @Query("""
        select r from Room r
        join fetch r.floor f
        join fetch f.building b
        where r.status = at.mci.igp.raumlotse.domain.EntityStatus.ACTIVE
          and f.status = at.mci.igp.raumlotse.domain.EntityStatus.ACTIVE
          and b.status = at.mci.igp.raumlotse.domain.EntityStatus.ACTIVE
          and b.id = :buildingId
    """)
    List<Room> findSearchCandidatesInBuilding(@Param("buildingId") UUID buildingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") UUID id);
}
