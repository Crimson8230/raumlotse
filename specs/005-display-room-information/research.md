# Research: Display Room Information

**Branch**: `005-display-room-information` | **Date**: 2026-09-21 | [spec.md](./spec.md)

## Decision 1: Reuse existing room and reservation APIs

- **Decision**: Load the room with the existing room client and load its reservations with `listRoomReservations(roomId)`. Do not add a display-specific endpoint or schema fields.
- **Rationale**: `RoomDetailPage` already resolves a room and feature 004 already owns the reservation lifecycle, creator, note, and room relationship required by the display.
- **Alternatives considered**: Adding a display-specific endpoint was rejected because it would duplicate existing room/reservation access.

## Decision 2: Select the current reservation from the time interval

- **Decision**: Treat a reservation as current when `startTime <= now < endTime` and its status is `RESERVED` or `ACTIVE`. Exclude `CANCELLED`, `COMPLETED`, and `EXPIRED` records. If multiple eligible records qualify because source data is inconsistent, select the one with the earliest start time and then the lexicographically smallest id as a deterministic tie-breaker.
- **Rationale**: The half-open interval matches feature 004 scheduling semantics. Terminal reservations must not be presented as live room usage, and deterministic selection prevents conflicting details on screen.
- **Alternatives considered**: Selecting the first API result was rejected because API ordering is not a safe conflict-resolution rule. Including terminal states was rejected because it can show stale or cancelled information as current.

## Decision 3: Keep the clock local to the display view

- **Decision**: Maintain a local current `Date` value and refresh it at least every 30 seconds while the view is mounted; clear the timer on unmount. Use the existing local date/time formatting conventions.
- **Rationale**: A local clock avoids a new backend dependency and provides a margin under the specification's 60-second freshness limit.
- **Alternatives considered**: Refreshing exactly every 60 seconds was rejected because scheduling drift could briefly exceed the requirement. Fetching server time was rejected because the feature has no server-time contract and does not need one.

## Decision 4: Handle unresolved data explicitly

- **Decision**: Show loading while room/reservation data is being fetched, an unavailable-room/data state on failed or unresolved room data, and a no-current-reservation state when the room is known but no eligible reservation matches the current time. Never retain the previous reservation after a failed reload.
- **Rationale**: The display must not present stale reservation information as current, and the user needs a visible state for every data condition.
- **Alternatives considered**: Keeping the previous reservation during errors was rejected because it can mislead viewers about current room usage.

## Decision 5: Fix the visual validation target

- **Decision**: Validate the mockup at 800 × 480 CSS pixels with body text no smaller than 14 px.
- **Rationale**: A fixed viewport and minimum text size make the readability and five-second usability criteria reproducible across implementation and review.
- **Alternatives considered**: Leaving the display size unspecified was rejected because visual acceptance checks would otherwise be subjective. A larger 1024 × 600 target was rejected in favor of the selected compact display profile.

## Decision 6: Navigate from the room detail view

- **Decision**: Add a button or link labeled exactly `Display Room Information` directly beside `Edit Room` in each room's detail view. It navigates to `/rooms/:roomId/display` using the currently displayed room id.
- **Rationale**: This directly implements the clarification and prevents opening a display for a different room.
- **Alternatives considered**: Adding the entry point only to the room list was rejected because the clarification requires the respective room view. A global display selector was rejected because it introduces unnecessary room-selection state.
