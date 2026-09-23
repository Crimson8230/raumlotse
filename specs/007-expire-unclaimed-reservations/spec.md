# Feature Specification: Expire Unclaimed Room Reservations

**Feature Branch**: `007-expire-unclaimed-reservations`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "When there was no check-in into a room that was reserved (status is not ACTIVE, existing state), the reservations status should be changed to EXPIRED (existing status) after 5 minutes (optionally configurable). Feature number should be 007"

## Clarifications

### Session 2026-09-23

- Q: How should the expiration grace period configuration be managed? (FR-004) → A: Hardcoded 5-minute constant: The grace period is standardized at 5 minutes as a constant in this release; external or runtime administrative configuration is deferred until system-wide settings exist.
- Q: At what exact mathematical boundary should an unattended reservation become eligible for expiration? (CHK001) → A: Strict threshold: The reservation only expires strictly after 5 minutes have elapsed (`now > startTime + 5 minutes` / `startTime < now - 5 minutes`); check-in remains valid up to and including the exact 5-minute mark.
- Q: Should the automatic expiration process strictly exclude active, completed, and cancelled reservations even if an active meeting exceeds its scheduled end time? (CHK002) → A: Auto-complete overdue active: The background sweep expires unattended RESERVED bookings after 5 minutes and also automatically transitions ACTIVE reservations to COMPLETED once their scheduled endTime has passed; terminal states (CANCELLED, COMPLETED, EXPIRED) remain strictly untouched.
- Q: What exact availability and conflict detection side-effects should take effect immediately upon reservation expiration? (CHK003) → A: Immediate zero-buffer release: Both the room and any reserved portable equipment are instantly excluded from conflict detection, allowing new bookings starting immediately from the current timestamp onwards for the remainder of the scheduled slot without turnover buffers.
- Q: How should the system execute the catch-up sweep for overdue reservations following application downtime or restarts? (CHK004) → A: Standard interval sweep: Catch-up execution runs during the standard periodic scheduler cycle (waiting 30 seconds after startup) with unbounded single-transaction processing; no special startup listener or complex chunking logic is introduced.
- Q: Should the maximum sweep delay and background execution interval be codified directly as a functional requirement? (CHK005) → A: Keep as implementation detail: The 60-second outcome deadline remains the testable metric in Success Criteria (SC-001, SC-006); specific scheduler polling intervals (e.g. 30 seconds) remain implementation details in the plan and code.
- Q: Should terminal state immutability strictly prohibit all modifications including administrative note edits? (CHK009) → A: Strict immutability: Terminal states (EXPIRED, COMPLETED, CANCELLED) strictly prohibit all metadata updates (including notes and attendee counts) and all status transitions, ensuring absolute audit integrity.
- Q: How should the system handle unattended reservations whose scheduled duration is shorter than or equal to the 5-minute grace period? (CHK011) → A: Cap expiration at scheduled end time: Unattended RESERVED bookings expire when strictly past 5 minutes (now > startTime + 5m) or upon reaching their scheduled end time (now >= endTime), whichever boundary occurs first; check-in attempts past scheduled endTime are rejected.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic Expiration of Unattended Reservations (Priority: P1)

When a room reservation reaches its scheduled start time and no organizer or attendee checks in within the allowable grace period (the reservation remains in `RESERVED` status rather than `ACTIVE`), the system automatically transitions the reservation status to `EXPIRED`. This releases the room back to general availability so that students, faculty, or staff can book or use the vacant room rather than allowing spaces to remain locked for absent parties.

**Why this priority**: Core value of the feature. Prevents "ghost bookings" and room hoarding by ensuring unclaimed rooms are released back into circulation in a timely manner.

**Independent Test**: Create a reservation for a room starting at a specific time. Let the scheduled start time plus the 5-minute grace period strictly elapse without initiating check-in. Verify that the reservation status automatically updates to `EXPIRED` and that the room is immediately bookable by another user for the remainder of that time slot.

**Acceptance Scenarios**:

1. **Given** a reservation in `RESERVED` status with a start time of 10:00, **When** current system time strictly exceeds 10:05 (e.g. 10:05:01) without any check-in action, **Then** the reservation status automatically transitions to `EXPIRED`.
2. **Given** a reservation that has automatically transitioned to `EXPIRED`, **When** any user views the room schedule or attempts to book that room for the remainder of the time slot, **Then** the expired reservation is excluded from conflict detection and the room is considered available.
3. **Given** a reservation that has transitioned to `EXPIRED`, **When** an organizer attempts to perform a check-in / activation on that reservation, **Then** the system rejects the activation request and indicates that the reservation has expired due to no-show.
4. **Given** a reservation in `RESERVED` status with a start time of 10:00, **When** an attendee checks in at or before exactly 10:05:00, **Then** the check-in is accepted and transitions the reservation to `ACTIVE`, exempting it from expiration.

