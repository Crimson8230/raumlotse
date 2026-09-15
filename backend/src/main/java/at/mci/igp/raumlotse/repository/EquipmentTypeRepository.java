package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<EquipmentType> findByStatus(EntityStatus status);

    @Query("select case when count(r) > 0 then true else false end from Room r join r.equipmentTypes e where e.id = :equipmentTypeId")
    boolean isAssignedToAnyRoom(UUID equipmentTypeId);
}
