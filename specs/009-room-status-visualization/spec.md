# Feature Specification: Room Status Visualization

**Feature Branch**: `009-room-status-visualization`

**Created**: 2026-10-07

**Status**: Draft

**Input**: User description: "Wenn ein Raum gerade frei und nicht reserviert ist, soll Verfügbar grün erscheinen. Wenn ein Raum gerade frei ist und eine anstehende Reservierung hat, soll weiterhin Verfügbar erscheinen. Während des Zeitraums einer Reservierung soll Reserviert erscheinen, solange noch kein Einchecken erfolgt ist. Wenn der Raum belegt ist, soll weiterhin Belegt erscheinen."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Current Room State at a Glance (Priority: P1)

As a person viewing a room display, I want the status to describe the room's current real-world state so that I can immediately decide whether it is available, reserved, or occupied.

**Why this priority**: The current state is the primary information users need when approaching a room.

**Independent Test**: Provide rooms that are free without a reservation, free before an upcoming reservation, within an unclaimed reservation period, and checked in/occupied. Verify the displayed text and color for each case.

**Acceptance Scenarios**:

1. **Given** a room is currently free and has no upcoming reservation, **When** the room display is viewed, **Then** it shows `Verfügbar` in green.
2. **Given** a room is currently free and has an upcoming reservation that has not started, **When** the room display is viewed, **Then** it still shows `Verfügbar` in green.
3. **Given** the current time is within a reservation period and nobody has checked in, **When** the room display is viewed, **Then** it shows `Reserviert` in yellow.
4. **Given** someone has checked in and the room is occupied, **When** the room display is viewed, **Then** it shows `Belegt` in red.
5. **Given** a status is communicated by color, **When** the display is viewed, **Then** the same status is also communicated by text or an equivalent non-color cue.

### User Story 2 - See the Next Reservation (Priority: P2)

As a person viewing a room display, I want to see the next reservation so that I know when the room will be needed next, even while the room is currently available.

**Why this priority**: Upcoming booking information remains useful without incorrectly changing the current availability status.

**Independent Test**: Provide a room with one or more future reservations and verify that the earliest eligible upcoming reservation is displayed with its person/designation and times.

**Acceptance Scenarios**:

1. **Given** a room has one future reservation, **When** the display is viewed, **Then** it shows one `Next Reservation` line containing `Reserved for:`, `Start Time`, and `End Time`.
2. **Given** a room has several future reservations, **When** the display is viewed, **Then** it shows the reservation with the earliest start time after the current time.
3. **Given** a room is currently available but has a future reservation, **When** the display is viewed, **Then** the status remains `Verfügbar` while the future reservation is shown separately.
4. **Given** a room has no eligible future reservation, **When** the display is viewed, **Then** the `Next Reservation` section communicates that no next reservation is scheduled.

### User Story 3 - Reflect State Changes Reliably (Priority: P1)

As a person relying on the room display, I want the status to change when time passes or a check-in occurs so that the display does not suggest an incorrect room state.

**Why this priority**: The distinction between upcoming, currently reserved, and occupied is time- and check-in-dependent.

**Independent Test**: Move a reservation through its start time and then record a check-in, refreshing according to the existing display behavior; verify each expected state transition.

**Acceptance Scenarios**:

1. **Given** a future reservation has not started, **When** its start time is reached and no check-in exists, **Then** the display changes from `Verfügbar` to `Reserviert`.
2. **Given** a room is `Reserviert` during an active reservation period, **When** a valid check-in is recorded, **Then** the display changes to `Belegt`.
3. **Given** an occupied reservation ends or is completed, **When** the display updates, **Then** the room no longer shows `Belegt` and shows `Verfügbar` unless another reservation is currently in progress and unclaimed.
4. **Given** a future reservation is cancelled or expired, **When** the display updates, **Then** it is excluded from the next-reservation selection and the status remains `Verfügbar` if no other current state applies.

### Edge Cases

