# Research & Technical Decisions: Room Reservations

**Branch**: `004-room-reservations` | **Date**: 2026-09-19 | **Spec**: [spec.md](./spec.md)

## 1. Concurrency Control & Conflict Detection

### Decision
Use pessimistic write locking (`SELECT FOR UPDATE` via `@Lock(LockModeType.PESSIMISTIC_WRITE)`) on the target `Room` row during reservation creation and rescheduling checks, combined with a SQL overlap query:
```sql
WHERE room_id = :roomId
  AND status = 'RESERVED'
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

## 2. Reservation Lifecycle & Status Computation

### Decision
Store the persistent state in the database as either `RESERVED` or `CANCELLED`. Dynamically compute the temporal lifecycle state (`ACTIVE` or `EXPIRED`) at runtime upon querying or DTO mapping:
- If stored status is `CANCELLED` → `CANCELLED`
- Else if `Instant.now() < startTime` → `RESERVED`
- Else if `startTime <= Instant.now() < endTime` → `ACTIVE`
- Else (`Instant.now() >= endTime`) → `EXPIRED`

### Rationale
- **Zero Background Drift**: Avoids running a scheduled background daemon or cron job every minute to flip database rows between `RESERVED`, `ACTIVE`, and `EXPIRED`.
- **Absolute Precision**: The status is guaranteed to be 100% accurate at the exact millisecond of the query without polling latency.
- **Audit Preservation**: Explicit cancellations are persisted permanently as `CANCELLED`.

### Alternatives Considered
- *Scheduled Spring `@Scheduled` worker*: Requires polling the database every minute, introduces database write load, and creates temporal lag where reservations remain `RESERVED` minutes after their start time has elapsed.

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
