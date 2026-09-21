# Research: Display Room Information

**Branch**: `005-display-room-information` | **Date**: 2026-09-20 | [spec.md](./spec.md)

## Decision 1: Reuse existing room and reservation APIs

- **Decision**: Load the room with the existing room client and load its reservations with `listRoomReservations(roomId)`. Do not add a backend endpoint or database migration.
- **Rationale**: `RoomDetailPage` already resolves a room, and the reservation response from feature 004 already contains `roomName`, `startTime`, `endTime`, and nullable `note`. The feature is a presentation of existing data.
- **Alternatives considered**: Adding a display-specific backend endpoint was rejected because it would duplicate an existing contract without adding data or authorization behavior.

## Decision 2: Select the current reservation from the time interval

- **Decision**: Treat a reservation as current when `startTime <= now < endTime` and its status is `RESERVED` or `ACTIVE`. Exclude `CANCELLED`, `COMPLETED`, and `EXPIRED` records. If multiple eligible records qualify because source data is inconsistent, select the one with the earliest start time and then the lexicographically smallest id as a deterministic tie-breaker.
- **Rationale**: The half-open interval matches feature 004 scheduling semantics. Terminal reservations must not be presented as live room usage, and deterministic selection prevents conflicting details on screen.
- **Alternatives considered**: Selecting the first API result was rejected because API ordering is not a safe conflict-resolution rule. Including terminal states was rejected because it can show stale or cancelled information as current.

## Decision 3: Keep the clock local to the display view

- **Decision**: Maintain a local current `Date` value and refresh it at least every 30 seconds while the view is mounted; clear the timer on unmount. Use the existing local date/time formatting conventions.
- **Rationale**: A local clock avoids a new backend dependency and provides a margin under the specification's 60-second freshness limit.
- **Alternatives considered**: Refreshing exactly every 60 seconds was rejected because scheduling drift could briefly exceed the requirement. Fetching server time was rejected because the feature has no server-time contract and does not need one.

## Decision 4: Bound long reservation notes

- **Decision**: Render the note in a fixed readable area. Show the full note when it fits; otherwise show a content-preserving preview with an explicit truncation indicator. Keep the existing reservation details as the place where the full note can be read.
- **Rationale**: The clarified requirement prohibits scrolling and requires readable content. Shrinking text to fit arbitrary 2,000-character notes would undermine the e-ink viewing goal.
- **Alternatives considered**: Showing all text with progressively smaller typography was rejected for readability. Scrolling or paginated display was rejected because required information must be available without interaction.

## Decision 5: Handle unresolved data explicitly

- **Decision**: Show loading while room/reservation data is being fetched, an unavailable-room/data state on failed or unresolved room data, and a no-current-reservation state when the room is known but no eligible reservation matches the current time. Never retain the previous reservation after a failed reload.
- **Rationale**: The display must not present stale reservation information as current, and the user needs a visible state for every data condition.
- **Alternatives considered**: Keeping the previous reservation during errors was rejected because it can mislead viewers about current room usage.

## Decision 6: Fix the visual validation target

- **Decision**: Validate the mockup at 800 × 480 CSS pixels with body text no smaller than 14 px.
- **Rationale**: A fixed viewport and minimum text size make the readability and five-second usability criteria reproducible across implementation and review.
- **Alternatives considered**: Leaving the display size unspecified was rejected because visual acceptance checks would otherwise be subjective. A larger 1024 × 600 target was rejected in favor of the selected compact display profile.

## Decision 7: Select the upcoming reservation by local calendar day

- **Decision**: Select the first eligible reservation whose start time is later than the display's current time and whose local calendar date matches the display's current local date. Use the same `RESERVED`/`ACTIVE` eligibility filter and deterministic ordering by start time, then id. Show the upcoming reservation whenever one exists, including when there is no current reservation.
- **Rationale**: Users need to know the next booking for the current day, but a booking tomorrow should not appear prematurely. Filtering by the browser/project local calendar date makes the day boundary explicit and keeps the display's meaning stable.
- **Alternatives considered**: Selecting the next reservation across all future dates was rejected because it violates the same-day requirement. Showing only upcoming reservations while a current reservation is absent was rejected because the display must also preview the next booking during an active reservation.

## Decision 8: Reserve upcoming space before truncating the note

- **Decision**: Allocate one line to the upcoming reservation's start and end times whenever it exists. Treat the note area as flexible: reduce its available height/preview first and append an explicit `...` marker when the full note no longer fits. Do not reduce body text below 14 px or scroll.
- **Rationale**: Upcoming information is a required display outcome when available, while the note can safely degrade to a clearly marked preview because its full value remains available in reservation details.
- **Alternatives considered**: Hiding upcoming content when the note is long was rejected because the clarification explicitly prioritizes the upcoming line. Shrinking all text or adding scrolling was rejected by the readability and no-interaction constraints.

## Decision 9: Display the current reservation creator

- **Decision**: Render the current reservation's required `createdBy` value as `Booked by: <name>` in the current reservation section. Do not add the creator to the one-line upcoming section, which is limited to start and end times.
- **Rationale**: The reservation response already supplies `createdBy`, so the display can expose the requested context without a new endpoint, persistence change, or data transformation boundary.
- **Alternatives considered**: Adding a backend display endpoint was rejected because the existing reservation contract already contains the field. Showing `createdBy` for upcoming reservations was rejected because the clarified upcoming format reserves one line for the relevant times.
