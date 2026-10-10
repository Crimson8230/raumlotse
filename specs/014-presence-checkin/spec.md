# Feature Specification: On-Site Presence Check-In

**Feature Branch**: `014-presence-checkin`

**Created**: 2026-10-08

**Status**: Draft

**Input**: User description: "Als Nutzer:in möchte ich vor Ort meine Anwesenheit bestätigen können, um die Buchung als \"in Nutzung\" markieren zu können. Akzeptanzkriterien: Es ist möglich die Anwesenheit in einem Raum (für eine bestimmte Buchung) zu bestätigen: QR Code Scan, NFC, Bewegungssensor. Sobald ein Raum in Nutzung ist, werden: Lichter eingeschaltet, Lüftung eingeschaltet, Tür entriegelt, Raumstatus auf \"belegt\" setzen. Nach Ende der Buchungszeit wird: Raumstatus auf \"frei\" gesetzt, Licht ausgeschaltet, Lüftung ausgeschaltet"

## Clarifications

### Session 2026-10-08

- Q: Should a motion-sensor event alone confirm a booking, given that the sensor cannot identify the person and the door only unlocks after confirmation? → A: No. The motion sensor does not check a booking in. It only confirms continued presence in a booking that is already `ACTIVE` (checked in by QR code, NFC or manually). Using that signal to release empty rooms early is out of scope here and is left for a later feature.
- Q: Who may confirm presence on site via QR code or NFC? → A: The booking owner and administrators, which matches the rules of the existing manual check-in. Other users cannot confirm someone else's booking.
- Q: Where should the simulated states of lighting, ventilation and door be visible so the concept can be demonstrated? → A: In both places. The room display shows read-only status icons (lighting on/off, ventilation on/off, door locked/unlocked) to every signed-in viewer. The existing device control page (feature 006) adds the door for the booking user. Device states stay simulated through the existing stub device integration; the display reads the last confirmed state and needs no separate simulation.
- Q: How should the motion sensor be simulated in the mockup, given that there is no real sensor? → A: With an admin-only "Bewegung simulieren" button on the room page or room display. It sends a sensor event for that room through the same entry point a real sensor would use.
- Q: How should QR code and NFC check-in be shown in the mockup? → A: The QR code is real. It is generated per room, shown on the room display and printable by administrators. NFC uses the same room check-in link marked with the method "NFC"; the link can be written to a real NFC tag, or opened directly as a stand-in when no tag is available. Both methods run through a single check-in flow, and there is no separate NFC simulation.
- Q: Should connecting real hardware (e-ink display and LED) be part of this feature, or should this feature only be ready for it? → A: Only be ready for it. This feature keeps the browser simulation and adds a device-readable, read-only room status (room status plus lighting, ventilation and door state) that a future e-ink display or LED controller could fetch regularly. Connecting actual devices (device sign-in, e-ink view, LED control) is a later feature, once the hardware models are known.
- Q: Who may fetch the read-only room status? → A: Signed-in users only, like every other API route today. Device access without a user account comes with the later hardware feature (for example via the planned one-time-code login for displays).

### Session 2026-10-10

- Q: Can a booking be checked in before its official start time? → A: Yes, up to 10 minutes before the start, but only if no other reservation of the room is ongoing at that moment. This applies to every check-in method, including the manual "Einchecken" action. From the moment of check-in the booking counts as in use: the room shows "occupied", the devices are prepared and the booking user can control them.
- Q: Can administrators change the check-in times without a code change? → A: Yes. The early check-in time (minutes before the start, 0–60, default 10; 0 switches early check-in off) and the check-in grace period (minutes after the start before an unattended booking expires, 1–30, default 5) are editable on an admin "Einstellungen" page. Changes apply immediately to all bookings, including existing ones, and are logged with the administrator's id.
- Q: How do users get back from the room display and the device control? → A: Both pages get a link back to the room page; on the room page, device control opens as a popup instead of a separate page.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Confirm Presence by Scanning the Room's QR Code (Priority: P1)

As the user who booked a room, I want to scan the QR code posted at the room so that my reservation is confirmed as "in use" without having to look it up manually in the application.

**Why this priority**: QR scanning needs only a phone and a printed code, so it works in every room without extra hardware. It is the smallest slice that delivers the core value: confirming presence on site and turning a `RESERVED` booking into an `ACTIVE` one.

**Independent Test**: Create a `RESERVED` booking for room A starting now, open the room's QR code link as the booking user, and verify that the reservation becomes `ACTIVE` and the user sees a confirmation.

