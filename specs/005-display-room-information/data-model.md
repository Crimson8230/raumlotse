# Data Model: Display Room Information

**Branch**: `005-display-room-information` | **Date**: 2026-09-21 | [spec.md](./spec.md)

This feature introduces a presentation model only. The persisted `Room` and `Reservation` entities remain defined by features 001 and 004.

## Source Entities

### Room

| Field | Source | Required for display | Rule |
|---|---|---:|---|
| `id` | Room response | Yes | Identifies the room display target |
| `name` | Room response | Yes | Displayed as the room heading |
| `status` | Room response | Yes | Used to identify an active/deactivated room state |

### Reservation

| Field | Source | Required for display | Rule |
|---|---|---:|---|
| `id` | Reservation response | Yes | Deterministic tie-breaker when source data conflicts |
| `roomId` | Reservation response | Yes | Must match the selected room |
| `startTime` | Reservation response | Yes | Inclusive interval boundary |
| `endTime` | Reservation response | Yes | Exclusive interval boundary |
| `status` | Reservation response | Yes | Only `RESERVED` and `ACTIVE` are eligible |
| `note` | Reservation response | No | Display as the note; use `No note provided` when missing |
| `createdBy` | Reservation response | Yes | Display as `Booked by: <name>` |

## Derived Display Model

`RoomDisplayViewModel` is derived in the frontend:

| Field | Type | Description |
|---|---|---|
| `roomName` | string | Resolved room name |
| `currentDateTime` | Date | Local clock value refreshed while mounted |
| `state` | `loading \| unavailable \| no-reservation \| reservation` | Display state |
| `reservationNote` | string | Reservation note or `No note provided` fallback |
| `bookedBy` | string | Creator name displayed as `Booked by: <name>` |
| `reservationStartTime` | string | Selected reservation start timestamp |
| `reservationEndTime` | string | Selected reservation end timestamp |

## Selection and Validation Rules

1. Room data is loaded for exactly one room id.
2. A reservation is eligible only when `roomId` matches the room, status is `RESERVED` or `ACTIVE`, and `startTime <= now < endTime`.
3. Among multiple eligible records, choose earliest `startTime`, then smallest `id` as a deterministic tie-breaker.
4. A null, empty, or whitespace-only note displays `No note provided`.
5. Failed data resolution clears any previous current reservation state and displays an unavailable state.

## Layout Rules

- Room name, `Booked by`, note, start time, and end time must remain visible without scrolling.
- Long room names, notes, or creator names wrap or use another visible layout treatment without overlap.
- Missing notes use an explicit `No note provided` fallback.
