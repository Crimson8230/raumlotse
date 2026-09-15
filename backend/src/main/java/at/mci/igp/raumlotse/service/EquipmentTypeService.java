package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.EquipmentTypeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EquipmentTypeService {

    private final EquipmentTypeRepository equipmentTypeRepository;

    public EquipmentTypeService(EquipmentTypeRepository equipmentTypeRepository) {
        this.equipmentTypeRepository = equipmentTypeRepository;
    }

    public EquipmentType create(String name) {
        requireUniqueName(name, null);
        return equipmentTypeRepository.save(new EquipmentType(name));
    }

    @Transactional(readOnly = true)
    public List<EquipmentType> list(EntityStatus statusFilter) {
        if (statusFilter == null) {
            return equipmentTypeRepository.findAll();
        }
        return equipmentTypeRepository.findByStatus(statusFilter);
    }

    @Transactional(readOnly = true)
    public EquipmentType get(UUID id) {
        return findOrThrow(id);
    }

    public EquipmentType rename(UUID id, String name) {
        EquipmentType equipmentType = findOrThrow(id);
        requireUniqueName(name, id);
        equipmentType.setName(name);
        return equipmentType;
    }

    public EquipmentType deactivate(UUID id) {
        EquipmentType equipmentType = findOrThrow(id);
        equipmentType.setStatus(EntityStatus.DEACTIVATED);
        return equipmentType;
    }

    public EquipmentType reactivate(UUID id) {
        EquipmentType equipmentType = findOrThrow(id);
        equipmentType.setStatus(EntityStatus.ACTIVE);
        return equipmentType;
    }

    public void delete(UUID id) {
        EquipmentType equipmentType = findOrThrow(id);
        if (equipmentTypeRepository.isAssignedToAnyRoom(id)) {
            throw new ConflictException(
                    "Equipment type '" + equipmentType.getName() + "' is assigned to one or more rooms; deactivate it instead.");
        }
        equipmentTypeRepository.delete(equipmentType);
    }

    private void requireUniqueName(String name, UUID excludingId) {
        boolean exists = excludingId == null
                ? equipmentTypeRepository.existsByNameIgnoreCase(name)
                : equipmentTypeRepository.existsByNameIgnoreCaseAndIdNot(name, excludingId);
        if (exists) {
            throw new ConflictException("An equipment type named '" + name + "' already exists.");
        }
    }

    private EquipmentType findOrThrow(UUID id) {
        return equipmentTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Equipment type " + id + " not found."));
    }
}
