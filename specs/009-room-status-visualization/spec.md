# Feature Specification: Room Status Visualization

**Feature Branch**: `009-room-status-visualization`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "Die Display Room Information soll die aktuelle Reservierungssituation besser visualisieren damit Nutzer auf einem Blick sehen wie ein Raum belegt ist. Grün für Raum ist zur Reservierung frei. Gelb für Raum ist Reserviert. Rot für Raum ist reserviert und belegt. Zusätzlich soll am Raum angezeigt werden wann die nächste Reservierung gebucht ist. Die Reservierung soll als \"Next Reservation\" angezeigt werden, mit Reserved for: und Start und End Time"

## Clarifications

### Session 2026-10-01

- Q: Soll `Next Reservation` ausschließlich zukünftige Reservierungen im Status `RESERVED` berücksichtigen? → A: Ja. Nur zukünftige Reservierungen im Status `RESERVED` werden berücksichtigt; `ACTIVE`-Reservierungen gelten als aktuelle Belegung und werden nicht als nächste Reservierung angezeigt.
- Q: Wie soll die `Next Reservation` dargestellt werden? → A: Als eine einzelne Zeile mit `Next Reservation`, `Reserved for:`, `Start Time` und `End Time`.
- Q: Wo soll die farbliche Raumstatusdarstellung im Display stehen? → A: Oberhalb der aktuell angezeigten Uhrzeit und des Datums.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recognize Room Availability at a Glance (Priority: P1)

As a person viewing the room display, I want the room to have a clear color status so that I can immediately understand whether it is available, reserved, or currently occupied.

**Why this priority**: Immediate recognition of room availability is the primary purpose of the feature and supports quick decisions in front of the room.

**Independent Test**: Provide rooms with no relevant reservation, a future reservation, and an active reservation, then verify that each display uses the correct status color and accompanying status meaning.

**Acceptance Scenarios**:

1. **Given** a room with no current or upcoming reservation that prevents booking, **When** the room display is viewed, **Then** the room is shown in green as available for reservation.
2. **Given** a room with a future reservation in `RESERVED` status, **When** the room display is viewed, **Then** the room is shown in yellow as reserved.
3. **Given** a room with a current reservation in `ACTIVE` status, **When** the room display is viewed, **Then** the room is shown in red as reserved and occupied.
4. **Given** a room status is shown by color, **When** the display is viewed, **Then** a text label or legend also communicates the status so that the meaning is not dependent on color alone.

### User Story 2 - See the Next Reservation (Priority: P1)

As a person viewing a room display, I want to see the next reservation and the person it is reserved for so that I know when the room will be needed next.

**Why this priority**: The next booking explains a yellow or green transition and lets users assess whether a room can be used before the next reservation starts.

**Independent Test**: Provide a room with one or more future reservations and verify that the earliest eligible upcoming reservation is displayed as one readable line with all required labels and times.

**Acceptance Scenarios**:

1. **Given** a room has one future reservation, **When** the display is viewed, **Then** it shows one line titled `Next Reservation` containing `Reserved for:`, `Start Time`, and `End Time`.
2. **Given** a room has several future reservations, **When** the display is viewed, **Then** it shows the reservation with the earliest start time after the current time.
3. **Given** the room is currently occupied and has a later reservation, **When** the display is viewed, **Then** the status remains red and the later reservation is still shown on one `Next Reservation` line.
4. **Given** a room has no future reservation, **When** the display is viewed, **Then** the `Next Reservation` section communicates that no next reservation is scheduled rather than showing blank or stale data.

### User Story 3 - Understand Status Changes Reliably (Priority: P2)

As a person relying on the room display, I want the status and next reservation information to reflect the current reservation data so that I do not make decisions based on outdated information.

**Why this priority**: A room display is only useful if its status remains trustworthy as reservations start, end, are cancelled, or expire.

**Independent Test**: Change reservation state and time relative to the display, refresh or wait for the normal update, and verify that the status and next reservation change accordingly.

**Acceptance Scenarios**:

1. **Given** a future reservation reaches its start time and is active, **When** the room display updates, **Then** the status changes from yellow to red.
2. **Given** an active reservation ends or is completed, **When** the room display updates, **Then** it no longer treats that reservation as current and uses the next applicable reservation or green available status.
3. **Given** a future reservation is cancelled or expired, **When** the room display updates, **Then** that reservation is excluded from `Next Reservation` and the next eligible reservation is selected.

### Edge Cases