---

### User Story 2 - Successful Check-In Prevents Expiration & Concludes on End Time (Priority: P1)

When an organizer or attendee checks in to the reserved room within the allowable grace period (e.g. at minute 2 after scheduled start time, or at start time), the reservation transitions to `ACTIVE`. Once active, the system exempts the reservation from the no-show expiration rule, allowing the meeting or event to proceed without risk of sudden room release. Furthermore, once the meeting's scheduled end time passes without manual check-out, the system automatically transitions the reservation to `COMPLETED` so the room is released and the attended record is cleanly closed.

**Why this priority**: Essential parity with User Story 1. Legitimately attended meetings must never be mistakenly released or marked expired while in progress, and concluded meetings must not indefinitely remain in active status.

**Independent Test**: Create a reservation starting at 14:00 and ending at 15:00. Check in at 14:03 (transitioning status to `ACTIVE`). Wait past 14:05 and verify the status remains `ACTIVE`. Wait past 15:00 and verify the status automatically transitions to `COMPLETED`.

**Acceptance Scenarios**:

1. **Given** a reservation in `RESERVED` status starting at 14:00, **When** the user checks in at 14:02, **Then** the reservation transitions to `ACTIVE` status.
2. **Given** an `ACTIVE` reservation whose start time was 14:00 and end time is 15:00, **When** current time reaches and passes 14:05, **Then** the reservation status remains `ACTIVE` and is not expired.
3. **Given** an `ACTIVE` reservation whose scheduled end time is 15:00, **When** current time reaches or exceeds 15:00 without manual checkout, **Then** the system automatically transitions the reservation status to `COMPLETED`, releasing the room while preserving the attended meeting record.

---

### User Story 3 - Standardized 5-Minute Grace Period (Priority: P2)

The system enforces a standardized 5-minute check-in grace period across all room reservations. Attendees have a predictable, campus-wide 5-minute window after their scheduled start time to activate their reservation before it lapses into forfeiture. External or runtime administrative configuration is deferred until system-wide settings exist.

**Why this priority**: Predictable and uniform rules across all campus rooms eliminate ambiguity about when an unclaimed room becomes forfeit.

**Independent Test**: Create a reservation starting at 09:00. Verify the reservation remains `RESERVED` at 09:05:00, and transitions to `EXPIRED` only after 09:05:00 has passed.

**Acceptance Scenarios**:

1. **Given** a reservation in `RESERVED` status starting at 09:00, **When** current time is up to and including 09:05:00 without check-in, **Then** the reservation remains in `RESERVED` status and can still be activated.
2. **Given** a reservation in `RESERVED` status starting at 09:00, **When** current time strictly exceeds 09:05:00 without check-in, **Then** the reservation status transitions to `EXPIRED`.

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

