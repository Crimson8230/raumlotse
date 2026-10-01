# Room Status Display Contract

**Feature**: `009-room-status-visualization`

## Existing data inputs

The display continues to use the existing read-only resources:

- `GET /api/rooms/{roomId}` for the room identity and room state.
- `GET /api/rooms/{roomId}/reservations` for the room's reservation records.

No new endpoint, request, response field, persistence table, or migration is required. The
existing reservation response must provide `roomId`, `startTime`, `endTime`, `status`, and
`reservedFor`.

## Derived display contract

| Derived status | Condition | Required presentation |
|---|---|---|
| `AVAILABLE` | No current `ACTIVE` reservation and no future `RESERVED` reservation | Green treatment and text `Available` |
| `RESERVED` | At least one future `RESERVED` reservation, with no current `ACTIVE` reservation | Yellow treatment and text `Reserved` |
| `OCCUPIED` | A current `ACTIVE` reservation exists | Red treatment and text `Reserved and Occupied` |
| `UNAVAILABLE` | Room or reservation data cannot be resolved | Error/unavailable message; never green |

`OCCUPIED` has precedence over `RESERVED`. A current `RESERVED` record without check-in does
not become occupied solely because its start time has passed; the existing lifecycle status is
authoritative.

## Next Reservation contract

When an eligible record exists, render one readable line containing the title and values in this
order:

`Next Reservation` · `Reserved for: <reservedFor>` · `Start Time: <formatted start time>` ·
`End Time: <formatted end time>`

Eligibility is restricted to the displayed room, `status == RESERVED`, and `startTime > now`.
Select the earliest start time, then the smallest reservation id for equal starts. If no
eligible record exists, render an explicit no-next-reservation message.

## Accessibility and freshness

- Every status color has a visible text label or equivalent semantic text.
- Room name, status, current date/time, and next-reservation fields remain available in the
  display without navigation.
- The status presentation appears above the current date/time.
- When a next reservation exists, its title and all required labels and values are presented on
  one readable line without overlap or hidden content at the intended display size.
- The display refreshes its reservation-derived state and clock on the existing 30-second
  cadence while mounted; a failed refresh produces the unavailable state rather than stale
  status claims.
