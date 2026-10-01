# Research: Room Status Visualization

**Feature**: [spec.md](spec.md)
**Date**: 2026-10-01

## Decision 1: Derive display status in the existing frontend display logic

- **Decision**: Keep status and next-reservation derivation in the existing
  `frontend/src/components/RoomDisplay/roomDisplayLogic.ts` boundary and pass a view model to
  `RoomDisplay`.
- **Rationale**: The page already loads the room and its reservations, and the feature is a
  presentation rule. This avoids a new endpoint and keeps the rules directly unit-testable.
- **Alternatives considered**: A backend-computed display-status endpoint would add a service
  boundary and contract without requiring new source data; deriving status in CSS would make
  lifecycle and time rules difficult to test.

## Decision 2: Use explicit status precedence and half-open time windows

- **Decision**: Evaluate a current `ACTIVE` reservation first, then a future `RESERVED`
  reservation, otherwise mark the room available. Current reservations use the existing
  half-open interval `startTime <= now < endTime`.
- **Rationale**: Red must win when a room is occupied even if another booking is later. The
  interval matches the existing reservation conflict and display behavior, preventing an
  ended booking from remaining current.
- **Alternatives considered**: Sorting all reservations without status precedence could show
  yellow while the room is occupied; inclusive end times would create overlap at back-to-back
  reservations.

## Decision 3: Select only future `RESERVED` records for `Next Reservation`

- **Decision**: Filter by displayed room, status `RESERVED`, and `startTime > now`; sort by
  start time and then reservation id for deterministic ties.
- **Rationale**: This directly records the clarification answer. `ACTIVE` means current
  occupancy and must not be presented as a future booking.
- **Alternatives considered**: Including every non-terminal status could expose an inconsistent
  future `ACTIVE` record as the next booking and weaken the lifecycle contract.

## Decision 4: Refresh reservation data with the existing 30-second display cadence

- **Decision**: Keep the current clock interval and use the same cadence to refresh the room's
  reservation data while the display is mounted; retain the existing unavailable state on
  failed refreshes rather than showing stale reservation details as current.
- **Rationale**: The specification requires status changes to be reflected and existing code
  already establishes a 30-second display update cadence, which keeps the implementation small.
- **Alternatives considered**: WebSocket/server push would introduce a new integration and is
  unnecessary for the stated 60-second freshness target.

## Decision 5: Make status understandable without color

- **Decision**: Render a semantic status label alongside a status class/visual treatment:
  `Available`, `Reserved`, or `Reserved and Occupied`. Use accessible text and existing CSS
  conventions; no new visual library is needed.
- **Rationale**: It satisfies the specification's non-color requirement and remains legible on
  the existing display mockup.
- **Alternatives considered**: Color-only indicators are insufficient for accessibility and
  ambiguous in low-contrast or monochrome display contexts.

## Decision 6: Put status before the clock and keep the next reservation inline

- **Decision**: Render the status block before the current date/time in the display hierarchy.
  Render `Next Reservation`, `Reserved for:`, `Start Time`, and `End Time` as one compact,
  non-wrapping line, using the existing display width and typography rules.
- **Rationale**: The status is the primary at-a-glance signal, and the clarification requires
  the next reservation to be readable as one line without losing any required label or value.
  This is a presentation-only change and does not alter the reservation contract.
- **Alternatives considered**: Keeping status after the clock weakens the visual priority;
  separate paragraphs or a definition list violate the clarified one-line requirement.

## Unknowns resolved

No unresolved technical choices remain for planning. Existing API response fields include room
identity, reservation status, time window, and `reservedFor`; no migration or API version change
is required.
