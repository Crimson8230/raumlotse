# Room Status Display Contract

**Feature**: `009-room-status-visualization`

## Existing data inputs

The display continues to use the existing read-only resources:

- `GET /api/rooms/{roomId}` for room identity and validity.
- `GET /api/rooms/{roomId}/reservations` for reservation interval, lifecycle status, and display details.

No endpoint, response field, table, migration, or backend status calculation is added.

## Derived display contract

| Derived status | Condition | Required presentation |
|---|---|---|
| `AVAILABLE` | No current `ACTIVE` or `RESERVED` reservation in `[startTime, endTime)` | Green treatment and visible text `Verfügbar` |
| `RESERVED` | Current `RESERVED` reservation in `[startTime, endTime)` and no current `ACTIVE` check-in | Yellow treatment and visible text `Reserviert` |
| `OCCUPIED` | Current `ACTIVE` reservation in `[startTime, endTime)` | Red treatment and visible text `Belegt` |
| `UNAVAILABLE` | Room or reservation data cannot be resolved | Error/unavailable message; never green |

`OCCUPIED` has precedence over `RESERVED`. A future `RESERVED` record does not change
`AVAILABLE` before its start time.

## Next reservation contract

The existing next-reservation presentation remains independent from the current status. An
eligible record is restricted to the displayed room, `status == RESERVED`, and `startTime > now`.
Select the earliest start time, then the smallest reservation id for equal starts. If no eligible
record exists, render the existing explicit no-next-reservation message.

## Accessibility and freshness

- Every status color has visible semantic text.
- Room name, status, and current date/time remain available without navigation.
- The status appears above the current date/time.
- The existing 30-second clock and reservation refresh cadence is retained.
- Failed loading produces the unavailable state rather than stale or green status claims.