- If reservation data cannot be loaded or the room cannot be resolved, the display MUST show a clear unavailable-data state and MUST NOT show a misleading green status.
- If two eligible future reservations have the same start time, the display MUST choose one deterministically and MUST not show contradictory next-reservation values.
- Reservations in terminal states such as `CANCELLED`, `EXPIRED`, or `COMPLETED` MUST NOT be selected as the next reservation.
- If `Reserved for:` is missing for an otherwise valid reservation, the display MUST show a clear fallback value rather than an empty field.
- If a reservation has already started but remains in `RESERVED` status, the display MUST follow the existing reservation lifecycle status and MUST not silently infer occupancy without an active/check-in state.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The room display MUST present a clear visual status for each displayed room.
- **FR-002**: The display MUST use green for a room that has no current or upcoming eligible reservation preventing availability.
- **FR-003**: The display MUST use yellow for a room with an upcoming eligible reservation in `RESERVED` status.
- **FR-004**: The display MUST use red for a room with a current reservation in `ACTIVE` status, indicating that the room is reserved and occupied.
- **FR-005**: Each color status MUST have an accompanying text meaning or accessible label: available, reserved, or reserved and occupied.
- **FR-006**: The display MUST determine the room status from the room's eligible reservation data and the current date and time.
- **FR-007**: The display MUST exclude `CANCELLED`, `EXPIRED`, and `COMPLETED` reservations from current-status and next-reservation calculations.
- **FR-008**: The display MUST show a section titled `Next Reservation` for the earliest associated reservation in `RESERVED` status whose start time is after the current time.
- **FR-009**: The display MUST show `Next Reservation` as one line containing the labels `Reserved for:`, `Start Time`, and `End Time` together with their values.
- **FR-010**: The `Reserved for:` value MUST identify the person or designation stored for the selected reservation.
- **FR-011**: The display MUST select the earliest future `RESERVED` reservation by start time and MUST not select an `ACTIVE` or ended reservation as the next reservation.
- **FR-012**: If no eligible future reservation exists, the display MUST show an explicit no-next-reservation message.
- **FR-013**: The display MUST update status and next-reservation information when the underlying reservation situation changes, according to the existing room display refresh behavior.
- **FR-014**: The display MUST retain the room identity while showing the current status and next reservation information.
- **FR-015**: The display MUST show an explicit unavailable-data state when required room or reservation data cannot be resolved.
- **FR-016**: The status presentation MUST remain understandable for users who cannot distinguish the status colors by using text, symbols, or equivalent non-color cues.
- **FR-017**: The colored room-status presentation MUST appear above the current date and time in the room display hierarchy.
- **FR-018**: The one-line `Next Reservation` presentation MUST remain readable without wrapping, overlapping, or hiding any of its required labels and values at the intended display size.

### Key Entities *(include if feature involves data)*

- **Room**: The room shown on the display, including its identity and availability presentation.
- **Reservation**: A scheduled room booking with a lifecycle status, time window, and `reservedFor` designation.
- **Room Status**: The derived user-facing state `Available`, `Reserved`, or `Reserved and Occupied`.
- **Next Reservation**: The earliest eligible future reservation associated with the room, presented as one line containing the reserved-for value and start/end times.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of validation scenarios, rooms with no eligible current or upcoming reservation are shown as green and labeled available.
- **SC-002**: In 100% of validation scenarios, future `RESERVED` rooms are shown as yellow and current `ACTIVE` rooms are shown as red.
- **SC-003**: At least 90% of first-time viewers identify the room's availability state within 5 seconds without opening another view.
- **SC-004**: In 100% of rooms with an eligible future reservation, one `Next Reservation` line shows the earliest reservation with `Reserved for:`, `Start Time`, and `End Time`.
- **SC-005**: In 100% of rooms without an eligible future reservation, the display shows an explicit no-next-reservation message and no stale reservation details.
- **SC-006**: In 100% of status-transition validation scenarios, the displayed color and text state agree with the current reservation lifecycle state after the display's normal update.
- **SC-007**: In 100% of accessibility/readability checks, status meaning remains understandable without relying on color perception alone.
- **SC-008**: In 100% of display-layout checks, the colored room status is positioned above the current date and time.
- **SC-009**: In 100% of intended-size readability checks, the `Next Reservation` content remains a single readable line with no overlap or hidden required value.

## Assumptions

- Existing reservation lifecycle states are authoritative: `RESERVED` means booked but not checked in, and `ACTIVE` means checked in/currently occupied.
- A reservation is eligible for `Next Reservation` only when it is associated with the room, has a start time after the current time, and is in `RESERVED` status.
- The existing room display's refresh behavior is reused; a new real-time transport or notification feature is outside this scope.
- The existing `reservedFor` reservation field is the source for the value shown after `Reserved for:`.
- Reservation creation, editing, cancellation, check-in, and room booking rules are outside this feature's scope.
- The exact visual shade, typography, and layout may follow the existing room display design system as long as the three required status meanings remain clear.
- `Next Reservation` uses a compact inline layout; exact separators between its labeled values may follow the display design system as long as all labels remain visible.
