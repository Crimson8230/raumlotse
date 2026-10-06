# Data Model: Admin-Statistikbereich

Das Feature führt keine neuen persistenten Entitäten ein. Es definiert ein read-only-Ausgabemodell über die bestehenden Reservierungs-, Raum- und Ausstattungdaten.

## Query: Statistics Period

| Field | Type | Rules |
|---|---|---|
| `from` | local date | Required; inclusive; must be before `to` |
| `to` | local date | Required; exclusive; maximum range is implementation-validated to protect query cost |
| timezone | fixed `Europe/Berlin` | Converts local dates to `[fromInstant, toInstant)` |

The UI displays calendar dates. The backend interprets `from` at local midnight and `to` at the next local midnight in `Europe/Berlin`.

## Output: Statistics Snapshot

| Field | Meaning |
|---|---|
| `period` | Normalized requested dates and timezone |
| `summary` | Cross-cutting booking, cancellation and attendee metrics |
| `rooms` | One row per room relevant to the selected period, including zero-use rooms |
| `features` | One row per catalog feature, including unused features |

## Room Statistics

- `roomId`, `roomName`, `roomStatus`
- `bookingCount`: count of valid reservations overlapping the period
- `bookedSeconds`: sum of clipped overlap duration for valid reservations
- `utilizationPercent`: `bookedSeconds / periodSeconds * 100`; it uses the complete calendar duration of the selected period and is labeled accordingly
- Room identity is retained even when the room is currently deactivated or renamed.

## Feature Statistics

- `featureId`, `featureName`, `featureStatus`
- `bookingCount`: count of valid room reservations attributed to this feature
- `bookedSeconds`: clipped valid reservation duration attributed to this feature
- A reservation is counted once for every feature assigned to its room. It does not multiply the summary or room-level counts.
- Features without usage are returned with zero values.

## Summary Statistics

- `totalReservations`: all reservations overlapping the period, regardless of status, used as the cancellation denominator
- `validReservationCount`: reservations with status `RESERVED`, `ACTIVE` or `COMPLETED`
- `cancelledReservationCount`: reservations with status `CANCELLED`
- `cancellationRatePercent`: cancelled divided by total reservations; `null` when total is zero
- `attendeeSum`: sum of `expectedAttendees` for valid reservations with usable values
- `attendeeBookingCount`: valid reservations included in attendee average
- `averageExpectedAttendees`: `attendeeSum / attendeeBookingCount`; `null` when no valid attendee value exists
- `missingAttendeeCount`: valid reservations excluded because the participant value is unavailable in the source data

## Status Rules

| Reservation status | Room/feature usage | Attendee average | Cancellation count | Cancellation denominator |
|---|---:|---:|---:|---:|
| `RESERVED` | yes | yes | no | yes |
| `ACTIVE` | yes | yes | no | yes |
| `COMPLETED` | yes | yes | no | yes |
| `CANCELLED` | no | no | yes | yes |
| `EXPIRED` | no | no | no | yes |

## Relationships

```text
StatisticsPeriod
  └── StatisticsSnapshot
      ├── SummaryStatistics
      ├── RoomStatistics[*] ── Room ── Floor ── Building
      └── FeatureStatistics[*] ── EquipmentType
                                  ▲
Reservation ── Room ── room_equipment ┘
```

No statistical result exposes reservation IDs, user IDs, notes, email addresses, or free-text booking data.
