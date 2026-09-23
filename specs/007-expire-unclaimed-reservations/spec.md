# Feature Specification: Expire Unclaimed Room Reservations

**Feature Branch**: `007-expire-unclaimed-reservations`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "When there was no check-in into a room that was reserved (status is not ACTIVE, existing state), the reservations status should be changed to EXPIRED (existing status) after 5 minutes (optionally configurable). Feature number should be 007"

## Clarifications

### Session 2026-09-23

- Q: How should the expiration grace period configuration be managed? (FR-004) → A: Hardcoded 5-minute constant: The grace period is standardized at 5 minutes as a constant in this release; external or runtime administrative configuration is deferred until system-wide settings exist.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic Expiration of Unattended Reservations (Priority: P1)

When a room reservation reaches its scheduled start time and no organizer or attendee checks in within the allowable grace period (the reservation remains in `RESERVED` status rather than `ACTIVE`), the system automatically transitions the reservation status to `EXPIRED`. This releases the room back to general availability so that students, faculty, or staff can book or use the vacant room rather than allowing spaces to remain locked for absent parties.

**Why this priority**: Core value of the feature. Prevents "ghost bookings" and room hoarding by ensuring unclaimed rooms are released back into circulation in a timely manner.

**Independent Test**: Create a reservation for a room starting at a specific time. Let the scheduled start time plus the 5-minute grace period elapse without initiating check-in. Verify that the reservation status automatically updates to `EXPIRED` and that the room is immediately bookable by another user for the remainder of that time slot.

**Acceptance Scenarios**:

1. **Given** a reservation in `RESERVED` status with a start time of 10:00, **When** current system time reaches 10:05 (start time plus 5-minute grace period) without any check-in action, **Then** the reservation status automatically transitions to `EXPIRED`.
2. **Given** a reservation that has automatically transitioned to `EXPIRED`, **When** any user views the room schedule or attempts to book that room for the remainder of the time slot, **Then** the expired reservation is excluded from conflict detection and the room is considered available.
3. **Given** a reservation that has transitioned to `EXPIRED`, **When** an organizer attempts to perform a check-in / activation on that reservation, **Then** the system rejects the activation request and indicates that the reservation has expired due to no-show.

---

### User Story 2 - Successful Check-In Prevents Expiration (Priority: P1)

When an organizer or attendee checks in to the reserved room within the allowable grace period (e.g. at minute 2 after scheduled start time, or at start time), the reservation transitions to `ACTIVE`. Once active, the system exempts the reservation from the no-show expiration rule, allowing the meeting or event to proceed without risk of sudden room release.

**Why this priority**: Essential parity with User Story 1. Legitimately attended meetings must never be mistakenly released or marked expired while in progress.

**Independent Test**: Create a reservation starting at 14:00. Check in at 14:03 (transitioning status to `ACTIVE`). Wait past 14:05 and verify the status remains `ACTIVE` throughout the meeting duration.

**Acceptance Scenarios**:

1. **Given** a reservation in `RESERVED` status starting at 14:00, **When** the user checks in at 14:02, **Then** the reservation transitions to `ACTIVE` status.
2. **Given** an `ACTIVE` reservation whose start time was 14:00, **When** current time reaches and passes 14:05, **Then** the reservation status remains `ACTIVE` and is not expired.

---

### User Story 3 - Standardized 5-Minute Grace Period (Priority: P2)

The system enforces a standardized 5-minute check-in grace period across all room reservations. Attendees have a predictable, campus-wide 5-minute window after their scheduled start time to activate their reservation before it lapses into forfeiture. External or runtime administrative configuration is deferred until system-wide settings exist.

**Why this priority**: Predictable and uniform rules across all campus rooms eliminate ambiguity about when an unclaimed room becomes forfeit.

**Independent Test**: Create a reservation starting at 09:00. Verify the reservation remains `RESERVED` at 09:04, and transitions to `EXPIRED` at 09:05.

**Acceptance Scenarios**:

1. **Given** a reservation in `RESERVED` status starting at 09:00, **When** current time is 09:04 without check-in, **Then** the reservation remains in `RESERVED` status and can still be activated.
2. **Given** a reservation in `RESERVED` status starting at 09:00, **When** current time reaches 09:05 without check-in, **Then** the reservation status transitions to `EXPIRED`.

---

### User Story 4 - Visible Status and Action Feedback in Room Schedule (Priority: P3)

Users viewing room schedules, upcoming bookings, or reservation details see clear visual indicators of expired reservations. Expired bookings show the `EXPIRED` status badge and display disabled operational controls, preventing confusion about whether a room is currently occupied or vacant.

**Why this priority**: Good user experience and operational clarity prevent disputes over room occupancy.

**Independent Test**: View a room that has an expired reservation. Verify the reservation card or row displays the `EXPIRED` status badge and that the "Activate / Check-In" action button is either disabled or absent.