**Acceptance Scenarios**:

1. **Given** a `RESERVED` booking by user U for room A whose check-in window is open, **When** U scans room A's QR code (shown on the room display or on a printout) and is signed in, **Then** the booking becomes `ACTIVE` and U sees a confirmation naming the room and booking time.
2. **Given** user U scans room A's QR code while not signed in, **When** U signs in, **Then** U returns to the check-in flow for room A without scanning again.
3. **Given** user U has no booking for room A in its check-in window, **When** U scans room A's QR code, **Then** no booking changes and U sees a message explaining that no matching booking exists.
4. **Given** a `RESERVED` booking by user U for room A, **When** a different non-admin user V scans room A's QR code, **Then** U's booking is not confirmed and V is told that V has no matching booking.
5. **Given** a `RESERVED` booking by user U for room A whose check-in window is open, **When** an administrator scans room A's QR code, **Then** the administrator can confirm U's booking, which becomes `ACTIVE`.
6. **Given** U's booking for room A is already `ACTIVE`, **When** U scans the QR code again, **Then** the booking stays `ACTIVE`, nothing is triggered twice, and U sees that the booking is already in use.

---

### User Story 2 - Room Is Prepared Automatically When a Booking Goes Into Use (Priority: P1)

As the user who booked a room, I want the lights and ventilation to turn on, the door to unlock and the room to show as "occupied" as soon as my presence is confirmed, so that I can start right away.

**Why this priority**: Preparing the room is the visible payoff of checking in. It is listed separately from US1 because it must happen for every way a booking becomes `ACTIVE`: QR, NFC, motion sensor, or the existing manual check-in.

**Independent Test**: Activate a booking by any supported method and verify that lighting and ventilation report "on", the door reports "unlocked" and the room display shows "occupied".

**Acceptance Scenarios**:

1. **Given** a `RESERVED` booking for room A, **When** the booking becomes `ACTIVE` by any confirmation method, **Then** room A's lighting and ventilation are switched on and its door is unlocked.
2. **Given** a booking in room A has just become `ACTIVE`, **When** anyone looks at room A's display or reservation list, **Then** room A or the booking is shown as "occupied" / "in Nutzung".
3. **Given** one device (for example the door) does not acknowledge its command, **When** the booking becomes `ACTIVE`, **Then** the booking still stays `ACTIVE`, the remaining devices are still switched, the failure is logged, and the booking user is told which device could not be switched.
4. **Given** room A's display is open, **When** a booking for room A becomes `ACTIVE`, **Then** without a manual reload the display shows "occupied" together with lighting on, ventilation on and door unlocked, within its regular refresh interval.
5. **Given** the booking user opens room A's device controls during the `ACTIVE` booking, **When** the controls load, **Then** the door is listed with its current state alongside lighting and ventilation.

---

### User Story 3 - Room Is Released Automatically When the Booking Ends (Priority: P1)

As the facility operator, I want the room to be marked "available" and the lights and ventilation to be turned off when a booking ends, so that no energy is wasted and the room is free for the next booking.

**Why this priority**: Without shutdown on release, rooms would keep consuming energy and show a wrong status. This directly mirrors the acceptance criteria.

**Independent Test**: Let an `ACTIVE` booking pass its end time and verify that the booking is `COMPLETED`, the room shows "available", and lighting and ventilation report "off".

**Acceptance Scenarios**:

1. **Given** an `ACTIVE` booking for room A ending at 15:00, **When** the time reaches 15:00, **Then** the booking becomes `COMPLETED`, room A shows "available", and its lighting and ventilation are switched off within 60 seconds.
2. **Given** an `ACTIVE` booking that the booking user ends early (manual completion), **When** it is completed, **Then** the same release actions are applied right away.
3. **Given** an `ACTIVE` booking for room A ending at 15:00 and a following booking for room A that has already become `ACTIVE` at 15:00, **When** the first booking ends, **Then** room A stays "occupied" and lighting and ventilation are not switched off.
4. **Given** room A's display is open, **When** the booking ends, **Then** the display switches to "available" with lighting and ventilation off.
5. **Given** a device does not acknowledge the switch-off command, **When** the booking ends, **Then** the booking is still completed and the room still shows "available", and the failed device is logged so it can be followed up.

---

### User Story 4 - Confirm Presence by NFC Tap (Priority: P2)

As the user who booked a room, I want to tap my phone on the NFC tag at the room door to confirm my presence, as a faster alternative to scanning the QR code.

