# Data Model: Display Room Information

**Branch**: `005-display-room-information` | **Date**: 2026-09-20 | [spec.md](./spec.md)

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
| `note` | Reservation response | No | Show note or `No note provided`; bounded preview if too long |
| `createdBy` | Reservation response | Yes | Display as `Booked by: <name>` for the current reservation |

## Derived Display Model

`RoomDisplayViewModel` is derived in the frontend:

| Field | Type | Description |
|---|---|---|
| `roomName` | string | Resolved room name |
| `currentDateTime` | Date | Local clock value refreshed while mounted |
| `state` | `loading \| unavailable \| no-reservation \| reservation` | Display state |
| `reservationNote` | string | Full note value or `No note provided` fallback |
| `reservationNotePreview` | string | Readable bounded text, with explicit truncation marker when needed |
| `bookedBy` | string | Required creator name displayed as `Booked by: <name>` for the current reservation |
| `reservationStartTime` | string | Selected reservation start timestamp |
| `reservationEndTime` | string | Selected reservation end timestamp |
| `noteTruncated` | boolean | Whether the preview omits part of the full note |
| `upcomingReservation` | Reservation or null | First eligible reservation later on the current local calendar day |
| `upcomingStartTime` | string or null | Upcoming reservation start timestamp for the one-line section |
| `upcomingEndTime` | string or null | Upcoming reservation end timestamp for the one-line section |

## Selection and Validation Rules

1. Room data is loaded for exactly one room id.
2. A reservation is eligible only when `roomId` matches the room, status is `RESERVED` or `ACTIVE`, and `startTime <= now < endTime`.
3. Among multiple eligible records, choose earliest `startTime`, then smallest `id` as a deterministic tie-breaker.
4. A null, empty, or whitespace-only note displays `No note provided`.
5. A note that exceeds the display area is previewed and marked explicitly; it is not silently discarded.
6. An upcoming reservation is eligible when it belongs to the selected room, has status `RESERVED` or `ACTIVE`, starts after the current time, and has the same local calendar date as the current display time.
7. Among multiple eligible upcoming records, choose earliest `startTime`, then smallest `id`.
8. Failed data resolution clears any previous current or upcoming reservation state and displays an unavailable state.
9. The current reservation's `createdBy` value is required and is displayed as the `bookedBy` value; the upcoming reservation does not display creator information.

## Layout Rules

- When `upcomingReservation` is present, reserve one display line for its start and end time.
- Reduce the note area's available height before omitting the upcoming line.
- If the note no longer fits, show a readable preview with an explicit marker such as `...`; keep body text at least 14 px and do not add scrolling.
