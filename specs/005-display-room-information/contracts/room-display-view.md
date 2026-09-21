# Room Display View Contract

**Branch**: `005-display-room-information` | **Date**: 2026-09-20 | [spec.md](../spec.md)

## Purpose

Define the user-visible contract for the read-only room display mockup. The view consumes the existing room and reservation response shapes; this document does not introduce a new REST endpoint.

## Inputs

- One room identifier from the display route/context.
- Existing room response containing at least `id`, `name`, and `status`.
- Existing reservation responses containing `id`, `roomId`, `startTime`, `endTime`, `status`, required `createdBy`, and nullable `note`.

## Current Reservation Rule

The displayed reservation is the eligible record satisfying:

```text
status ∈ { RESERVED, ACTIVE }
startTime <= currentTime < endTime
roomId == displayedRoom.id
```

If multiple records qualify, the view chooses the earliest start time, then the smallest reservation id. Terminal records (`COMPLETED`, `EXPIRED`, `CANCELLED`) are never shown as current.

## Upcoming Reservation Rule

The view selects the first eligible reservation satisfying all of the following:

```text
status ∈ { RESERVED, ACTIVE }
startTime > currentTime
localDate(startTime) == localDate(currentTime)
roomId == displayedRoom.id
```

The upcoming reservation is shown whether or not a current reservation exists. Reservations whose local calendar date is later than the current display date are not shown as upcoming.

## Rendered States

| State | Required visible content |
|---|---|
| Loading | A clear loading indicator |
| Reservation unavailable | Room/data unavailable message; no stale reservation details |
| No current reservation | Room name, current date/time, and explicit no-current-reservation message |
| Current reservation | Room name, current date/time, note or `No note provided`, `Booked by: <name>`, separately labeled start/end times, and same-day upcoming reservation when available |

## Note Presentation

- Preserve note text order, whitespace, and special characters where visible.
- Show the complete note when it fits the readable note area.
- When it does not fit, show a bounded preview with an explicit truncation indicator.
- If an upcoming reservation is available, reserve one line for its start and end times and reduce the note area first; a shortened note must use an explicit marker such as `...`.
- Do not render separate reservation-title or lecturer-name fields.
- Render the current reservation creator as `Booked by: <name>` using `createdBy`.

## Upcoming Presentation

- Label the section `Upcoming reservation`.
- Render its start and end time on one line.
- Render it even when there is no current reservation, provided a same-day eligible reservation exists.
- Do not render a reservation from a later local calendar day.

## Freshness and Accessibility

- Refresh the displayed clock at least every 30 seconds while mounted.
- Required information must be available without scrolling or navigation.
- Use semantic labels/headings and text content that remains understandable without color alone.