**Why this priority**: It offers the same result as US1 through a faster interaction, but rooms need NFC tags, so it comes after the QR baseline.

**Independent Test**: As the booking user of a `RESERVED` booking, open room A's NFC check-in link (by tapping a tag that holds it, or directly as a stand-in) and verify that the booking becomes `ACTIVE` with the check-in method recorded as NFC.

**Acceptance Scenarios**:

1. **Given** a `RESERVED` booking by user U for room A whose check-in window is open, **When** U taps room A's NFC tag with a phone, **Then** the same check-in flow as the QR code runs and the booking becomes `ACTIVE`.
2. **Given** a phone without NFC support, **When** the user is at the room, **Then** the QR code remains available as an alternative for that room.
3. **Given** no physical NFC tag is available in the demo, **When** the booking user opens room A's NFC check-in link directly, **Then** the check-in behaves exactly as a tag tap would and is recorded as NFC.

---

### User Story 5 - Motion Sensor Confirms Continued Presence (Priority: P3)

As the facility operator, I want the room's motion sensor to record that people are still present during a booking that is in use, so that actual room usage is visible and can later be used to release unused rooms early.

**Why this priority**: The sensor cannot identify people, and in a locked room it can only detect someone after the door has been unlocked by check-in. So it cannot serve as a check-in method itself. It adds usage evidence on top of QR, NFC and manual check-in.

**Independent Test**: As an administrator, use the "Bewegung simulieren" button for room A before and after a booking's check-in, and verify that only the events during the `ACTIVE` booking are recorded as presence confirmations and that no event changes the booking status.

**Acceptance Scenarios**:

1. **Given** an `ACTIVE` booking for room A, **When** room A's motion sensor reports presence, **Then** the time of the most recent detected presence is recorded for that booking, and the booking status is unchanged.
2. **Given** a `RESERVED` booking for room A that has not been checked in, **When** room A's motion sensor reports presence, **Then** the booking stays `RESERVED`, no devices are switched, and it still expires under the existing grace-period rule if nobody checks in.
3. **Given** no booking for room A is `ACTIVE`, **When** room A's motion sensor reports presence, **Then** no booking changes and no devices are switched.
4. **Given** an `ACTIVE` booking with no motion event at all, **When** the booking runs and ends, **Then** check-in, room preparation and release work unchanged; only the presence record stays empty.
5. **Given** an administrator views room A's page or display, **When** the administrator presses "Bewegung simulieren", **Then** a motion event for room A is processed exactly as a real sensor event would be, and the time of the most recent detected presence is shown to the administrator.
6. **Given** a non-admin user views room A's page or display, **When** the page loads, **Then** no "Bewegung simulieren" button is shown, and a direct attempt to submit a motion event is rejected.

### Edge Cases

