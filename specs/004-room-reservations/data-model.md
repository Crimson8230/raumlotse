# Data Model: Room Reservations

**Branch**: `004-room-reservations` | **Date**: 2026-09-19 | **Spec**: [spec.md](./spec.md)

## Entity Relationship Diagram

```mermaid
erDiagram
    ROOM ||--o{ RESERVATION : "has"
    SEATING_ARRANGEMENT ||--o{ RESERVATION : "configured for"
    EQUIPMENT_TYPE ||--o{ RESERVATION_EQUIPMENT : "requested in"
    RESERVATION ||--o{ RESERVATION_EQUIPMENT : "includes"

    ROOM {
        uuid id PK
        string name
        uuid floor_id FK
        string status
        bigint version
    }

    SEATING_ARRANGEMENT {
        uuid id PK
        uuid room_id FK
        string name
        int max_capacity
    }

    EQUIPMENT_TYPE {
        uuid id PK
        string name
        string status
    }

    RESERVATION {
        uuid id PK
        uuid room_id FK
        uuid seating_arrangement_id FK
        timestamptz start_time
        timestamptz end_time
        string status
        int expected_attendees
        text note
        string created_by
        timestamptz created_at
        timestamptz updated_at
        bigint version
    }

    RESERVATION_EQUIPMENT {
        uuid reservation_id PK,FK
        uuid equipment_type_id PK,FK
    }
```

---

## Entities & Attributes

### 1. `Reservation`
Represents a scheduled booking of a room for a designated time window.

| Field | Type | Nullable | Constraints & Validation | Description |
|---|---|---|---|---|
| `id` | `UUID` | No | Primary Key, Auto-generated (v4 / random) | Unique identifier |
| `room` | `Room` (`ManyToOne`) | No | Foreign Key to `room.id`, indexed | The room being booked |
| `seatingArrangement` | `SeatingArrangement` (`ManyToOne`) | No | Foreign Key to `seating_arrangement.id`, indexed | Selected seating layout |
| `startTime` | `Instant` / `timestamptz` | No | `@NotNull`, `@Future`, before `endTime` | Booking start time (inclusive) |
| `endTime` | `Instant` / `timestamptz` | No | `@NotNull`, after `startTime` | Booking end time (exclusive) |
| `status` | `PersistentReservationStatus` (`String`) | No | `RESERVED` or `CANCELLED`, default `RESERVED` | Stored lifecycle state |
| `expectedAttendees` | `int` | No | `@Min(1)`, `<= seatingArrangement.maxCapacity` | Headcount expected |
| `note` | `String` | Yes | `@Size(max = 2000)` | Remarks / setup instructions |
| `createdBy` | `String` | No | `@NotBlank`, `@Size(max = 255)` | User name / identifier |
| `createdAt` | `Instant` / `timestamptz` | No | Auto-set on create | Administrative creation timestamp |
| `updatedAt` | `Instant` / `timestamptz` | No | Auto-set on update | Administrative update timestamp |
| `version` | `Long` | No | Optimistic lock version counter | Concurrency control |
| `additionalEquipment` | `List<EquipmentType>` (`ManyToMany`) | No | Active types, not in `room.equipmentTypes` | Extra equipment requested |

---

## Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> RESERVED : Created (now < startTime)
    RESERVED --> ACTIVE : now >= startTime and now < endTime
    ACTIVE --> EXPIRED : now >= endTime
    RESERVED --> CANCELLED : Explicit user cancellation
    ACTIVE --> CANCELLED : Explicit early termination / cancellation
    CANCELLED --> [*]
    EXPIRED --> [*]
```

### State Definitions
- **`RESERVED`**: Persistent status `RESERVED` where `Instant.now() < startTime`. Booking is confirmed and awaiting its start time.
- **`ACTIVE`**: Persistent status `RESERVED` where `startTime <= Instant.now() < endTime`. The meeting/reservation is currently ongoing.
- **`EXPIRED`**: Persistent status `RESERVED` where `Instant.now() >= endTime`. The scheduled window has completed.
- **`CANCELLED`**: Persistent status `CANCELLED`. Explicitly revoked; room is immediately released for new bookings during that window.

---

## Database Migration (`V4__create_reservation_tables.sql`)

```sql
CREATE TABLE reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES room (id),
    seating_arrangement_id UUID NOT NULL REFERENCES seating_arrangement (id),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status TEXT NOT NULL DEFAULT 'RESERVED',
    expected_attendees INT NOT NULL CHECK (expected_attendees > 0),
    note TEXT,
    created_by TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_reservation_time CHECK (end_time > start_time)
);

CREATE INDEX ix_reservation_room_id ON reservation (room_id);
CREATE INDEX ix_reservation_seating_arrangement_id ON reservation (seating_arrangement_id);
CREATE INDEX ix_reservation_room_time_status ON reservation (room_id, status, start_time, end_time);

CREATE TABLE reservation_equipment (
    reservation_id UUID NOT NULL REFERENCES reservation (id) ON DELETE CASCADE,
    equipment_type_id UUID NOT NULL REFERENCES equipment_type (id),
    PRIMARY KEY (reservation_id, equipment_type_id)
);

CREATE INDEX ix_reservation_equipment_equipment_type_id ON reservation_equipment (equipment_type_id);
```
