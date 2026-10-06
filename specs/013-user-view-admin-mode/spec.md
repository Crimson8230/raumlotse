# Feature Specification: User View, Administration Mode and Access Control

**Feature Branch**: `013-user-view-admin-mode`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Passe die Routen an: Es soll eine Nutzeransicht geben, die für die reine Buchung und Übersicht über die Räume - die Administrationsoptionen sollen in einem Administrationsmodus eingeblendet werden (diesen können nur Administratoren einschalten). Fixe in diesem Zusammenhang auch gleich fehlende Autorisierungen sowie die Besitzprüfung."

## Clarifications

### Session 2026-10-06

- Q: Soll der Server Administrationsfunktionen auch ablehnen, wenn ein Administrator den Administrationsmodus ausgeschaltet hat? → A: Nein. Der Modus steuert nur die Sichtbarkeit; die Administrator-Rolle ist die einzige Berechtigung.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Booking and Room Overview Without Admin Clutter (Priority: P1)

As a signed-in user (student, lecturer, staff, viewer), I want a clean view that only offers what I need — browsing and searching rooms, seeing room status, booking a room and managing my own bookings — so that I am not confronted with administration options I may not use.

**Why this priority**: This is the default experience for every user and the main purpose of the application.

**Independent Test**: Sign in as a non-administrator and walk through the whole application: only overview and booking functions are visible and reachable; no create/edit/delete/deactivate controls for rooms, buildings, floors, equipment, maps, connections or user roles appear anywhere.

**Acceptance Scenarios**:

1. **Given** a signed-in non-administrator, **When** the user opens any page, **Then** navigation and page content show only overview and booking functions (room list and search, room detail, room status display, my reservations, floor maps in read-only form, device control for the user's own active booking).
2. **Given** a signed-in non-administrator, **When** the user opens an administration address directly (for example the room creation page, the room edit page or the user role pages), **Then** the user is informed that the page is not available to them and no administration form or data is shown.
3. **Given** a signed-in user, **When** the user books a room, **Then** the booking flow works exactly as before and the booking appears in "my reservations".

---

### User Story 2 - Administrators Switch Administration Mode On and Off (Priority: P1)

As an administrator, I want to explicitly switch an administration mode on, so that all administration options (maintaining buildings, floors, equipment and rooms; uploading and editing maps and connections; managing user roles) appear in addition to the normal view, and switch it off again to work like any other user.

**Why this priority**: It separates daily use from maintenance and prevents accidental changes; it is the central request of this feature.

**Independent Test**: Sign in as administrator; verify the normal view first, switch the mode on and verify all administration options appear, switch it off and verify they disappear again.

**Acceptance Scenarios**:

1. **Given** a signed-in administrator, **When** the user signs in, **Then** the application starts in the normal user view with administration mode off.
2. **Given** an administrator in the user view, **When** the user switches administration mode on, **Then** all administration options become visible and the application clearly indicates that administration mode is active.
3. **Given** administration mode is on, **When** the administrator switches it off, **Then** all administration options disappear, and administration pages that are open are left for the user view.
4. **Given** a non-administrator, **When** the user looks for the mode switch, **Then** it is not offered; **And** when the user attempts to activate the mode by any other means, it stays off.
5. **Given** administration mode is on, **When** the user signs out and signs in again, or the session ends, **Then** the mode is off again.
6. **Given** a user whose administrator role is revoked while administration mode is on, **When** the user's next request is processed, **Then** administration mode is turned off and administration functions are denied.

---

### User Story 3 - Only Authorized People Can Change Master Data (Priority: P1)

As the operator of the system, I want every administrative change (buildings, floors, equipment types, rooms, including deactivate/reactivate/delete, as well as the maintenance sweep of overdue reservations) to be permitted only for administrators, regardless of how the request is made, so that regular users cannot alter or destroy master data.

**Why this priority**: Currently any signed-in user can change master data; this is a security gap and must be closed together with the new views.

**Independent Test**: As a non-administrator, attempt each administrative change through the application and through direct requests; every attempt is refused and nothing changes. Repeat as administrator (with administration mode on and off) and verify the change is applied.

**Acceptance Scenarios**:

1. **Given** a signed-in non-administrator, **When** the user attempts to create, change, deactivate, reactivate or delete a building, floor, equipment type or room, **Then** the request is refused as not permitted and the data is unchanged.
2. **Given** a signed-in non-administrator, **When** the user triggers the overdue-reservation maintenance sweep, **Then** it is refused.
3. **Given** a signed-in administrator, **When** the administrator performs the same changes, **Then** they are applied, independent of whether the administration-mode switch in the interface is on (the mode controls what is shown, the administrator role controls what is allowed).
4. **Given** any signed-in user, **When** the user reads rooms, buildings, floors, equipment types, maps and reservations' public availability, **Then** reading remains permitted.
5. **Given** an unauthenticated visitor, **When** any application function is requested, **Then** it is refused as before.

---

### User Story 4 - Reservations Can Only Be Changed by Their Owner (Priority: P1)

As a user who made a booking, I want to be the only one (besides administrators) who can edit, check in, complete, expire or cancel it and see its details, so that nobody can tamper with my bookings.

**Why this priority**: Currently any signed-in user can modify or cancel any reservation, and device control is unlocked via check-in; this is a security and trust gap.

**Independent Test**: User A creates a booking. User B tries to read details of it, edit, check in, complete, expire and cancel it: all are refused and the booking is unchanged. User A and an administrator can perform the allowed actions.

**Acceptance Scenarios**:

1. **Given** a reservation created by user A, **When** user B (not an administrator) tries to view its details, edit it, check in, complete, expire or cancel it, **Then** the request is refused and the reservation is unchanged; the response does not reveal more about the reservation than a non-existing one would.
2. **Given** a reservation created by user A, **When** user A performs an action that is allowed by the reservation's current status, **Then** it succeeds as before.
3. **Given** a reservation created by user A, **When** an administrator cancels it, **Then** it succeeds. [Assumption A2]
4. **Given** any signed-in user, **When** the user views a room's schedule, **Then** the user sees which time slots are occupied (so booking is possible) but not personal details of other users' bookings (booked-for person, note, creator). [Assumption A3]
5. **Given** a user with upcoming bookings, **When** the user opens "my upcoming reservations", **Then** all and only the user's own upcoming bookings are listed. (Today this list does not reliably show the user's own bookings; this must be corrected as part of ownership.)
6. **Given** a user whose display name changes or two users with the same display name, **When** ownership is checked, **Then** ownership follows the user's identity, never the display name.

