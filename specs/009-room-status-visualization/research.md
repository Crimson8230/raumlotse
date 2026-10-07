# Research: Room Status Visualization

**Feature**: [spec.md](spec.md)
**Date**: 2026-10-07

## Decision 1: Derive the current status from the current interval

- **Decision**: Keep derivation in `frontend/src/components/RoomDisplay/roomDisplayLogic.ts`. Filter reservations to the displayed room and the half-open interval `startTime <= now < endTime`; return `OCCUPIED` for a current `ACTIVE` record, `RESERVED` for a current `RESERVED` record, and `AVAILABLE` otherwise.
- **Rationale**: The existing response already provides the required lifecycle and time data. A future reservation must not make a currently free room look reserved.
- **Alternatives considered**: A backend-computed status would add an unnecessary endpoint/service boundary; deriving status from all future records reproduces the reported defect.

## Decision 2: Give check-in precedence

- **Decision**: When current `ACTIVE` and current `RESERVED` records overlap, `OCCUPIED` wins. A `RESERVED` record becomes `RESERVED` only while its interval is active and no current check-in is present.
- **Rationale**: The user-facing state must reflect actual occupancy when check-in exists.
- **Alternatives considered**: Sorting by start time alone could show `RESERVED` while the room is occupied.

## Decision 3: Keep future-reservation selection independent

- **Decision**: `selectNextReservation` continues to select the earliest future `RESERVED` record by start time and deterministic id tie-breaker. This selection does not affect the current status.
- **Rationale**: Users still need to see the next booking while the room remains currently available.
- **Alternatives considered**: Removing next-reservation information would exceed the requested scope and lose an existing feature.

## Decision 4: Preserve refresh and failure behavior

- **Decision**: Retain the 30-second clock/data refresh and show the existing unavailable state when required data cannot be loaded.
- **Rationale**: This is sufficient for the feature's freshness requirement without introducing real-time infrastructure.
- **Alternatives considered**: WebSocket/server-push would add unnecessary integration complexity.

## Decision 5: Use explicit German status labels and semantic cues

- **Decision**: Render `Verfügbar`, `Reserviert`, and `Belegt` as visible labels with the existing status classes and accessible status role. Update the available label from `Frei` to `Verfügbar`; retain existing layout and reservation labels.
- **Rationale**: This matches the requested terminology and prevents color-only interpretation.
- **Alternatives considered**: Color-only indicators or retaining `Frei` would not satisfy the requested wording.

## Unknowns resolved

No unresolved technical choices remain. No backend contract, database migration, or new dependency is required.
