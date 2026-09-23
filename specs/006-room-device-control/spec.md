# Feature Specification: Room Device Control

**Feature Branch**: `006-room-device-control`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "006-room-device-control Das neue Feature dient zur Steuerung der in dem jeweiligen Raum vorhandenen Beleuchtung, Lüftung und Projector. Das Feature soll nur denjenigen zur Verfügung stehen dem Benutzer stehen der den Raum gebucht hat. Außerdem muss die Reservierung bestätigt sein. Weiteres dürfen die Features nur während der Start und End Time dem Benutzer zur Verfügung gestellt werden, eine Steuerung der Features außerhalb der validen Buchungszeiten ist ausgeschlossen. Wir gehen davon aus das jeder Raum Standardmäßig über eine Beleuchtung und eine Lüftung verfügt, eine Beamersteuerung soll nur in Räumen möglich sein die Gemäß dem Room Management über einen Projector verfügen."

## Clarifications

### Session 2026-09-23

- Q: Soll ausschließlich der Status `ACTIVE` als bestätigte Reservierung gelten, die während `[startTime, endTime)` zur Gerätesteuerung berechtigt? → A: Ja – nur `ACTIVE` berechtigt zur Steuerung; `RESERVED` und alle terminalen Status sind ausgeschlossen.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Control Devices During an Eligible Reservation (Priority: P1)

As the user who booked a room, I want to control the room's available lighting and ventilation during my `ACTIVE` reservation so that I can operate the room without assistance.

**Why this priority**: Device control is the core value of the feature and must be useful for the default equipment available in every room.

**Independent Test**: Create an `ACTIVE` reservation for a room, sign in as its booking user, move the current time inside the reservation window, and verify that lighting and ventilation controls are available and that each requested device state change is reflected in the room.

**Acceptance Scenarios**:

1. **Given** an `ACTIVE` reservation for room A owned by user U and the current time is between its start and end time, **When** U opens the room controls, **Then** lighting and ventilation controls are shown and can be operated.
2. **Given** an `ACTIVE` reservation and a successful lighting or ventilation command, **When** the user views the room controls again, **Then** the current device state is displayed.
3. **Given** a reservation for room A owned by user U, **When** a different authenticated user opens room controls during the reservation window, **Then** the different user cannot view or operate the controls and receives an authorization error or an equivalent unavailable state.

---

### User Story 2 - Control a Room Projector When Present (Priority: P2)

As the user who booked a room, I want to control its projector when Room Management says that the room has one, so that presentation equipment is available when needed.

**Why this priority**: Projector control is valuable for presentation rooms, but it is conditional and must not appear for rooms without a projector.

**Independent Test**: Use one room configured with a projector and one room without one, create `ACTIVE` reservations for both, and verify that only the first room exposes projector control to its booking user.

**Acceptance Scenarios**:

1. **Given** an `ACTIVE` reservation for a room whose Room Management record includes a projector, **When** the booking user opens the controls during the reservation window, **Then** projector control is displayed and can be operated.
2. **Given** an `ACTIVE` reservation for a room whose Room Management record does not include a projector, **When** the booking user opens the controls, **Then** no projector control is displayed and projector commands are rejected.
3. **Given** a room whose projector configuration is removed or disabled before the reservation starts, **When** the booking user opens the controls, **Then** projector control is unavailable and the user receives a clear explanation.

---

### User Story 3 - Enforce Reservation and Time Boundaries (Priority: P1)

As the facility operator, I want device control to be restricted to the booking user, an `ACTIVE` reservation, and its valid time window so that rooms cannot be controlled by unauthorized users or outside booked hours.

**Why this priority**: Access and time restrictions are safety and authorization requirements; violating them would allow control of a room without a valid booking.

**Independent Test**: Attempt the same device command as the booking user before, during, and after an `ACTIVE` reservation, and repeat it with a non-`ACTIVE` reservation and a non-booking user. Only the in-window booking-user attempt must succeed.

**Acceptance Scenarios**:

1. **Given** an `ACTIVE` reservation whose start time has not arrived, **When** its booking user requests a device change, **Then** the command is rejected and no device state changes.
2. **Given** an `ACTIVE` reservation whose end time has passed, **When** its booking user requests a device change, **Then** the command is rejected and no device state changes.
3. **Given** a `RESERVED`, cancelled, expired, completed, or otherwise invalid reservation, **When** its associated user requests a device change, **Then** the command is rejected and no device state changes.
4. **Given** the exact start instant of an `ACTIVE` reservation, **When** its booking user requests a device change, **Then** the command is permitted; **given** the exact end instant, **when** the user requests a device change, **then** it is rejected. The valid interval is `[start, end)`.
5. **Given** a user attempts to control a room without an `ACTIVE` reservation for that room, **When** the command is submitted, **Then** it is rejected regardless of whether another user's reservation is currently active.