---

### User Story 5 - Clear, Consistent Page Addresses per View (Priority: P2)

As a user, I want page addresses ("routes") that clearly separate the user view from administration, so that links are stable, bookmarkable and unambiguous about what they show.

**Why this priority**: Supports the other stories (access checks per address) but delivers little value alone.

**Independent Test**: Review the list of addresses: every address is either a user-view address or an administration address; administration addresses are refused for users who may not use them, and the behavior is the same when opened by link, bookmark or in-app navigation.

**Acceptance Scenarios**:

1. **Given** the set of application addresses, **When** they are listed, **Then** each is classified as user view (any signed-in user) or administration (administrators in administration mode) and administration addresses share a recognizable common prefix.
2. **Given** a previously valid address that moved (for example room creation, room editing, location catalog, floor map maintenance, user roles), **When** a user opens the old address, **Then** the user is forwarded to the new equivalent address or, if not permitted, informed that it is unavailable.
3. **Given** an administrator with administration mode off, **When** the administrator opens an administration address, **Then** the administrator is offered to switch the mode on (or is taken to the user view) rather than seeing a broken page.
4. **Given** a user in the user view, **When** the user opens a room, **Then** the room detail shows booking and status information only; administration controls for that room (edit, deactivate, delete) appear only in administration mode.

---

### Edge Cases

- An administrator switches the mode off while an unsaved administration form is open: the user is warned about discarding changes before leaving.
- The administrator role is revoked in another session while the mode is on: the next action is refused and the interface returns to the user view.
- The last remaining administrator turns administration mode off: nothing is locked out, since the mode can be switched on again at any time.
- A user opens a link to someone else's booking: shown as not found/not permitted without leaking details.
- Existing bookings without a recorded creator identity (created before sign-in existed): they are treated as not owned by any regular user and only administrators may manage them. [Assumption A4]
- A booking is made on behalf of another person ("booked for" is free text): ownership stays with the creator, not the named person.
- Multiple browser tabs: switching the mode in one tab is reflected in the other tabs of the same session without requiring sign-in again.
- Device control remains available only to the owner of a currently active booking; administrators do not gain device control through administration mode. [Assumption A5]

## Requirements *(mandatory)*

### Functional Requirements

**User view**

- **FR-001**: The system MUST provide a user view containing only overview and booking functions: room list and search, room detail, room status display, floor maps (read-only), booking, "my upcoming reservations", and device control for the user's own active booking.
- **FR-002**: The user view MUST NOT show or link to any administration function: creating, editing, deactivating, reactivating or deleting buildings, floors, equipment types, rooms; uploading/replacing/deleting maps; placing rooms; maintaining connections; managing user roles.
- **FR-003**: All signed-in users, regardless of role, MUST be able to use the user view; room booking MUST remain available to all signed-in users. [Assumption A1]

**Administration mode**

- **FR-004**: The system MUST offer a switch for administration mode only to users holding the administrator role.
- **FR-005**: Administration mode MUST be off after every sign-in and MUST end with the session.
- **FR-006**: While administration mode is on, the system MUST show all administration functions in addition to the user view and MUST display a persistent, clearly visible indicator of the active mode.
- **FR-007**: The system MUST NOT let a user without the administrator role turn administration mode on or use any administration function, regardless of how a request is made. The administration mode itself MUST NOT be a precondition for permitting an administrator's request: an administrator with the mode off is permitted exactly as with the mode on (the mode only controls what the interface shows).
- **FR-008**: When the administrator role is withdrawn from a user, administration mode MUST end for that user and administration functions MUST be refused from the next request on.
- **FR-009**: Administration pages MUST only be reachable when administration mode is on; for administrators with the mode off, the system MUST offer switching it on or return them to the user view.