- **Exact 5-Minute Boundary Condition**: At the exact 5-minute mark (`startTime + 5 minutes`, e.g. `10:05:00.000` for a `10:00:00` booking), check-in remains valid and the reservation is not yet expired. Auto-expiration is strictly triggered only once current time strictly exceeds `startTime + 5 minutes` (`now > startTime + 5 minutes`).
- **Overdue Active Reservations Past End Time**: If an active meeting runs past its scheduled end time without manual checkout, the background sweep automatically transitions the reservation from `ACTIVE` to `COMPLETED` (`now >= endTime`), releasing the room for subsequent bookings or walk-ins while preserving attended audit status.
- **Immediate Back-to-Back or Sub-Grace-Period Bookings**: What happens if a reservation has a scheduled duration that is shorter than or equal to the grace period (e.g. a 5-minute quick consultation)? The reservation expires at the configured grace period or its scheduled end time, whichever boundary occurs first (`now > startTime + 5m` OR `now >= endTime`), if not activated. Check-in attempts after the scheduled end time has elapsed are strictly rejected.
- **Concurrent Check-In and Expiration**: What happens if a user submits a check-in request at the exact moment the expiration process evaluates the reservation? Concurrency controls (such as optimistic locking) ensure that either the check-in or the expiration completes cleanly; the losing concurrent operation fails gracefully without corrupting reservation state.
- **Cancelled and Completed Reservations**: What happens if a reservation was cancelled before its start time or completed early? Reservations in `CANCELLED` or `COMPLETED` status are permanent terminal states and are strictly ignored by the expiration evaluator.
- **System Downtime or Restart Recovery**: When the application recovers from downtime or restarts, the next regular scheduler execution (within 30 seconds of startup) automatically sweeps and processes all overdue bookings in a single transaction, restoring state consistency without requiring custom startup batching.
- **Future and Ongoing Active Bookings**: What happens to reservations whose start time is still in the future? They remain in `RESERVED` status and are never touched by the expiration process until their individual start time plus grace period has passed.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST identify room reservations in `RESERVED` status whose scheduled `startTime` plus the check-in grace period has strictly elapsed (current time > startTime + 5 minutes) OR whose scheduled `endTime` has elapsed (current time >= endTime), whichever boundary occurs first.
- **FR-002**: System MUST automatically transition identified unclaimed reservations from `RESERVED` to `EXPIRED` status without requiring manual user intervention.
- **FR-003**: System MUST enforce a standardized check-in grace period of 5 minutes (`PT5M`).
- **FR-004**: System MUST define the 5-minute grace period as an application constant; externalized or runtime administrative configuration is deferred until system-wide administrative settings exist.
- **FR-005**: System MUST treat `EXPIRED` and `COMPLETED` as permanent terminal states, strictly rejecting any subsequent check-in, activation, completion, cancellation, or metadata modification attempts.
- **FR-006**: System MUST exclude `EXPIRED` (and `COMPLETED`) reservations and their booked portable equipment from conflict detection immediately upon transition, releasing the room and gear for ad-hoc bookings and new reservations starting from the current time onward with zero turnover buffer.
- **FR-007**: System MUST record the transition timestamp (`updatedAt`) and generate a structured audit log entry whenever a reservation is transitioned to `EXPIRED` or `COMPLETED`.
- **FR-008**: System MUST NOT transition reservations currently in `ACTIVE`, `COMPLETED`, or `CANCELLED` status to `EXPIRED`.
- **FR-008a**: System MUST automatically transition reservations currently in `ACTIVE` status to `COMPLETED` once their scheduled `endTime` has elapsed (`now >= endTime`), ensuring concluded meetings are closed even if attendees omit manual checkout.
- **FR-009**: System MUST automatically catch up on and expire any overdue unattended reservations, and complete any overdue active reservations, that passed their thresholds during system downtime or restarts during the normal periodic scheduler sweep (within 30 seconds of application startup) without requiring special startup batching.
- **FR-010**: System MUST visually present the `EXPIRED` and `COMPLETED` status badges in room schedule views and reservation lists, disallowing check-in/activation interactions.

### Key Entities

- **Reservation**: Represents a booked room slot.
  - Relevant attributes:
    - `startTime`: Scheduled start timestamp of the booking.
    - `endTime`: Scheduled end timestamp of the booking.
    - `status`: Lifecycle state (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`).
    - `updatedAt`: Timestamp when the reservation record was last updated.
  - Lifecycle transitions handled by background sweep:
    - `RESERVED -> EXPIRED` (when `now > startTime + 5m`)
    - `ACTIVE -> COMPLETED` (when `now >= endTime`)
- **Check-In Grace Period**: Standardized constant defining the room claim timeout window (`5 minutes`, strictly elapsed: `now > startTime + 5 minutes`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of unattended reservations in `RESERVED` status are transitioned to `EXPIRED` within 60 seconds after their start time plus grace period has passed.
- **SC-002**: 0% false positives — reservations in `ACTIVE` status are never transitioned to `EXPIRED` (transitioning only to `COMPLETED` upon reaching `endTime`), and terminal reservations (`COMPLETED`, `CANCELLED`, `EXPIRED`) are never modified.
- **SC-003**: 100% of rooms tied to newly expired reservations become immediately available for new reservations or walk-in use without administrative intervention.
- **SC-004**: 100% of evaluated reservations consistently apply the standardized 5-minute grace period without variance.
- **SC-005**: 100% of activation or modification attempts on expired reservations are rejected with an explicit error explaining that the reservation lapsed.
- **SC-006**: 100% of unattended active reservations are transitioned to `COMPLETED` within 60 seconds after their scheduled `endTime` has elapsed.

## Assumptions

- The 5 existing reservation lifecycle statuses (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`) established in the core reservation feature are retained without structural changes.
- "Check-in" corresponds directly to the existing "Activate / Check-In" action that transitions a reservation from `RESERVED` to `ACTIVE`.
- The 5-minute grace period is fixed as an application constant across all room reservations; dynamic or external configuration is deferred.
- Automated email or push notifications alerting users when their reservation expires are out of scope for this initial release and can be introduced in a future notification enhancement.
- Expiration and completion checks run on a lightweight periodic schedule or during room query evaluations, ensuring system performance remains unimpacted even with high volumes of reservations.
