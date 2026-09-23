# Feature Specification: Display Room Information

**Feature Branch**: `005-display-room-information`

**Created**: 2026-09-20

**Status**: Draft

**Input**: User description: "Display Room Information. We want to display relevant Data in a Mockup Screen View of an E-Ink Display. The relevant Data is received from the data-models of 004-room-reservations and via Foreign Keys from 001-room-management. The Screen should show the current date and time, the RoomName, Booked by, Note, Start time, and End time."

## Clarifications

### Session 2026-09-21

- Q: How should users reach the room display from room management? → A: From the respective room view via a button named "Display Room Information" positioned next to "Edit Room".
- Q: Which reservation details should the room display implement? → A: Only `Booked by`, `Note`, `Start time`, and `End time`; other reservation metadata is out of scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Current Room Reservation on E-Ink Screen (Priority: P1)

As a person approaching a room, I want to see the room identity, the current date and time, and the relevant reservation details on an e-ink display so that I can quickly confirm where and when an event takes place.

**Why this priority**: Showing the essential room and reservation information is the core value of the screen and enables people to identify the current room use without opening another application.

**Independent Test**: Provide a room with one relevant reservation and verify that the mockup screen presents all required fields with the expected values and readable labels.

**Acceptance Scenarios**:

1. **Given** an active room with a current reservation, **When** the screen view is opened, **Then** it displays the current date and time, room name, `Booked by`, `Note`, `Start time`, and `End time`.
2. **Given** reservation and room data linked to the same room, **When** the screen view renders, **Then** the displayed room name comes from the linked room record and the displayed reservation details come from the linked reservation record.
3. **Given** a reservation with a note or creator name containing spaces or special characters, **When** the screen view renders, **Then** the complete value is shown without being silently changed.
4. **Given** an administrator is viewing a specific room in room management, **When** they select the "Display Room Information" button next to "Edit Room", **Then** the read-only display view opens for that same room.

### User Story 2 - Recognize an Unoccupied Room (Priority: P2)

As a person approaching a room without a current reservation, I want the display to identify the room and indicate that no reservation is currently active so that I do not mistake stale information for a live booking.

**Why this priority**: A room display must remain trustworthy when the room is unoccupied or between reservations.

**Independent Test**: Provide an active room without a currently relevant reservation and verify that the room name and current date/time remain visible while the reservation area communicates that no current reservation is available.

**Acceptance Scenarios**:

1. **Given** an active room with no reservation covering the current time, **When** the screen view renders, **Then** it displays the room name and current date/time and shows an explicit no-current-reservation message.
2. **Given** a room reservation that has ended, **When** the screen view renders after its end time, **Then** the ended reservation is not presented as the current reservation.

### User Story 3 - Read the Screen at a Glance (Priority: P3)

As a person viewing the display from a short distance, I want the required information to have a clear visual hierarchy and readable formatting so that I can understand the room status quickly.

**Why this priority**: The value of an e-ink room display depends on legibility and quick comprehension in the physical environment.

**Independent Test**: Review the mockup at its intended display size and confirm that each required value has a distinct label, is not clipped, and can be identified without navigating or interacting with the screen.

**Acceptance Scenarios**:

1. **Given** a reservation with all required values, **When** a viewer looks at the mockup, **Then** each required value is visible with an unambiguous label and the start and end times are distinguishable.
2. **Given** a long room name, note, or creator name, **When** the screen view renders, **Then** the value remains readable through wrapping or another visible layout treatment and does not overlap another required field.

### Edge Cases