**Page addresses**

- **FR-010**: Every page address MUST be classifiable as user view or administration; administration addresses MUST share a common recognizable prefix.
- **FR-011**: Addresses that move MUST forward to their new equivalent (or show a not-permitted notice) so existing bookmarks do not end on an empty or broken page.
- **FR-012**: Unauthenticated visitors MUST be taken to sign-in for every address except sign-in itself, and return to the requested address afterwards when it is permitted.

**Authorization of changes**

- **FR-013**: The system MUST refuse every change to buildings, floors, equipment types and rooms (create, update, deactivate, reactivate, delete) unless the requester is an administrator.
- **FR-014**: The system MUST refuse the overdue-reservation maintenance sweep unless the requester is an administrator.
- **FR-015**: Existing administrator-only rules (user role management; map, placement and connection changes) MUST continue to apply.
- **FR-016**: Authorization MUST be enforced by default for every present and future change function: a function without an explicit permission rule MUST be refused for non-administrators rather than allowed.
- **FR-017**: Reading master data (rooms, buildings, floors, equipment types, maps, connections) MUST remain permitted for all signed-in users.
- **FR-018**: A refusal MUST use a distinct "not permitted" outcome (separate from "not signed in") and MUST NOT change any data.

**Ownership of reservations**

- **FR-019**: The system MUST record the creator of each reservation by stable user identity and MUST base all ownership decisions on it, not on display names.
- **FR-020**: Only the owner or an administrator MAY edit, check in, complete, expire or cancel a reservation or read its personal details.
- **FR-021**: Requests by other users for such actions MUST be refused without altering the reservation and without revealing information beyond what a non-existing reservation would reveal.
- **FR-022**: "My upcoming reservations" MUST list exactly the signed-in user's own upcoming reservations (reserved or active, not yet ended).
- **FR-023**: A room's schedule shown to users other than owner/administrators MUST show occupied time slots only and MUST NOT disclose booked-for person, note or creator.
- **FR-024**: Device control MUST remain restricted to the owner of a currently active reservation in that room.
- **FR-025**: Reservations without a recorded creator identity MUST be manageable by administrators only.

**Traceability**

- **FR-026**: The system MUST log refused administrative and ownership-protected requests (who, what category, outcome) without recording secrets or personal booking content.

### Key Entities

- **User**: A signed-in person with identity and zero or more roles; the administrator role is the only one granting administration rights.
- **Administration Mode**: A per-session state of an administrator (off by default) that determines whether administration options are shown.
- **Reservation**: A booking of a room for a time window; has an owner (creator, by identity) and an optional "booked for" person who is not the owner.
- **Page Address (Route)**: A navigable address, classified as user view or administration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For a non-administrator, 0 administration controls or links are visible on any page, verified across all pages of the application.
- **SC-002**: 100% of administrative change operations (buildings, floors, equipment, rooms, maintenance sweep, user roles, maps, connections) are refused for non-administrators and 0 data changes result, verified by an automated test per operation.
- **SC-003**: 100% of reservation actions (view details, edit, check in, complete, expire, cancel) attempted by a different non-administrator user are refused and leave the reservation unchanged, verified per action.
- **SC-004**: An administrator can switch administration mode on and reach any administration function in no more than 2 interactions, and switch it off in 1 interaction.
- **SC-005**: A user who has made at least one future booking sees it in "my upcoming reservations" in 100% of cases and sees no one else's.
- **SC-006**: Administration mode is off after 100% of new sign-ins.
- **SC-007**: A previously valid page address opened after this change leads to the equivalent page or a clear "not available" notice in 100% of cases (no blank or error page).
- **SC-008**: Booking a room takes a regular user no more steps or time than before this change.

## Assumptions

- **A1**: All roles (administrator, university staff, student, lecturer, viewer) may use the user view and book rooms; a finer role-based restriction of booking is out of scope for this feature (all existing accounts currently hold at least the viewer role, so restricting booking would lock them out).
- **A2**: Administrators may manage (including cancel) any reservation; this is intended as an organizational override, not a regular workflow.
- **A3**: Other users need to see occupancy to book sensibly, but not personal details; the room status display shows only occupied/free and the time window.
- **A4**: Legacy reservations without creator identity are rare/test data and are managed by administrators only.
- **A5**: Administrators gain no extra device-control rights; they are bound by the same ownership rule as everyone else.
- **A6** (confirmed in clarification session 2026-10-06): Administration mode is a convenience and safety feature for the interface; the administrator role remains the source of truth for permission (a request by an administrator is permitted even if the switch is off, as the mode only controls visibility).
- **A7**: Administration mode lasts per session; it is not persisted across sign-ins and is not shared between different browsers or devices.
- **A8**: Display devices and one-time-code sign-in mentioned in feature 012 are not part of this feature.
- **A9**: Redefinition of what the other roles (staff, student, lecturer, viewer) may do beyond the user view is out of scope and may become a follow-up feature.
- **Dependency**: Builds on existing sign-in, role management (feature 003), reservations (004, 009), room device control (006) and map maintenance (012).
