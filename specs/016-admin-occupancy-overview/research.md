# Research: Admin-Belegungsübersicht

All Technical Context items were known from the existing codebase; no NEEDS CLARIFICATION remained. The decisions below fix the open design points.

## R1 – Which bookings count as "current"

- **Decision**: Reuse the covering rule of `ReservationRepository.findCovering` (feature 009/014) for all rooms at once: a booking covers `now` when `now < endTime` and either `RESERVED` with `startTime <= now`, or `ACTIVE` with `coalesce(checkedInAt, startTime) <= now`. Checked-in persons and occupied rooms use only the `ACTIVE` subset; the running-bookings list uses both.
- **Rationale**: The overview then agrees with the room display and the read-only room status: a room counted as occupied here is shown as "occupied" there. Early check-in (feature 014) is covered via `checkedInAt`. `ACTIVE` bookings whose end has passed but that the sweep has not completed yet are excluded through `now < endTime` (spec edge case "Buchungsende").
- **Alternatives considered**: Status `ACTIVE` only, without a time condition. Rejected because the overview would keep counting ended bookings for up to a minute until the sweep runs, and would differ from the room display.

## R2 – Hourly forecast value

- **Decision**: For each local hour `[h, h+1)` in Europe/Berlin, collect all `RESERVED` and `ACTIVE` bookings whose effective interval `[coalesce(checkedInAt, startTime), endTime)` overlaps the hour. Expected persons = maximum of the summed `expectedAttendees` over all moments within the hour (sweep line over the start/end points clipped to the hour). Booked rooms = number of distinct rooms in that set. The booking list of the hour is the same set. Not-yet-checked-in `RESERVED` bookings count in full (clarified 2026-10-11). The same calculation runs once per building on that building's bookings, giving the hour × building grid (FR-014a), and once per floor for the floor groups of the hour's booking list (FR-016). Grouping and sorting happen in the backend so the page only renders.

## R8 – Where people are expected

- **Decision**: Booking list per hour grouped by building and floor, with expected persons per floor, sorted by most persons first (clarified 2026-10-11).
- **Rationale**: Answers "where are the 100 people at 14:00" with the data already in the response and no new UI component.
- **Alternatives considered**: Highlighting rooms on the floor maps of feature 012. Deferred to a separate feature by the product owner because of maintenance cost; it also only works for rooms that have a map position.
- **Rationale**: Matches FR-014. A sweep over at most a few hundred bookings per day is trivial in memory, and the logic stays a pure function that can be unit-tested without a database (Principle I).
- **Alternatives considered**: (a) Value at the full hour only. Rejected because it misses bookings starting at :15 or :30. (b) Sum over all overlapping bookings. Rejected because back-to-back bookings in one room would be counted twice. (c) SQL `generate_series` aggregation. Rejected as harder to test and not needed for this volume.

## R3 – Hours shown, DST and past hours

- **Decision**: The backend returns hours 07–21 (slots 07:00–08:00 … 21:00–22:00) plus every other local hour of the day that has at least one booking. Hours are built by stepping `ZonedDateTime` from local midnight, so 23- and 25-hour DST days are handled. For today, hours whose end is `<= now` are returned with `past: true` and no values. Days before today are rejected with 400.
- **Rationale**: Implements FR-015 and FR-017 server-side so the frontend only renders. Using `AdminStatisticsPeriod.ZONE` keeps the time zone consistent with feature 011.
- **Alternatives considered**: Let the frontend fill in hours. Rejected because the time-zone and DST logic would then live in two places.

## R4 – Building breakdown and filter

- **Decision**: An optional `buildingId` query parameter on both endpoints filters bookings by `room.floor.building.id`; an unknown id returns 404. The current snapshot always contains a `buildings` list: every active building (also with zeros), plus inactive buildings only if they have current values, so the sum of the list equals the totals.
- **Rationale**: FR-009 and FR-010 with one request. Inactive buildings are included only when needed, which keeps the edge case "deaktivierte Räume oder Gebäude" consistent without cluttering the list.
- **Alternatives considered**: A separate per-building endpoint. Rejected as an extra round trip for the same data.

## R5 – Access and privacy

- **Decision**: Add `GET /api/admin/occupancy(?:/forecast)?` to `RoleAccessFilter` with `PermissionCode.STATISTICS_READ`. Responses carry no `reservedFor`, `createdBy`, `note` or user ids, and are sent with `Cache-Control: no-store` like `/api/admin/statistics`.
- **Rationale**: FR-002 and FR-008. The existing filter already returns 401/403 for direct calls, and `ProtectedBusinessRouteTest`/`AuthorizationMatrixTest` can cover the new routes.
- **Alternatives considered**: A new permission "Belegung lesen". Rejected per the spec's assumptions; it would mean a catalog change in feature 015.

## R6 – Refresh

- **Decision**: The frontend polls the current snapshot every 30 seconds (same interval as `RoomDisplayPage`) and shows `generatedAt`. On a failed refresh it keeps the last data and shows a stale-data notice. The forecast loads on date or building change only.
- **Rationale**: Meets FR-006 (≤ 60 s) and FR-012 with the existing pattern; no push channel is needed.
- **Alternatives considered**: Server-Sent Events or WebSocket. Rejected (Principle V, YAGNI).

## R7 – Database indexes

- **Decision**: No migration. Both queries filter by time range and status across all rooms, and the existing `ix_reservation_room_time_status` leads with `room_id`.
- **Rationale**: The target scale (≤ 500 running bookings, 100 rooms, SC-006) is small. Feature 011 runs comparable unindexed range queries over a year. An index can be added if `quickstart.md` timing shows a need.
- **Alternatives considered**: An index on `(status, start_time, end_time)`. Deferred.