**Acceptance Scenarios**:

1. **Given** an expired reservation, **When** viewed in the room's reservation list, **Then** it displays with an `EXPIRED` status badge.
2. **Given** an expired reservation, **When** viewed by any user, **Then** the "Check-In / Activate" button is not interactive and metadata cannot be edited.

---

### Edge Cases

- **Immediate Back-to-Back or Sub-Grace-Period Bookings**: What happens if a reservation has a scheduled duration that is shorter than or equal to the grace period (e.g. a 5-minute quick consultation)? The reservation expires at the configured grace period (or its scheduled end time, whichever boundary occurs first) if not activated.
- **Concurrent Check-In and Expiration**: What happens if a user submits a check-in request at the exact moment the expiration process evaluates the reservation? Concurrency controls (such as optimistic locking) ensure that either the check-in or the expiration completes cleanly; the losing concurrent operation fails gracefully without corrupting reservation state.
- **Cancelled and Completed Reservations**: What happens if a reservation was cancelled before its start time or completed early? Reservations in `CANCELLED` or `COMPLETED` status are permanent terminal states and are strictly ignored by the expiration evaluator.
- **System Downtime or Restart Recovery**: What happens if the system is restarted or temporarily offline during a scheduled start window? Upon service availability, the expiration process immediately detects all unattended `RESERVED` bookings whose `startTime + 5 minutes` has elapsed and transitions them to `EXPIRED`.
- **Future and Ongoing Active Bookings**: What happens to reservations whose start time is still in the future? They remain in `RESERVED` status and are never touched by the expiration process until their individual start time plus grace period has passed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST identify room reservations in `RESERVED` status whose scheduled `startTime` plus the check-in grace period has elapsed.
- **FR-002**: System MUST automatically transition identified unclaimed reservations from `RESERVED` to `EXPIRED` status without requiring manual user intervention.
- **FR-003**: System MUST enforce a standardized check-in grace period of 5 minutes (`PT5M`).
- **FR-004**: System MUST define the 5-minute grace period as an application constant; externalized or runtime administrative configuration is deferred until system-wide administrative settings exist.
- **FR-005**: System MUST treat `EXPIRED` as a permanent terminal state, strictly rejecting any subsequent check-in, activation, completion, cancellation, or metadata modification attempts.
- **FR-006**: System MUST exclude `EXPIRED` reservations from room conflict detection, immediately releasing the room for ad-hoc bookings and new reservations across the remaining time window.
- **FR-007**: System MUST record the transition timestamp (`updatedAt`) and generate a structured audit log entry whenever a reservation is transitioned to `EXPIRED`.
- **FR-008**: System MUST NOT apply the expiration rule to reservations currently in `ACTIVE`, `COMPLETED`, or `CANCELLED` status.
- **FR-009**: System MUST automatically catch up on and expire any overdue unattended reservations that passed their expiration threshold during system downtime or restarts.
- **FR-010**: System MUST visually present the `EXPIRED` status badge in room schedule views and reservation lists, disallowing check-in/activation interactions.

### Key Entities

- **Reservation**: Represents a booked room slot.
  - Relevant attributes:
    - `startTime`: Scheduled start timestamp of the booking.
    - `endTime`: Scheduled end timestamp of the booking.
    - `status`: Lifecycle state (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`).
    - `updatedAt`: Timestamp when the reservation record was last updated.
- **Check-In Grace Period**: Standardized constant defining the room claim timeout window (`5 minutes`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of unattended reservations in `RESERVED` status are transitioned to `EXPIRED` within 60 seconds after their start time plus grace period has passed.
- **SC-002**: 0% false positives — reservations in `ACTIVE`, `COMPLETED`, or `CANCELLED` status are never transitioned to `EXPIRED`.
- **SC-003**: 100% of rooms tied to newly expired reservations become immediately available for new reservations or walk-in use without administrative intervention.
- **SC-004**: 100% of evaluated reservations consistently apply the standardized 5-minute grace period without variance.
- **SC-005**: 100% of activation or modification attempts on expired reservations are rejected with an explicit error explaining that the reservation lapsed.

## Assumptions

- The 5 existing reservation lifecycle statuses (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`) established in the core reservation feature are retained without structural changes.
- "Check-in" corresponds directly to the existing "Activate / Check-In" action that transitions a reservation from `RESERVED` to `ACTIVE`.
- The 5-minute grace period is fixed as an application constant across all room reservations; dynamic or external configuration is deferred.
- Automated email or push notifications alerting users when their reservation expires are out of scope for this initial release and can be introduced in a future notification enhancement.
- Expiration checks run on a lightweight periodic schedule or during room query evaluations, ensuring system performance remains unimpacted even with high volumes of reservations.
