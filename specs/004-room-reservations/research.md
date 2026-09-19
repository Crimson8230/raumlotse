# Research & Technical Decisions: Room Reservations

**Branch**: `004-room-reservations` | **Date**: 2026-09-19 | **Spec**: [spec.md](./spec.md)

## 1. Concurrency Control & Conflict Detection

### Decision
Use pessimistic write locking (`SELECT FOR UPDATE` via `@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the target `Room` row during reservation creation and rescheduling checks, combined with a SQL overlap query:
```sql
WHERE room_id = :roomId
  AND status IN ('RESERVED', 'ACTIVE')
  AND start_time < :requestedEndTime
  AND end_time > :requestedStartTime
```

### Rationale
- **Concurrency Safety**: Two concurrent requests attempting to reserve the same room will serialize at the database level when acquiring the room lock, guaranteeing that the conflict check reads committed data and prevents race conditions (phantom double bookings).
- **Postgres & JPA Compatibility**: Avoids requiring the PostgreSQL `btree_gist` extension (which would be required for exclusion constraints across UUID + tstzrange).
- **Simplicity**: Aligns with Principle V (Simplicity) by using standard Spring Data JPA locking annotations without third-party dependencies.

### Alternatives Considered
- *PostgreSQL Exclusion Constraint (`EXCLUDE USING gist`)*: Requires installing the `btree_gist` extension in the PostgreSQL container and complicates Flyway migrations across different target test environments.
- *Optimistic Locking on Room version*: Bumping room version on every reservation would trigger optimistic locking failures even for completely non-overlapping time slots on the same room.

---

## 2. Reservation Lifecycle & Persisted Status Model

### Decision
Persist all five lifecycle states directly in the database `status` column (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`). Drive status transitions via explicit operational endpoints and manual UI buttons:
- **`RESERVED`**: Default initial state upon creation.
- **`ACTIVE`**: Triggered via `POST /api/reservations/{id}/activate` ("Activate / Check-In") from `RESERVED`.
- **`COMPLETED`**: Triggered via `POST /api/reservations/{id}/complete` ("Complete / Check-Out") from `ACTIVE`.
- **`EXPIRED`**: Triggered via `POST /api/reservations/{id}/expire` ("Expire / Mark No-Show") on an unattended `RESERVED` booking whose scheduled window elapsed.
- **`CANCELLED`**: Triggered via `POST /api/reservations/{id}/cancel` on `RESERVED` or `ACTIVE` bookings.

### Rationale
- **Explicit Domain Audit**: Concluded meetings (`COMPLETED`) are clearly distinguished from unattended reservations (`EXPIRED` no-shows), improving utilization reporting.
- **Persistence Clarity**: All states are directly queryable in PostgreSQL without in-memory state derivation discrepancies.
- **Operational Control**: Provides manual buttons immediately while deferring automatic background scheduler daemons (`@Scheduled`) to a future enhancement.
- **Terminal State Safety**: `COMPLETED`, `EXPIRED`, and `CANCELLED` are immutable terminal states; modifications and reactivations are rejected with HTTP 400 / 409.

### Alternatives Considered
- *Dynamic runtime calculation*: Computes status on read, but cannot distinguish between a completed meeting and an unattended no-show without check-in timestamps.
- *Automated `@Scheduled` worker daemon*: Periodically flips statuses automatically; deferred to future iterations per user requirements.

---

## 3. Additional Equipment Resolution

### Decision
The backend endpoint `GET /api/rooms/{roomId}/available-equipment` (or enriched in the room view response) returns active equipment types from `EquipmentTypeRepository` that are **not** present in the room's permanent `equipmentTypes` collection:
```java
List<EquipmentType> available = equipmentTypeRepository.findByStatus(EntityStatus.ACTIVE).stream()
    .filter(eq -> !room.getEquipmentTypes().contains(eq))
    .toList();
```
When creating a reservation, the backend validates that:
1. Every requested additional equipment type exists and has `status = ACTIVE`.
2. None of the requested equipment types are already assigned to the room.

### Rationale
Enforces spec requirements FR-011 and Story 2 with clear separation between built-in room assets and mobile equipment.

---

## 4. Room Deactivation & Deletion Lifecycle Integration

### Decision
1. **Dependent History Check**: Implement the existing project interface `RoomDependentHistoryChecker`:
   ```java
   @Component
   public class ReservationRoomHistoryChecker implements RoomDependentHistoryChecker {
       private final ReservationRepository reservationRepository;
       @Override
       public boolean hasDependentHistory(UUID roomId) {
           return reservationRepository.existsByRoomId(roomId);
       }
   }
   ```
2. **Deactivation Guard**: Update `RoomService.deactivate(UUID id)` to check if active/future reservations exist:
   ```java
   boolean hasActiveOrUpcoming = reservationRepository.existsUpcomingOrActiveByRoomId(roomId, Instant.now());
   if (hasActiveOrUpcoming) {
       throw new ConflictException("Room has active or upcoming reservations; cancel them first.");
   }
   ```

### Rationale
Directly fulfills FR-020 and FR-021 and integrates cleanly with the existing design extension point in `RoomService`.
