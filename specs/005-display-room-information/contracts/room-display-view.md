# Room Display View Contract

**Branch**: `005-display-room-information` | **Date**: 2026-09-21 | [spec.md](../spec.md)

## Purpose

Define the user-visible contract for the read-only room display mockup. The view consumes the existing room and reservation response shapes; this document does not introduce a new REST endpoint.

## Inputs

- One room identifier from the display route/context.
- Existing room response containing at least `id`, `name`, and `status`.
- Existing reservation responses containing `id`, `roomId`, `startTime`, `endTime`, `status`, `createdBy`, and nullable `note`.

## Entry Point

- The room detail view shows `Display Room Information` directly beside `Edit Room`.
- Activating it navigates to `/rooms/:roomId/display` for the room currently shown in the detail view.

## Current Reservation Rule

The displayed reservation is the eligible record satisfying:

```text
status ∈ { RESERVED, ACTIVE }
startTime <= currentTime < endTime
roomId == displayedRoom.id
```

If multiple records qualify, the view chooses the earliest start time, then the smallest reservation id. Terminal records (`COMPLETED`, `EXPIRED`, `CANCELLED`) are never shown as current.

## Rendered States

| State | Required visible content |
|---|---|
| Loading | A clear loading indicator |
| Reservation unavailable | Room/data unavailable message; no stale reservation details |
| No current reservation | Room name, current date/time, and explicit no-current-reservation message |
| Current reservation | Room name, current date/time, `Booked by: <name>`, note or `No note provided`, and separately labeled start/end times |

## Reservation Field Presentation

- Render `Booked by`, `Note`, `Start time`, and `End time` as separate, clearly labeled values.
- Preserve note and creator values, including spaces and special characters.
- For a null, empty, or whitespace-only note, render `No note provided`.

## Freshness and Accessibility

- Refresh the displayed clock at least every 30 seconds while mounted.
- Required information must be available without scrolling or navigation.
- Use semantic labels/headings and text content that remains understandable without color alone.