- If reservation data cannot be loaded or the room cannot be resolved, the display MUST show a clear unavailable-data state and MUST NOT show a misleading `Verfügbar` status.
- If a reservation has started but has no check-in, the display MUST show `Reserviert`, not `Verfügbar` or `Belegt`.
- If a room has a future reservation but is currently free, the future reservation MUST NOT cause `Reserviert` to be shown before its start time.
- If overlapping reservations or conflicting check-in data exist, the display MUST use the occupied state when a valid current check-in exists; otherwise it MUST show `Reserviert` during the applicable reservation period.
- Reservations in terminal states such as `CANCELLED`, `EXPIRED`, or `COMPLETED` MUST NOT determine the current status or next reservation.
- If two eligible future reservations have the same start time, the display MUST choose one deterministically and MUST NOT show contradictory next-reservation values.
- If `Reserved for:` is missing for an otherwise valid reservation, the display MUST show a clear fallback value rather than an empty field.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The room display MUST present one clear current status for each displayed room.
- **FR-002**: The display MUST show `Verfügbar` in green when the room is currently free and no reservation is currently in progress, regardless of whether a future reservation exists.
- **FR-003**: The display MUST show `Reserviert` in yellow when the current time is within a valid reservation period and no check-in has been recorded for that reservation.
- **FR-004**: The display MUST show `Belegt` in red when a valid current check-in indicates that the room is occupied.
- **FR-005**: A future reservation whose start time has not been reached MUST NOT change the current status from `Verfügbar` to `Reserviert`.
- **FR-006**: The display MUST determine the current status from valid reservation data, the current date and time, and check-in state.
- **FR-007**: The display MUST give the occupied state precedence over the unclaimed-reservation state when both apply to the same current reservation.
- **FR-008**: The display MUST exclude `CANCELLED`, `EXPIRED`, and `COMPLETED` reservations from current-status and next-reservation calculations.
- **FR-009**: Each status color MUST have an accompanying text meaning or accessible label: `Verfügbar`, `Reserviert`, or `Belegt`.
- **FR-010**: The display MUST show a `Next Reservation` section for the earliest associated future reservation in `RESERVED` status whose start time is after the current time.
- **FR-011**: The `Next Reservation` presentation MUST contain `Reserved for:`, `Start Time`, and `End Time` together with their values.
- **FR-012**: The display MUST select the earliest future `RESERVED` reservation by start time and MUST NOT select an active, ended, cancelled, expired, or completed reservation as the next reservation.
- **FR-013**: If no eligible future reservation exists, the display MUST show an explicit no-next-reservation message and no stale reservation details.
- **FR-014**: The display MUST update status and next-reservation information according to the existing room display refresh behavior when time, reservation state, or check-in state changes.
- **FR-015**: The display MUST retain the room identity while showing the current status and next-reservation information.
- **FR-016**: The display MUST show an explicit unavailable-data state when required room or reservation data cannot be resolved.
- **FR-017**: The colored room-status presentation MUST appear above the current date and time in the room display hierarchy.
- **FR-018**: The `Next Reservation` presentation MUST remain readable at the intended display size without hiding required labels or values.

### Key Entities *(include if feature involves data)*

- **Room**: The room shown on the display, including its identity and current availability presentation.
- **Reservation**: A scheduled room booking with a lifecycle status, time window, and `reservedFor` designation.
- **Check-in**: The recorded event that indicates a reservation holder has arrived and the room is occupied.
- **Room Status**: The derived user-facing state `Verfügbar`, `Reserviert`, or `Belegt`.
- **Next Reservation**: The earliest eligible future reservation associated with the room, presented with its reserved-for value and start/end times.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of validation scenarios, a currently free room is shown as `Verfügbar` in green, both with and without an upcoming reservation.
- **SC-002**: In 100% of validation scenarios, a room within an unclaimed reservation period is shown as `Reserviert` in yellow.
- **SC-003**: In 100% of validation scenarios, a checked-in occupied room is shown as `Belegt` in red.
- **SC-004**: In 100% of status-transition scenarios, the displayed text and color agree with the current time and check-in state after the normal display update.
- **SC-005**: At least 90% of first-time viewers identify the room's current state within 5 seconds without opening another view.
- **SC-006**: In 100% of rooms with an eligible future reservation, one `Next Reservation` line shows the earliest reservation with `Reserved for:`, `Start Time`, and `End Time`.
- **SC-007**: In 100% of rooms without an eligible future reservation, the display shows an explicit no-next-reservation message and no stale reservation details.
- **SC-008**: In 100% of readability and accessibility checks, status meaning remains understandable without relying on color perception alone.
- **SC-009**: In 100% of intended-size layout checks, the status is above the current date and time and the next-reservation content remains readable.

## Assumptions

- Existing reservation lifecycle states are authoritative: `RESERVED` means booked but not checked in, while a valid check-in means the room is `Belegt`.
- A reservation becomes relevant to the current status at its start time, not when it is merely created or scheduled.
- A future reservation is eligible for `Next Reservation` only when it is associated with the room, has a start time after the current time, and is in `RESERVED` status.
- The existing room display refresh behavior is reused; a new real-time transport or notification feature is outside this scope.
- The existing `reservedFor` reservation field is the source for the value shown after `Reserved for:`.
- Reservation creation, editing, cancellation, check-in, and room-booking rules are outside this feature's scope.
- Exact shades, typography, and layout may follow the existing design system as long as the three required status meanings remain clear.
