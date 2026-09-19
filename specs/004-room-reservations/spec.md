# Feature Specification: Room Reservations

**Feature Branch**: `004-room-reservations`

**Created**: 2026-09-19

**Status**: Draft

**Input**: User description: "We add a new feature to this project: the ability to reserve/book a room. So from the room-view we want to be able to create a new reservation for a given time/duration. It can only be booked if there is no conflicting reservation. reservations can have these states: reserved (initial), cancelled, active and expired. as part of a reservation user needs to specify which seating arrangement he wants to have deployed. he can reserve additional equipment that is not present in that room (e.g. microphones). user has to specify for how many persons are expected. user can specify a longtext (note). we want to store administrative data like which user created that reservation (an when)."

## Clarifications

### Session 2026-09-19

- Q: Should users be allowed to edit an existing reservation (e.g., reschedule times, change seating arrangement, or update notes), or must they cancel and create a new reservation? → A: Metadata edits only: Users can update notes and expected attendee count (validated against the configured seating arrangement's capacity). Changing time window, room, or seating arrangement requires cancelling and re-booking.
- Q: How should the system capture the creator's identity (`createdBy`) during room reservation while the application lacks a centralized login system? → A: Form input field: The booking form includes a required text field for the user's name or institutional email/ID, which is validated as non-blank and stored as `createdBy`.
- Q: What should happen to existing future reservations when an administrator deactivates a room? → A: Prevent room deactivation: An administrator cannot deactivate a room if upcoming `RESERVED` or `ACTIVE` bookings exist; those reservations must be cancelled first.
- Q: Should consecutive reservations allow immediate back-to-back occupancy with zero buffer, or must the system enforce a mandatory setup/turnover buffer between different bookings? (CHK001) → A: Strict zero-gap half-open intervals `[start, end)` without mandatory buffer for current implementation (Option A); conflict detection service logic must isolate buffer calculation to allow future turnover buffers (e.g. when seating arrangement changes) without structural rework.
- Q: Is the `CANCELLED` status a permanent terminal state (prohibiting un-cancellation), or can users restore/reactivate a cancelled reservation if the time slot is still free? (CHK003) → A: Permanent terminal states: Both `CANCELLED` and `EXPIRED` are irreversible terminal states. Once a reservation is cancelled or expired, it cannot be reactivated, extended, or modified, and is permanently excluded from conflict detection for new bookings.
- Q: Must every reservation mandate selecting exactly one of the room's configured seating arrangements, and how should single-arrangement rooms be handled? (CHK005) → A: Mandatory single selection with auto-select: Exactly one configured seating arrangement belonging to the room is required for every reservation. If the target room has only one configured arrangement, the booking form pre-selects it by default.
- Q: Should the expected attendee count strictly accept any positive integer (minimum 1), and how should non-positive or missing inputs be handled? (CHK007) → A: Strict positive integer (`>= 1`): Any integer satisfying `1 <= expectedAttendees <= maxCapacity` is valid. Values <= 0, decimals, non-numeric strings, or blank inputs are rejected with a validation error.
- Q: How should equipment already permanently installed in a room be presented and excluded during reservation creation? (CHK008) → A: Omit from selector with empty-state notice: Active catalog equipment types already assigned to the room are strictly omitted from the additional equipment selection list. If all catalog equipment types are permanently present in the room, the reservation form displays an informative notice ("All catalog equipment is already present in this room") with no checkboxes.
- Q: How should the system handle deactivated catalog equipment types during reservation creation and when viewing existing reservations? (CHK009) → A: Exclude from booking and preserve history: Deactivated equipment types are omitted from the selection UI and rejected with a 400 Bad Request error if submitted via API. Existing reservations that include previously booked deactivated equipment display the equipment names intact to preserve audit integrity.
- Q: Should additional equipment requests be captured purely as selected equipment types without quantities, or should users specify a requested quantity for each selected equipment type? (CHK010) → A: Type-only checkboxes: Additional equipment is selected purely as distinct equipment types (checkboxes) without quantity inputs. Inventory stock tracking and quantity allocations are explicitly out of scope for this version.
- Q: How should the exact temporal boundaries between the four lifecycle states (RESERVED, ACTIVE, EXPIRED, CANCELLED) be objectively evaluated? (CHK011) → A: Persisted database status with manual operational buttons: All four lifecycle states (RESERVED, ACTIVE, EXPIRED, CANCELLED) are stored and persisted directly in the database. In this version, status transitions are operated manually via action buttons on each reservation: "Activate / Check-In" transitions from RESERVED to ACTIVE, "Complete / Expire" transitions from ACTIVE to EXPIRED, and "Cancel" transitions to CANCELLED. Automatic scheduled background status transitions are deferred as a future feature.
- Q: Should the system introduce a distinct COMPLETED status to distinguish successfully held meetings from unattended expired reservations? (Lifecycle Statuses) → A: Add COMPLETED status: The system supports 5 lifecycle states (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`). `ACTIVE` reservations transition to `COMPLETED` when concluded/checked-out, while `RESERVED` reservations that lapse without check-in transition to `EXPIRED` (no-show). Both `COMPLETED` and `EXPIRED` are terminal states alongside `CANCELLED`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create Room Reservation with Conflict Prevention (Priority: P1)

A user viewing a specific room needs to book it for a defined time window and duration. During booking, the user selects one of the room's available seating arrangements, specifies the expected attendee count, provides an optional longtext note, and enters their identity (name or institutional ID) into a required field. The system validates that the room is active, that the requested time slot has no conflicting bookings for the same room, that the expected attendees fit within the chosen seating arrangement's capacity, and that creator details are provided. Upon confirmation, the reservation is created in `RESERVED` status.

**Why this priority**: Core value of the feature. Without the ability to create conflict-free bookings, no other reservation capability can function.

**Independent Test**: Navigate to an active room view, book a future time slot with a valid seating arrangement, attendee count, and creator name, verify that the reservation is created in `RESERVED` state, and verify that a second overlapping booking attempt for that room is blocked.

**Acceptance Scenarios**:

1. **Given** an active room with configured seating arrangements, **When** a user submits a reservation with a future start time, valid duration, an expected attendee count within the selected seating arrangement's capacity, and non-blank creator user details, **Then** the reservation is created in `RESERVED` status and displays the creator identity and creation timestamp.
2. **Given** a room with an existing active or reserved booking from 10:00 to 11:30, **When** a user attempts to book the same room from 11:00 to 12:00, **Then** the system rejects the booking with an error indicating a scheduling conflict.
3. **Given** a room with an existing booking from 10:00 to 11:00, **When** a user submits a booking for that room starting at exactly 11:00, **Then** the booking succeeds without conflict under zero-gap half-open intervals `[start, end)`.
4. **Given** a room with a seating arrangement having a maximum capacity of 20, **When** a user enters 25 expected attendees, **Then** the system rejects the booking with a capacity validation error.
5. **Given** a reservation submission with a blank or missing creator identity, **When** submitted, **Then** the system rejects the submission with a validation error.

---

### User Story 2 - Reserve Additional Equipment Not Present in Room (Priority: P2)

When reserving a room, the user needs to request additional portable equipment (such as microphones or presentation devices) that are not already part of the room's permanent equipment inventory, ensuring equipment is prepared for the reservation.

**Why this priority**: Organizers frequently need supplementary gear beyond the room's baseline equipment for meetings and presentations.

**Independent Test**: Select a room that already has a projector assigned. Verify the additional equipment selector only offers equipment types not already assigned to that room. Select an additional equipment type (e.g., "Microphone"), create the reservation, and confirm it appears in the reservation details.

**Acceptance Scenarios**:

1. **Given** a room with "Projector" already assigned as built-in equipment, **When** the user opens the reservation form, **Then** "Projector" is strictly omitted from the additional equipment selection list and unassigned active equipment types (e.g., "Microphone") are available.
2. **Given** a room that already has all active catalog equipment types assigned as permanent equipment, **When** the user opens the reservation form, **Then** no equipment checkboxes are displayed and an informative notice ("All catalog equipment is already present in this room") is shown.
3. **Given** a user selects one or more additional equipment items, **When** the reservation is submitted, **Then** the reservation persists and displays the requested additional equipment list.

---

### User Story 3 - View Room Reservation Schedule and Lifecycle States (Priority: P3)

Users and facility managers viewing a room need to see all scheduled reservations and their current lifecycle state (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`), along with administrative details (who booked it and when). Users can also update metadata (notes, expected attendees) on upcoming reserved bookings.

**Why this priority**: Provides visibility into room occupancy, past usage, and administrative audit trails, while allowing minor headcount and note adjustments without re-booking.

**Independent Test**: View a room's reservations and verify that upcoming bookings display as `RESERVED`, currently ongoing bookings display as `ACTIVE`, finished meetings display as `COMPLETED`, and no-shows display as `EXPIRED`. Verify that editing notes or attendee count on a `RESERVED` booking updates the record.

**Acceptance Scenarios**:

1. **Given** an existing reservation in `RESERVED` status, **When** an authorized user triggers the manual "Activate / Check-In" action, **Then** its persisted status in the database transitions to `ACTIVE`.
2. **Given** an existing reservation in `ACTIVE` status, **When** an authorized user triggers the manual "Complete / Check-Out" action, **Then** its persisted status in the database transitions to `COMPLETED`.
3. **Given** an existing reservation in `RESERVED` status that passed without check-in, **When** an authorized user triggers the "Expire / Mark No-Show" action, **Then** its persisted status in the database transitions to `EXPIRED`.
4. **Given** an existing reservation in `COMPLETED`, `EXPIRED`, or `CANCELLED` status, **When** viewing its details, **Then** operational action buttons (Activate, Complete, Expire, Cancel) are disabled or hidden as these are permanent terminal states.
5. **Given** any existing reservation, **When** viewing its details, **Then** the creator username/identifier and creation timestamp are visible.
6. **Given** an existing reservation in `RESERVED` status, **When** the user updates the expected attendees within the seating arrangement's capacity or updates the note, **Then** the reservation metadata is updated successfully.
7. **Given** an existing reservation in `RESERVED` status, **When** the user attempts to update the expected attendees to exceed the seating arrangement's capacity, **Then** the update is rejected with a validation error.

---

### User Story 4 - Cancel Room Reservation and Enforce Room Lifecycle Safety (Priority: P4)

A user or facility manager needs to cancel an existing upcoming or active reservation when plans change, releasing the room so others can reserve it. Furthermore, room administrative actions (deactivation and deletion) must respect existing reservations to prevent orphaned commitments.

**Why this priority**: Prevents ghost bookings, frees up room capacity, and protects scheduled reservations from unintended administrative closures.

**Independent Test**: Cancel an existing `RESERVED` booking, verify its status transitions to `CANCELLED`, and verify that the previously blocked time slot can now be booked by another user. Verify that attempting to deactivate a room with active or reserved bookings is rejected.

**Acceptance Scenarios**:

1. **Given** an existing reservation in `RESERVED` or `ACTIVE` status, **When** a user cancels the reservation, **Then** its status changes to `CANCELLED`.
2. **Given** a time slot previously occupied by a reservation that is now `CANCELLED`, **When** another user attempts to book that exact time slot, **Then** the booking succeeds without conflict.
3. **Given** a room with one or more reservations in `RESERVED` or `ACTIVE` status, **When** an administrator attempts to deactivate the room, **Then** the deactivation is blocked with an error stating that upcoming and active reservations must be cancelled first.
4. **Given** a room with historical, active, or cancelled reservations, **When** an administrator attempts to delete the room, **Then** the deletion is blocked by the dependent history check.

---

### Edge Cases

- **Deactivated Room**: What happens when a user attempts to book a room that has status `DEACTIVATED`? The system MUST reject the reservation attempt with an error stating that deactivated rooms cannot be booked.
- **Deactivated Equipment in Catalog**: What happens if an equipment type is deactivated in the catalog? The system MUST NOT offer deactivated equipment types in the additional equipment selector and MUST reject any reservation creation request referencing a deactivated equipment ID with an HTTP 400 validation error. Existing historical reservations containing previously booked deactivated equipment MUST display the equipment items intact without alteration.
- **Start Time in the Past**: What happens if a user submits a start time earlier than current time? The system MUST reject the submission.
- **Zero or Negative Duration**: What happens if the end time is equal to or earlier than the start time? The system MUST reject the submission.
- **Back-to-Back Bookings**: What happens when an existing booking ends at 11:00:00 and a new booking requests to start at 11:00:00? The system MUST permit both without conflict under half-open interval rules `[start, end)`.
- **Missing Creator Identity**: What happens if a user attempts to create a reservation without providing their name or identifier? The system MUST reject the submission with a validation error.
- **Simultaneous / Concurrent Bookings**: What happens if two users attempt to book overlapping time slots on the same room at the exact same moment? The system MUST ensure transactional isolation so that exactly one reservation succeeds and the other fails with a conflict error.
- **Repeated Cancellation, Completion, or Reactivation**: What happens if a user attempts to transition or reactivate a reservation that is already `CANCELLED`, `COMPLETED`, or `EXPIRED`? The system MUST reject the action; all three are permanent terminal states.
- **Editing Active, Completed, Expired, or Cancelled Reservations**: What happens if a user attempts to edit metadata on an `ACTIVE`, `COMPLETED`, `EXPIRED`, or `CANCELLED` reservation? The system MUST reject the edit request (only `RESERVED` reservations can have metadata modified).
- **Attempting to Reschedule via Edit**: What happens if a user attempts to change the room, time window, or seating arrangement of an existing reservation? The system MUST reject the update; schedule and layout modifications require cancelling and creating a new reservation.
- **Deactivating Room with Scheduled Bookings**: What happens when an administrator attempts to deactivate a room that has bookings in `RESERVED` or `ACTIVE` status? The system MUST reject the deactivation request until those bookings are cancelled.
- **Deleting Room with Any Reservation Record**: What happens when an administrator attempts to delete a room that has any reservation history? The system MUST reject deletion via the dependent history check.
- **Longtext Formatting**: What happens when a user enters long notes containing multi-line text or special characters? The system MUST preserve whitespace and formatting up to a reasonable character limit (e.g., 2,000 characters).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow users to initiate and create a reservation directly from the room view for a specified room.
- **FR-002**: System MUST require a reservation start date/time and either an end date/time or duration.
- **FR-003**: System MUST validate that reservation start time is in the future and that end time is strictly greater than start time.
- **FR-004**: System MUST reject reservation attempts on rooms that have `DEACTIVATED` status.
- **FR-005**: System MUST perform conflict detection and reject any reservation where the requested time window overlaps with an existing reservation for the same room that is in `RESERVED` or `ACTIVE` status.
- **FR-006**: System MUST treat reservation time boundaries as strict half-open intervals `[start, end)` with zero required buffer between consecutive bookings, permitting back-to-back bookings where one reservation's start time equals an existing reservation's end time. Conflict detection architecture MUST isolate the turnover buffer parameter (defaulting to zero) to support future turnover rules without structural refactoring.
- **FR-007**: System MUST treat `CANCELLED`, `COMPLETED`, and `EXPIRED` as permanent terminal states, excluding them from conflict detection for new bookings and prohibiting any reactivation, resurrection, or schedule modification.
- **FR-008**: System MUST require exactly one seating arrangement belonging to the target room to be selected for each reservation, and the reservation form MUST pre-select the arrangement by default if the room has only one configured layout.
- **FR-009**: System MUST require a valid expected attendee count specified as a positive integer greater than or equal to 1, rejecting any values less than 1, non-integers, or blank inputs with clear validation feedback.
- **FR-010**: System MUST validate that the expected attendee count does not exceed the `maxCapacity` of the selected seating arrangement.
- **FR-011**: System MUST strictly omit equipment types permanently assigned to the target room from the additional equipment selection list, offering only active unassigned equipment types. If all active catalog equipment types are permanently present in the target room, the reservation form MUST display an informative notice indicating that all catalog equipment is already present, with no checkboxes displayed. The system MUST reject reservation creation requests that reference deactivated equipment types with an HTTP 400 validation error, while preserving booked equipment items on historical reservations. Equipment selection is strictly type-based via checkboxes without quantity inputs.
- **FR-012**: System MUST allow the user to provide an optional longtext note describing requirements or details for the reservation.
- **FR-013**: System MUST require a non-blank creator identity (name or institutional ID) during reservation creation, persisting it and the creation timestamp as administrative audit metadata.
- **FR-014**: System MUST store and persist the reservation lifecycle status directly in the database (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`), initialized to `RESERVED` upon creation.
- **FR-014a**: System MUST provide manual operational action triggers for status transitions: allowing a `RESERVED` reservation to be manually transitioned to `ACTIVE` ("Activate / Check-In"), an `ACTIVE` reservation to be manually transitioned to `COMPLETED` ("Complete / Check-Out"), and an unattended `RESERVED` reservation whose scheduled time has passed to be transitioned to `EXPIRED` ("Expire / Mark No-Show"). Once in `COMPLETED`, `EXPIRED`, or `CANCELLED` status, no further status transitions are permitted. Automated background timer-based transitions are deferred as a future feature.
- **FR-015**: System MUST allow cancelling a reservation in `RESERVED` or `ACTIVE` status, transitioning its state to `CANCELLED`. Once in `CANCELLED` status, the reservation is permanently closed and cannot be un-cancelled or reactivated.
- **FR-016**: System MUST display existing reservations for a room in the room view, showing schedule, current lifecycle status (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`), seating arrangement, attendee count, additional equipment, notes, and administrative metadata.
- **FR-017**: System MUST allow updating the note and expected attendee count of an existing reservation in `RESERVED` status, verifying that the new attendee count does not exceed the capacity of the configured seating arrangement.
- **FR-018**: System MUST prevent updating the room, time window (`startTime`, `endTime`), or seating arrangement of an existing reservation; any schedule or layout change requires cancelling and creating a new reservation.
- **FR-019**: System MUST prevent metadata edits on reservations in `ACTIVE`, `COMPLETED`, `EXPIRED`, or `CANCELLED` status.
- **FR-020**: System MUST prevent deactivating a room if any reservations for that room currently exist in `RESERVED` or `ACTIVE` status.
- **FR-021**: System MUST block deleting a room that has any associated reservation records (fulfilling the room dependent history safety check).

### Key Entities *(include if feature involves data)*

- **Reservation**:
  - `id`: Unique identifier of the reservation.
  - `room`: Reference to the reserved room.
  - `startTime`: Starting date and time of the booking (inclusive).
  - `endTime`: Ending date and time of the booking (exclusive).
  - `status`: Current lifecycle state (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`).
  - `seatingArrangement`: The specific seating arrangement deployed for this booking.
  - `expectedAttendees`: Number of persons expected to attend.
  - `additionalEquipment`: Collection of equipment types requested that are not built into the room.
  - `note`: Optional longtext description or setup instructions.
  - `createdBy`: Identity (non-blank name or identifier) of the user who made the reservation.
  - `createdAt`: Date and time when the reservation was created.
- **Room**: Physical space with an assigned floor, building, status (`ACTIVE` or `DEACTIVATED`), a collection of available seating arrangements, and a collection of permanently installed equipment types.
- **Seating Arrangement**: Seating configuration option belonging to a room, having a name and `maxCapacity`.
- **Equipment Type**: Catalog item representing a type of equipment (e.g. "Microphone", "Flipchart"), having a name and status.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can create a complete room reservation from the room view in under 60 seconds.
- **SC-002**: 100% of conflicting reservation attempts for the same room and overlapping time window are blocked.
- **SC-003**: 100% of reservations accurately reflect their persisted lifecycle status (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`) when viewed.
- **SC-004**: 100% of reservation attempts with expected attendees exceeding the chosen seating arrangement capacity are rejected with clear user feedback.
- **SC-005**: 100% of created reservations retain immutable administrative audit metadata (creator identity and creation timestamp).
- **SC-006**: When a reservation is cancelled, the room's time slot is released immediately and becomes available for new bookings within 1 second.
- **SC-007**: Metadata updates (attendees, note) on `RESERVED` bookings take effect immediately without modifying the scheduled time window or room allocation.
- **SC-008**: 100% of attempts to deactivate a room with upcoming active or reserved bookings are blocked with clear messaging.

## Assumptions

- **User Authentication & Identity**: In the current application state where centralized single sign-on (SSO) is not yet integrated, the reservation form requires the reserving user to enter their name or institutional ID into a mandatory `createdBy` field. When an authentication module is added to the application, this field will be populated automatically from the authenticated session context without changing the reservation entity schema.
- **Time Zone & Granularity**: All timestamps are recorded and compared with time-zone awareness (ISO 8601 UTC). Scheduling supports minute-level resolution with standard 15-minute quick-selection presets in the user interface.
- **Turnover Buffer Architecture**: The current feature enforces zero-gap half-open interval boundaries without mandatory buffers between meetings. The scheduling architecture must isolate turnover buffer calculation to allow future conditional buffer rules (e.g. for seating layout adjustments) to be introduced cleanly without changing database schema or contract types.
- **Equipment Stock / Inventory Limits**: Selection of additional equipment is strictly type-based via checkboxes without quantity inputs or counts; tracking discrete serialized units or global equipment pool stock counts is explicitly out of scope for this initial feature version.
- **Lifecycle Persistence & Transitions**: All five lifecycle states (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`) are persisted directly in the database status column. State transitions are operated via manual action buttons ("Activate / Check-In" from `RESERVED` to `ACTIVE`, "Complete / Check-Out" from `ACTIVE` to `COMPLETED`, "Expire / Mark No-Show" from `RESERVED` to `EXPIRED`, and "Cancel" to `CANCELLED`). Automated background timer-based transitions are deferred as a future feature.
- **Cancellation Access**: In this initial feature version, any user viewing the reservation can cancel it. Role-based cancellation restrictions (e.g., only creator or admin) will be enforced once user authorization roles are formally defined in the project.