### Edge Cases

- A reservation spanning midnight remains controllable only from its start instant until, but not including, its end instant.
- A reservation with a start time equal to its end time is invalid and cannot grant device-control access.
- If the booking user has multiple overlapping `ACTIVE` reservations for the same room, the controls remain available only while at least one reservation grants access; no access is granted outside every applicable reservation window.
- If a device command fails or the physical device does not acknowledge the requested state, the user sees a failure message and the displayed state is not falsely reported as changed.
- If Room Management cannot provide a room's equipment configuration, projector control is withheld until the projector presence is known; lighting and ventilation remain governed by the room defaults.
- If the reservation is cancelled or becomes invalid while the controls are open, subsequent commands are rejected immediately.
- A user cannot gain control by changing a reservation identifier, room identifier, or device identifier in a request.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide room-device controls for lighting and ventilation for every room, because these devices are assumed to be standard room equipment.
- **FR-002**: The system MUST provide projector controls only when Room Management identifies an enabled projector as present in the selected room.
- **FR-003**: The system MUST identify the booking user for each reservation and MUST grant device-control access only to that user.
- **FR-004**: The system MUST require the reservation to have `ACTIVE` status before granting any device-control access; `RESERVED`, cancelled, expired, completed, and other non-active statuses MUST NOT grant access.
- **FR-005**: The system MUST permit device control only during the reservation interval `[startTime, endTime)`, including the start instant and excluding the end instant.
- **FR-006**: The system MUST evaluate the booking user's identity, reservation status, room association, and current time for every device-control command, not only when the controls are first displayed.
- **FR-007**: The system MUST reject commands made before the reservation start, at or after the reservation end, for a reservation without `ACTIVE` status, or by a user other than the booking user.
- **FR-008**: The system MUST ensure that rejected commands do not change the state of any room device.
- **FR-009**: The system MUST display the current state of each available device and update that state after a successful command.
- **FR-010**: The system MUST provide clear feedback when a device command is rejected because of authorization, reservation status, time window, device availability, or device failure.
- **FR-011**: The system MUST reject projector commands for rooms that do not have an enabled projector according to Room Management, even if a client submits a projector identifier manually.
- **FR-012**: The system MUST prevent a user from controlling devices in a room based solely on knowledge of a reservation or device identifier; access MUST be tied to the authenticated booking user and an `ACTIVE` reservation.
- **FR-013**: The system MUST stop accepting commands as soon as a reservation is cancelled, invalidated, or reaches its end time.

### Key Entities *(include if feature involves data)*

- **Room Device**: A controllable capability belonging to a room. Every room has lighting and ventilation; a projector exists only when configured by Room Management. It has an availability and current operational state.
- **Reservation**: A room booking with a room association, booking user, start and end times, and a confirmation/validity state that determines whether it can authorize control.
- **Booking User**: The authenticated user recorded as the owner/creator of the reservation. Only this user may control devices for that reservation.
- **Device Command**: A requested change to a specific available room device, evaluated against authorization, reservation validity, and the active time window.
- **Room Equipment Configuration**: The Room Management information that determines whether optional equipment, especially a projector, is present and available in a room.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of device commands issued by the booking user during an `ACTIVE` reservation's valid interval are evaluated and either executed or receive a specific device-failure response; no command is silently accepted.
- **SC-002**: 100% of commands issued by a non-booking user, for a reservation without `ACTIVE` status, or outside `[startTime, endTime)` are rejected, and none changes a device state.
- **SC-003**: In usability testing, at least 90% of booking users can locate and operate an available room device within 30 seconds of opening the room controls.
- **SC-004**: 100% of rooms expose lighting and ventilation controls, while 100% of rooms without a configured projector expose no projector control.
- **SC-005**: After a successful command, the resulting device state is shown to the booking user within 2 seconds in at least 95% of attempts.
- **SC-006**: When a reservation reaches its end time or is invalidated, all subsequent device commands are rejected within 1 second and no later than the next command evaluation.

## Assumptions

- Only an existing reservation with `ACTIVE` status is considered confirmed for this feature. `RESERVED` does not grant device-control access; the reservation lifecycle remains authoritative for this decision.
- User identity is available from the application's authentication context and can be matched to the booking user recorded on the reservation.
- Device control in this feature covers the operational state exposed by each device (for example, on/off); detailed device-specific modes, schedules, automation, and remote diagnostics are out of scope unless already supported by the room-device integration.
- Lighting and ventilation are modeled as available by default for every room; Room Management remains the source of truth for optional projector presence.
- Hardware integration or simulated device adapters are available to execute commands and report current state; this specification does not prescribe a particular communication technology.
- Device control is available through the room view for the booking user of an `ACTIVE` reservation; facility-wide administrative override controls are out of scope.
- All times use the reservation's configured application time zone and are compared with sufficient precision to enforce the exact start and end boundaries.
