package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.EquipmentType;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.SeatingArrangement;
import at.mci.igp.raumlotse.dto.RoomCreateRequest;
import at.mci.igp.raumlotse.dto.RoomUpdateRequest;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.EquipmentTypeRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoomService {

    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final RoomDependentHistoryChecker dependentHistoryChecker;
    private final ReservationRepository reservationRepository;

    public RoomService(
            RoomRepository roomRepository,
            FloorRepository floorRepository,
            EquipmentTypeRepository equipmentTypeRepository,
            RoomDependentHistoryChecker dependentHistoryChecker,
            ReservationRepository reservationRepository) {
        this.roomRepository = roomRepository;
        this.floorRepository = floorRepository;
        this.equipmentTypeRepository = equipmentTypeRepository;
        this.dependentHistoryChecker = dependentHistoryChecker;
        this.reservationRepository = reservationRepository;
    }

    public Room create(RoomCreateRequest request) {
        Floor floor = requireSelectableFloor(request.floorId());
        requireUniqueName(floor, request.name(), null);
        List<EquipmentType> equipmentTypes = requireActiveEquipmentTypes(request.equipmentTypeIds());

        Room room = new Room(request.name(), floor);
        room.replaceSeatingArrangements(toSeatingArrangements(request.seatingArrangements()));
        room.setEquipmentTypes(equipmentTypes);
        room.setNotBarrierFree(Boolean.TRUE.equals(request.notBarrierFree()));
        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public List<Room> list(EntityStatus statusFilter) {
        if (statusFilter == null) {
            return roomRepository.findAll();
        }
        return roomRepository.findByStatus(statusFilter);
    }

    @Transactional(readOnly = true)
    public Room get(UUID id) {
        return findOrThrow(id);
    }

    public Room update(UUID id, RoomUpdateRequest request) {
        Room room = findOrThrow(id);
        if (!room.getVersion().equals(request.version())) {
            throw new OptimisticLockingFailureException(
                    "Raum " + id + " wurde zwischenzeitlich von jemand anderem geändert.");
        }

        Floor floor = requireSelectableFloor(request.floorId());
        requireUniqueName(floor, request.name(), id);
        List<EquipmentType> equipmentTypes = requireActiveEquipmentTypes(request.equipmentTypeIds());

        room.setName(request.name());
        room.setFloor(floor);
        room.replaceSeatingArrangements(toSeatingArrangements(request.seatingArrangements()));
        room.setEquipmentTypes(equipmentTypes);
        if (request.notBarrierFree() != null) {
            room.setNotBarrierFree(request.notBarrierFree());
        }
        return room;
    }

    public Room deactivate(UUID id) {
        Room room = findOrThrow(id);
        if (reservationRepository.existsByRoomIdAndStatusIn(id, List.of(ReservationStatus.RESERVED, ReservationStatus.ACTIVE))) {
            throw new ConflictException("Der Raum hat aktive oder künftige Reservierungen; bitte zuerst stornieren.");
        }
        room.setStatus(EntityStatus.DEACTIVATED);
        return room;
    }

    public Room reactivate(UUID id) {
        Room room = findOrThrow(id);
        room.setStatus(EntityStatus.ACTIVE);
        return room;
    }

    public void delete(UUID id) {
        Room room = findOrThrow(id);
        if (dependentHistoryChecker.hasDependentHistory(id)) {
            throw new ConflictException(
                    "Raum '" + room.getName() + "' hat Buchungshistorie; bitte stattdessen deaktivieren.");
        }
        roomRepository.delete(room);
    }

    private Floor requireSelectableFloor(UUID floorId) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new NotFoundException("Stockwerk " + floorId + " nicht gefunden."));
        if (!floor.isSelectable()) {
            throw new IllegalArgumentException(
                    "Stockwerk '" + floor.getName() + "' ist nicht aktiv (oder sein Gebäude ist nicht aktiv).");
        }
        return floor;
    }

    private List<EquipmentType> requireActiveEquipmentTypes(List<UUID> equipmentTypeIds) {
        return equipmentTypeIds.stream()
                .map(equipmentTypeId -> {
                    EquipmentType equipmentType = equipmentTypeRepository.findById(equipmentTypeId)
                            .orElseThrow(() -> new NotFoundException("Ausstattungstyp " + equipmentTypeId + " nicht gefunden."));
                    if (equipmentType.getStatus() != EntityStatus.ACTIVE) {
                        throw new IllegalArgumentException(
                                "Ausstattungstyp '" + equipmentType.getName() + "' ist deaktiviert und kann nicht zugewiesen werden.");
                    }
                    return equipmentType;
                })
                .toList();
    }

    private void requireUniqueName(Floor floor, String name, UUID excludingId) {
        UUID buildingId = floor.getBuilding().getId();
        boolean exists = excludingId == null
                ? roomRepository.existsByFloor_Building_IdAndNameIgnoreCase(buildingId, name)
                : roomRepository.existsByFloor_Building_IdAndNameIgnoreCaseAndIdNot(buildingId, name, excludingId);
        if (exists) {
            throw new ConflictException("Ein Raum mit dem Namen '" + name + "' existiert in diesem Gebäude bereits.");
        }
    }

    private List<SeatingArrangement> toSeatingArrangements(List<at.mci.igp.raumlotse.dto.SeatingArrangementRequest> requests) {
        return requests.stream()
                .map(r -> new SeatingArrangement(r.name(), r.maxCapacity()))
                .toList();
    }

    private Room findOrThrow(UUID id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Raum " + id + " nicht gefunden."));
    }
}
