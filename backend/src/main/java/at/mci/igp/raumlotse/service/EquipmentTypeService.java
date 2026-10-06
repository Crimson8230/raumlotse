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
        return create(name, null);
    }
    public EquipmentType create(String name, String code) {
        requireUniqueName(name, null);
        return equipmentTypeRepository.save(new EquipmentType(name, code == null || code.isBlank() ? name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_") : code.trim().toUpperCase()));
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

    public EquipmentType updateCode(UUID id, String code) {
        EquipmentType equipmentType = findOrThrow(id);
        equipmentType.setCode(code.trim().toUpperCase());
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
                    "Ausstattungstyp '" + equipmentType.getName() + "' ist einem oder mehreren Räumen zugewiesen; bitte stattdessen deaktivieren.");
        }
        equipmentTypeRepository.delete(equipmentType);
    }

    private void requireUniqueName(String name, UUID excludingId) {
        boolean exists = excludingId == null
                ? equipmentTypeRepository.existsByNameIgnoreCase(name)
                : equipmentTypeRepository.existsByNameIgnoreCaseAndIdNot(name, excludingId);
        if (exists) {
            throw new ConflictException("Ein Ausstattungstyp mit dem Namen '" + name + "' existiert bereits.");
        }
    }

    private EquipmentType findOrThrow(UUID id) {
        return equipmentTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ausstattungstyp " + id + " nicht gefunden."));
    }
}
