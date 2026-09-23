# Data Model: Room Device Control

## Reservation ownership extension

The existing `Reservation` remains the source for room, time interval, and lifecycle status. Add:

| Field | Type | Rules |
|---|---|---|
| `createdByUserId` | UUID, FK to user account | Required for new reservations; historical null values cannot authorize device control; immutable after creation |
| `createdBy` | existing text snapshot | Retained for display/audit; populated from the authenticated user's display name for new reservations |

Relationship: `Reservation.createdByUserId` identifies the only user allowed to control devices for that reservation. The room and user must both match the command request context.

## Equipment type extension

Add a stable catalog `code` to `EquipmentType`.

| Field | Type | Rules |
|---|---|---|
| `code` | uppercase string | Unique among equipment types; `PROJECTOR` identifies projector capability; other values remain ordinary catalog equipment |
| `name` | existing text | Display label; never used as the capability authorization key |
| `status` | existing enum | Only `ACTIVE` assigned equipment exposes projector control |

Room Management continues to represent equipment through its existing room-equipment relationship. A projector capability exists only if an active equipment type with code `PROJECTOR` is assigned to the room.

## RoomDeviceState

One row represents the last confirmed operational state of one device capability in one room.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Unique identifier |
| `roomId` | UUID, FK to room | Required; one-to-many from room |
| `kind` | enum | `LIGHTING`, `VENTILATION`, or `PROJECTOR`; unique together with `roomId` |
| `enabled` | boolean | Lighting and ventilation default to true; projector is enabled only while Room Management reports it present |
| `state` | boolean | Current last confirmed operational state, initially false/off |
| `updatedAt` | instant | Updated only after a successful command or capability synchronization |

Default capability behavior:

- Lighting and ventilation are available for every room, even if no explicit equipment row exists.
- Projector state is exposed only when an active `PROJECTOR` equipment assignment exists.
- Removing/deactivating the projector assignment hides the capability and rejects projector commands; its historical state may remain for audit/recovery but is not controllable.

## Authorization predicate

For room `R`, authenticated user `U`, current instant `T`, and reservation `B`, access is granted only if:

```text
B.room.id == R
AND B.createdByUserId == U.id
AND B.status == ACTIVE
AND B.startTime <= T
AND T < B.endTime
```

The predicate is evaluated for every capability read and every command. No client-provided reservation ID is trusted without rechecking this predicate.

## State transition behavior

1. Resolve room and requested device kind.
2. Resolve an eligible active reservation for the authenticated user and room at the current instant.
3. Resolve capability presence; projector presence is checked against active Room Management equipment.
4. Execute the device operation through the service seam.
5. Persist `state` and `updatedAt` only after successful acknowledgement.
6. On failure, return a structured problem and leave the previous state unchanged.
