# Data Model: Admin-Belegungsübersicht

No persistent entities are added or changed and no migration is needed. The feature reads `Reservation → Room → Floor → Building` and returns two read-only response models.

## Source data (existing)

| Entity | Fields used | Notes |
|---|---|---|
| Reservation | id, room, startTime, endTime, status, expectedAttendees, checkedInAt | `reservedFor`, `createdBy`, `createdByUserId`, `note` are never read into the responses (FR-008). |
| Room | id, name, status, floor | Inactive rooms are still counted (edge case). |
| Floor | id, name, building | |
| Building | id, name, status | Filter target and breakdown key. |

## Derived rules

- **Effective start** = `coalesce(checkedInAt, startTime)` for `ACTIVE`, otherwise `startTime`.
- **Covers now** (research R1): `now < endTime` and effective start `<= now`, with status `RESERVED` or `ACTIVE`.
- **Checked-in persons** = Σ `expectedAttendees` of covering `ACTIVE` bookings.
- **Occupied rooms** = |distinct room ids| of covering `ACTIVE` bookings.
- **Hour value** (research R2): bookings with status `RESERVED` or `ACTIVE` whose `[effective start, endTime)` overlaps `[hourStart, hourEnd)`. `expectedPersons` = max simultaneous Σ `expectedAttendees` within the hour; `bookedRooms` = |distinct room ids|.
- **Hour value per building**: the same rule applied to the bookings of one building (`room.floor.building`). The hour total is computed over all bookings, not summed from building values (equal in practice, since a booking belongs to exactly one building).
- **Peak** = all non-past hours whose `expectedPersons` equals the day's maximum, provided the maximum is > 0; evaluated separately for the total and for each building.

## Response model: OccupancySnapshot (current situation)

| Field | Type | Rule |
|---|---|---|
| generatedAt | instant | Server `Clock` time used for the evaluation (FR-006). |
| buildingId | uuid \| null | Applied filter, or null for all buildings. |
| totals.checkedInPersons | int ≥ 0 | FR-003 |
| totals.occupiedRooms | int ≥ 0 | FR-004 |
| buildings[] | BuildingOccupancy | Active buildings plus inactive buildings with values > 0; Σ equals totals (FR-009). With a filter, only that building. Sorted by name. |
| runningBookings[] | RunningBooking | Covering `RESERVED` + `ACTIVE` bookings (FR-007), sorted by building, room, start. |

**BuildingOccupancy**: buildingId, buildingName, buildingActive (bool), checkedInPersons, occupiedRooms.

**RunningBooking / BookingEntry** (shared shape): reservationId, roomId, roomName, buildingId, buildingName, floorId, floorName, startTime, endTime, expectedAttendees, checkInStatus (`CHECKED_IN` for `ACTIVE`, `NOT_CHECKED_IN` for `RESERVED`), checkedInAt (instant \| null).

## Response model: OccupancyForecast (day view)

| Field | Type | Rule |
|---|---|---|
| date | local date | Requested day, today or later; earlier → 400 (FR-017). |
| zone | string | `Europe/Berlin` |
| generatedAt | instant | |
| buildingId | uuid \| null | Applied filter (FR-018). |
| peakExpectedPersons | int \| null | Day maximum over non-past hours; null if every hour is past. |
| buildings[] | ForecastBuilding | Column headers: active buildings plus inactive buildings with bookings that day, sorted by name; only the filtered building when `buildingId` is set (FR-014a). |
| hours[] | HourForecast | 07–21 plus any other hour with bookings, ascending (FR-015). |

**ForecastBuilding**: buildingId, buildingName, buildingActive, peakExpectedPersons (int \| null).

**HourForecast**: start (instant), end (instant), localLabel (`HH:mm`), past (bool), expectedPersons (int \| null, null when past), bookedRooms (int \| null), peak (bool), buildings[] (HourBuildingValue, one per entry of the forecast's `buildings`, same order), floors[] (FloorGroup, empty when past) (FR-014a, FR-016).

**FloorGroup**: buildingId, buildingName, floorId, floorName, expectedPersons (hour rule applied to this floor's bookings), bookings[] (BookingEntry, sorted by expectedAttendees desc, then room name). Groups are sorted by expectedPersons desc, then buildingName, floorName. Only floors with bookings in that hour appear.

**HourBuildingValue**: buildingId, expectedPersons (int \| null), bookedRooms (int \| null), peak (bool).

Both `RESERVED` (not yet checked in) and `ACTIVE` bookings count in full.

## Validation

- `date`: ISO date, required; `date < today (Europe/Berlin)` → 400 Problem with a hint to the statistics area.
- `buildingId`: optional UUID; malformed → 400, unknown → 404.
- No write operations (FR-013).