- **Check-in window**: Presence can be confirmed from 10 minutes before the booking's start time until the end of the existing 5-minute grace period after the start (feature 007). After that the booking is `EXPIRED` and on-site confirmation is rejected with an explanation.
- **Early arrival**: A check-in more than 10 minutes before the start, or while another reservation of the room is still ongoing, does not activate the booking. The user is told when check-in opens (the later of start − 10 minutes and the end of the ongoing reservation). An early check-in makes the booking in use from the check-in time on.
- **Back-to-back bookings in the same room**: Confirmation always targets the booking whose check-in window is currently open. A following booking can be checked in early only once the previous booking of the room is no longer ongoing, and the end of one booking does not shut down a room that a following booking is already using.
- **Several bookings by the same user**: Only the booking for the scanned room is affected. Bookings for other rooms remain unchanged.
- **Tampered or unknown codes**: A QR or NFC code that does not belong to an active room is rejected without revealing information about bookings.
- **Repeated confirmation**: Scanning or tapping while the booking is already `ACTIVE` changes nothing and does not resend device commands. Motion events only update the most recent presence time.
- **Motion before check-in**: Motion detected in a room whose booking is still `RESERVED` (for example someone in the room from an earlier booking, or a room without a lock) never activates the booking.
- **Cancellation while in use**: If an `ACTIVE` booking is cancelled or completed early, the same release actions as at the regular end time apply.
- **Door at the end of a booking**: The door is not re-locked automatically at the end of a booking, so that nobody is locked in or out mid-transition (see Assumptions).
- **Manual device changes during the booking**: Device changes the booking user makes during the booking through the existing room controls (feature 006) are overridden by the switch-off at the end of the booking.
- **Downtime**: Bookings that ended while the system was unavailable are released, and their devices switched off, in the next regular sweep after restart.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST let users check in on site for a specific booking in a specific room through two methods, QR code scan and NFC tag tap, in addition to the existing manual check-in. Motion-sensor detection MUST NOT check a booking in; it only confirms continued presence in an `ACTIVE` booking (FR-016).
- **FR-002**: Each active room MUST have a stable check-in link that identifies the room without further input. The QR code encodes this link. The NFC variant is the same link with the method marked as NFC, so it can be written to any standard NFC tag. Both variants MUST run through the same check-in flow and differ only in the recorded method.
- **FR-003**: QR code and NFC check-in MUST require an authenticated user. Only the booking owner or an administrator may confirm a booking for the identified room, which are the same rules as the existing manual check-in. Any other user MUST NOT be able to confirm someone else's booking.
- **FR-004**: Check-in (QR code, NFC or the manual "Einchecken" action) MUST only succeed for a booking in `RESERVED` status whose check-in window is open: from 10 minutes before the start time up to and including start time + 5 minutes, before its end time, and — while the start time has not been reached — only if no other `RESERVED` or `ACTIVE` reservation of the room is ongoing. Otherwise, the user MUST receive a message explaining why (too early / room still occupied, expired, already in use, no matching booking). A booking checked in early counts as in use from its check-in time (room status "occupied", device control, room display).
- **FR-005**: A successful on-site confirmation MUST transition the booking from `RESERVED` to `ACTIVE` using the same lifecycle rules as the existing manual check-in.
- **FR-006**: Whenever a booking becomes `ACTIVE`, by any method including the existing manual check-in, the system MUST switch on the room's lighting, switch on its ventilation, and unlock its door.
- **FR-007**: The room status shown on the room display and in reservation lists MUST be "occupied" while a booking for that room is `ACTIVE`. Showing occupancy in room search or on the map is out of scope.
- **FR-008**: When an `ACTIVE` booking ends (end time reached, completed early, or cancelled), the system MUST mark the room as "available" and switch off its lighting and ventilation, unless another booking for the same room is already `ACTIVE`.
- **FR-009**: Release at the regular end time MUST happen automatically, without user action, within 60 seconds after the end time, including after system downtime.
- **FR-010**: A failure of any single device command MUST NOT prevent the booking status change or the remaining device commands. Each failure MUST be logged in structured form. On an on-site check-in (QR code or NFC), failed devices MUST be shown to the person checking in; after a manual check-in, the device controls and room display show the actual state.
- **FR-011**: Repeated confirmations for an already `ACTIVE` booking MUST be idempotent: no state change and no repeated device commands.
- **FR-012**: The door MUST be a controllable room device with the states "locked" and "unlocked", in addition to the existing lighting and ventilation devices. It MUST appear in the booking user's existing device controls (feature 006).
- **FR-013**: The system MUST record which method checked in each booking (QR code, NFC, manual), who checked it in, and when.
- **FR-014**: The system MUST generate a real, scannable QR code for each room. It MUST be shown on the room display, and administrators MUST be able to obtain it for printing, together with the room's NFC check-in link for writing to a tag.
- **FR-015**: Motion-sensor events MUST be accepted only from the room's sensor or from an administrator's simulation (FR-019), and MUST NOT be submittable by ordinary users.
- **FR-016**: When a motion sensor reports presence while a booking for its room is `ACTIVE`, the system MUST record the time of the most recent detected presence for that booking. Motion events MUST NOT change any booking status or switch any device. Automatically releasing rooms without detected presence is out of scope.
- **FR-017**: The room display MUST show read-only icons with the current state of the room's lighting (on/off), ventilation (on/off) and door (locked/unlocked) to every signed-in viewer, and MUST update them through its regular refresh. Viewers MUST NOT be able to change device states from the display.
- **FR-018**: Device states MUST remain simulated: commands go through the existing stub device integration, and the last confirmed state is what all views show. No physical device protocol is introduced.
- **FR-019**: Because no physical sensor exists, administrators MUST be able to simulate a motion event for a room with a "Bewegung simulieren" action on the room page or room display. A simulated event MUST be processed exactly like a real sensor event (FR-016), and the most recent detected presence time MUST be visible to the administrator.
- **FR-020**: The system MUST offer a read-only, machine-readable room status per room, containing at least the room status ("available", "reserved", "occupied") and the states of lighting, ventilation and door. It MUST be suitable for regular fetching by external devices, so that a future e-ink display or LED controller can obtain everything it needs from the backend without the backend sending anything to the device. It MUST NOT allow any changes, it MUST require a signed-in user like all other application routes, and it MUST NOT contain personal data (no names, no booking details).
- **FR-021**: The room display and the device control page MUST offer a way back to the room page; on the room page, device control MUST open as a popup that can be closed again.
- **FR-022**: Administrators MUST be able to view and change the early check-in time (0–60 minutes before the start) and the check-in grace period (1–30 minutes after the start) on an admin settings page. Check-in by every method and the automatic expiry of unattended bookings MUST use the current values; values outside the ranges MUST be rejected with an explanation.