- If no room record can be resolved, the screen MUST show a clear unavailable-room state instead of presenting reservation data without a room identity.
- If no current reservation exists, the screen MUST show the room name and current date/time plus an explicit no-current-reservation message.
- If a reservation has a missing note, the screen MUST show a visible fallback such as "No note provided"; it MUST NOT show an empty unlabeled area.
- If the current date/time changes while the screen is being viewed, the displayed time MUST remain current according to the screen's refresh behavior and MUST NOT display a stale time indefinitely.
- If a room has multiple reservations, the screen MUST show only the reservation relevant to the current time; if more than one record qualifies due to inconsistent source data, it MUST use a deterministic result and avoid presenting conflicting details.
- Long values MUST remain readable without clipping, overlap, or obscuring any required field.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a read-only mockup screen view representing the room's e-ink display.
- **FR-002**: The screen MUST display the current date and current time using a consistent, human-readable format.
- **FR-003**: The screen MUST display the `RoomName` of the room associated with the display.
- **FR-004**: When a current reservation exists for the displayed room, the screen MUST display the creator with the label `Booked by`.
- **FR-005**: When a current reservation exists for the displayed room, the screen MUST display the reservation note with the label `Note`.
- **FR-006**: When a current reservation exists for the displayed room, the screen MUST display the reservation start time and end time as separate, clearly labeled values.
- **FR-007**: The screen MUST resolve the room identity from the room-management data associated with the reservation through the existing room relationship and MUST NOT display room information from an unrelated room.
- **FR-008**: The screen MUST use reservation data from the room-reservations domain for the displayed creator, note, start time, and end time.
- **FR-009**: The screen MUST identify the reservation relevant to the current date and time and MUST NOT present an ended or not-yet-started reservation as the current reservation.
- **FR-010**: If no current reservation exists, the screen MUST retain the room name and current date/time and display a clear no-current-reservation state.
- **FR-011**: The screen MUST provide visible fallback text for a missing optional reservation note.
- **FR-012**: The screen MUST keep all required fields readable at the intended mockup display size, including when room names, notes, or creator names are long.
- **FR-013**: The screen MUST be viewable without user interaction, scrolling, or navigation to another detail view to find any required field.
- **FR-014**: The screen MUST refresh its displayed current date and time often enough that the shown time is never more than 60 seconds behind the current time while the view remains open.
- **FR-015**: When source data cannot be resolved, the screen MUST show a clear data-unavailable state and MUST NOT present stale reservation information as current.
- **FR-016**: The room-management view for each room MUST provide a button labeled "Display Room Information" next to "Edit Room" that opens the read-only display view for the same room.

### Key Entities *(include if feature involves data)*

- **Room**: The room-management record associated with the display, including the room name used as the screen heading.
- **Reservation**: The room-reservations record that supplies the creator, note, start time, and end time.
- **Room Display View**: The read-only presentation of current time, room identity, and the relevant reservation or no-reservation state for one room.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of test cases with valid linked room and reservation data, the screen displays the room name, creator, note, start time, and end time from the matching records.
- **SC-002**: A viewer can identify the room and determine whether a current reservation exists within 5 seconds in at least 90% of usability checks.
- **SC-003**: While the view remains open, the displayed current date and time are no more than 60 seconds behind the actual current time in 100% of verification checks.
- **SC-004**: In 100% of no-current-reservation test cases, the screen shows the room name and current date/time and does not show an ended or future reservation as current.
- **SC-005**: In 100% of readability checks at the intended mockup display size, all required labels and values are visible, non-overlapping, and not clipped.
- **SC-006**: In 100% of missing-data and unresolved-link test cases, the screen shows an explicit fallback or unavailable state rather than blank or misleading content.

## Assumptions

- The display is associated with exactly one room for the purpose of this mockup.
- "Current reservation" means a reservation whose time interval includes the current time; an interval beginning at the start time and ending at the end time is treated as active until the end time.
- The reservation data model already contains, or will expose for this feature, the creator and note in addition to its start and end times.
- The room-management and room-reservations records are linked through the existing room relationship/foreign key defined by the earlier features.
- This feature is read-only: creating, editing, cancelling, or otherwise managing reservations is outside its scope.
- A consistent local project date/time convention will be used for display, while the underlying records remain the source of truth for reservation timing.
- The visual treatment may use static mockup content and representative empty/error states, but it must demonstrate the required data fields and states without requiring a physical e-ink device.