### Key Entities

- **Reservation (existing)**: Room booking with owner, room, start and end time, and status (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`). This feature adds the check-in record (method, actor, time) and the most recent detected presence time.
- **Room Check-In Identifier**: The stable identifier of a room, encoded in its QR code and NFC tag, that tells the system which room the user is standing at.
- **Check-In Record**: Part of the reservation: the method (QR, NFC, manual), who checked in, and when. Rejected attempts are not stored; they are logged with room, method and reason.
- **Presence Signal**: The most recent time a motion sensor detected presence during an `ACTIVE` booking. It is evidence of actual use, not a check-in.
- **Room Device (existing, extended)**: Controllable equipment of a room. Lighting and ventilation exist already; the door (locked/unlocked) is new.
- **Presence Sensor**: A motion sensor belonging to exactly one room, which reports presence events to the system. In this release every room has one simulated sensor, triggered by administrators.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A booking user can confirm presence by QR code or NFC in under 15 seconds from scan to confirmation (when already signed in).
- **SC-002**: In 95% of activations, lighting, ventilation and door reach their target state, and the room display shows "occupied" with the matching device icons within one display refresh interval after confirmation.
- **SC-003**: 100% of ended bookings release the room ("available") and switch off lighting and ventilation within 60 seconds after their end time, unless a following booking for the same room is already in use.
- **SC-004**: 0% of on-site check-ins by non-admin users activate a booking of a different user, and 0% activate a booking outside its check-in window or in a different room.
- **SC-005**: Rooms with QR codes posted show a lower no-show expiration rate (feature 007) than before introduction, measured over the first four weeks.
- **SC-006**: 100% of failed device commands during activation or release appear in the logs with room, device and booking.
- **SC-007**: 0% of motion-sensor events change a booking status. 100% of motion events during an `ACTIVE` booking update its most recent presence time.

## Assumptions

- No physical hardware is integrated in this release; the software demonstrates a working concept. Lighting, ventilation and door use the existing stub device integration from feature 006, which acknowledges every command and keeps the last confirmed state. That stored state is treated as the actual device state for all views. A later hardware adapter can replace the stub without changing the views.
- Real hardware is out of scope for this feature. Display and device models (e-ink display, LED) are still undecided. A later feature will connect them, preferably by having the devices fetch the read-only room status (FR-020) so that the backend needs no device-specific protocol. Until then, the read-only room status requires a signed-in user; that feature also covers device sign-in, an e-ink-friendly view (high contrast, slow refresh) and the meaning of the LED.
- NFC needs no reader integration: a tag only holds the room's NFC check-in link, which the phone opens in the browser. Without a tag, opening that link directly stands in for the tap. Reading student ID cards or other badges at a reader is out of scope.
- The check-in window and the 5-minute grace period come from feature 007 and are not changed by this feature.
- The existing manual check-in (the "Check-In / Activate" action in the app) remains available and also triggers the room preparation actions.
- The "occupied" and "available" status is derived from the reservation status, as already displayed by the room status visualization (feature 009). No separate status is introduced.
- The door is not re-locked automatically at the end of a booking. Locking or other access-control policies (for example opening hours) are out of scope.
- The projector is not switched automatically. It stays under manual control via the existing room device controls.
- Room preparation is triggered only by a booking becoming `ACTIVE`, not by bookings that are merely `RESERVED`.
- Room search and the floor map do not show a live occupancy status today; adding one is a separate feature.
- The admin-editable grace period (FR-022) replaces the fixed 5-minute constant that feature 007 deferred "until system-wide administrative settings exist"; 5 minutes stays the default.
- Who may check in on site follows the existing manual check-in rules (booking owner or administrator). Other attendees are not modelled.
- Motion sensors exist only as a simulation in this release. Every room counts as having a simulated sensor, and administrators trigger its events by hand. Automatic or random event generation is out of scope.
- Using the motion-sensor presence signal to release rooms early or end bookings early is a separate, later feature.
