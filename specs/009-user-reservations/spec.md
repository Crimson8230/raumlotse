# Feature Specification: User Reservation Integration

**Feature Branch**: `009-user-reservations`

**Created**: 2026-09-28

**Status**: Draft

**Input**: User description: "Integrate reservations with user management: we require two fields for room reservations: first, a genuine "createdBy" referencing the user ID of the current user; second, a text field "reservedFor" that can be freely set by the user, pre-filled by default with the name of the creating user (mandatory field). This gives us the ability to provide a listing on the app entry page (table with links) "My Upcoming Reservations" (or is there a better description for it?). There we want to see: reservation time, room, and duration. The list refers exclusively to the "createdBy" of the reservation."

## Clarifications

### Session 2026-09-28

- Q: Which title and display scope should be used for the personal reservations overview on the application's home page? → A: "My Upcoming Reservations" (localized in German UI as "Meine nächsten Reservierungen") showing strictly upcoming and currently active reservations (sorted chronologically ascending by start time). Past, completed, or cancelled bookings are excluded from this overview.
- Q: Should the creator (or an authorized user) be allowed to edit the `reservedFor` text on an existing upcoming reservation? → A: Editable for upcoming `RESERVED` bookings alongside notes and expected attendee count; changing room, layout, or time slot still requires cancelling and re-booking.
- Q: Should the "My Upcoming Reservations" table on the home page support direct actions (such as cancellation), or should reservation management remain centralized on the room detail page? → A: Navigation-only; clicking the room name links to the room detail view (`/rooms/:id`) where all existing reservation operations (cancellation, status updates, editing) remain centralized.
- Q: Should the "My Upcoming Reservations" table on the home page limit the number of displayed upcoming reservations to keep the dashboard compact? → A: Display up to the next 10 upcoming reservations ordered chronologically ascending.
- Q: How should the "My Upcoming Reservations" section behave on the home page when a visitor is not logged in? → A: Hidden completely; unauthenticated visitors see only general content and the section is omitted entirely.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create Reservation Linked to Authenticated User and Designated Person (Priority: P1)

An authenticated user reserving a room needs the reservation to record their user identity as the creator (`createdBy`), while designating who the reservation is for (`reservedFor`). The booking form automatically pre-fills the mandatory `reservedFor` field with the authenticated user's display name, but allows the user to overwrite it if booking on behalf of someone else (e.g., a colleague, student group, or guest lecturer).

**Why this priority**: Core prerequisite for user-reservation integration. Establishing ownership and the designated reservation recipient enables personal reservation views, accountability, and role-based management.

**Independent Test**: As an authenticated user, open the room booking form. Verify that `reservedFor` is pre-populated with the user's display name. Submit the reservation and verify that the record persists the authenticated user's ID as `createdBy` and the display name as `reservedFor`. Then create a second reservation modifying `reservedFor` to another name and verify that the custom name is stored with the user's ID.

**Acceptance Scenarios**:

1. **Given** an authenticated user is on a room booking form, **When** the form loads, **Then** the `reservedFor` input field is pre-populated with the user's current display name.
2. **Given** an authenticated user with pre-filled `reservedFor`, **When** the user submits a valid reservation without editing the field, **Then** the reservation is created with `createdBy` set to the authenticated user's ID and `reservedFor` matching the user's display name.
3. **Given** an authenticated user who is booking on behalf of another person or project group, **When** the user overwrites `reservedFor` with custom text (e.g., "Web Project Group") and submits, **Then** the reservation is created with the custom `reservedFor` text while `createdBy` remains the authenticated user's ID.
4. **Given** a reservation form where the user clears the `reservedFor` field, **When** the user attempts to submit, **Then** the submission is blocked with a validation error indicating that `reservedFor` is mandatory.
5. **Given** an unauthenticated visitor, **When** attempting to access the room reservation creation action, **Then** the system requires authentication before allowing reservation submission.

---

### User Story 2 - View "My Upcoming Reservations" on the Home Page (Priority: P1)

An authenticated user visiting the application's home/entry page needs a dedicated overview titled "My Upcoming Reservations" (in German: "Meine nächsten Reservierungen") showing their upcoming room bookings in a clear table, including the reservation time, room name (as a clickable link), and duration.

**Why this priority**: Directly delivers the primary end-user value stated in the request: users can instantly see their upcoming commitments upon entering the application and jump directly to the relevant room details.

**Independent Test**: Create multiple reservations across different users and dates. Log in as User A and navigate to the home page. Verify that the "My Upcoming Reservations" table lists only User A's upcoming bookings, sorted by start time ascending, displaying reservation time, room name (linked to the room), and duration. Verify that User B's bookings and past/cancelled bookings are not listed.

**Acceptance Scenarios**:

1. **Given** an authenticated user with upcoming reservations, **When** visiting the home page, **Then** a table titled "My Upcoming Reservations" (or localized "Meine nächsten Reservierungen") displays the user's upcoming reservations with columns for Reservation Time (date and time window), Room (room name), and Duration (calculated duration).
2. **Given** reservations belonging to different users, **When** an authenticated user views the home page, **Then** the list strictly displays reservations where `createdBy` matches the authenticated user.
3. **Given** multiple upcoming reservations for the user, **When** displayed in the table, **Then** they are ordered chronologically by start time ascending (soonest upcoming reservation first), up to a maximum of 10 entries.
4. **Given** an upcoming reservation in the table, **When** the user clicks on the room name link, **Then** the user is navigated directly to that room's detail view.
5. **Given** an ongoing reservation (current time is between start and end time), **When** the user views the home page, **Then** the ongoing reservation is included in "My Upcoming Reservations".
6. **Given** an authenticated user who has no upcoming reservations, **When** visiting the home page, **Then** an informative empty state message (e.g., "No upcoming reservations found" / "Keine anstehenden Reservierungen vorhanden") is displayed instead of an empty table.

---

### User Story 3 - Unauthenticated Visitor Experience on Home Page (Priority: P2)

An unauthenticated visitor accessing the application's home page sees public, general content (e.g., system status, general navigation) with the "My Upcoming Reservations" section completely hidden, ensuring the landing page remains clean, uncluttered, and free of personalized empty states.

**Why this priority**: Preserves a clean, professional first impression for guests without showing empty or inaccessible personalized UI components.

**Independent Test**: Visit the home page in an unauthenticated session. Verify that "My Upcoming Reservations" is completely omitted from the rendered page and only public page content is visible.

**Acceptance Scenarios**:

1. **Given** an unauthenticated visitor on the home page, **When** viewing the page, **Then** the "My Upcoming Reservations" section is completely hidden.
2. **Given** an unauthenticated visitor who logs in, **When** viewing the home page as an authenticated user, **Then** the "My Upcoming Reservations" section becomes visible.

---

### User Story 4 - Display Designated Person (`reservedFor`) in Room and Reservation Details (Priority: P3)

Users and facility managers viewing a room's reservation schedule need to see for whom each reservation was booked (`reservedFor`), distinguishing the actual meeting organizer/beneficiary from the administrative account (`createdBy`) that entered the booking.

**Why this priority**: Completes transparency for room occupancy so that participants and staff know who is using the room, especially when an assistant or team lead booked on behalf of others.

**Independent Test**: View a room detail page with scheduled reservations. Confirm that each reservation displays `reservedFor` clearly alongside time, layout, and creator information.

**Acceptance Scenarios**:

1. **Given** existing reservations on a room's detail page, **When** viewed by any user, **Then** the `reservedFor` name is displayed prominently for each reservation entry.
2. **Given** a reservation booked on behalf of another person, **When** viewing detailed reservation information, **Then** both the `reservedFor` name and the `createdBy` creator identity are visible.
3. **Given** an existing reservation in `RESERVED` status, **When** the creator (or authorized user) edits the reservation metadata, **Then** the user can update the `reservedFor` field with non-blank text without altering the room, layout, or time window.

---

### Edge Cases

- **User modifies display name in profile**: Existing reservations retain the exact `reservedFor` text entered at booking time to preserve historical integrity, while `createdBy` continues to match the user's permanent account ID.
- **Session expiration during booking**: If a user's session expires while filling out the reservation form, submitting prompts the user to re-authenticate without silently attributing the booking to an anonymous or invalid user ID.
- **Cancelled or completed reservations**: Reservations that are cancelled, expired, or completed are excluded from the "My Upcoming Reservations" home page table, ensuring the table remains focused on actionable upcoming commitments.
- **Duration display across different lengths**: Short bookings (e.g., 45 minutes) display in minutes (e.g., "45 min" or localized "45 Min."), while bookings of an hour or more display in hours and minutes (e.g., "1 hr 30 min" or localized "1 Std. 30 Min.").
- **High volume of upcoming bookings**: If a user has numerous upcoming bookings, the home page displays the nearest upcoming bookings (capped at 10 entries) in a clean, scannable layout.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST associate every newly created room reservation with the authenticated user's unique account identifier as `createdBy`.
- **FR-002**: The system MUST prevent reservation creation when the user is not authenticated.
- **FR-003**: The system MUST include a required text field `reservedFor` on every room reservation.
- **FR-004**: The reservation form MUST pre-fill the `reservedFor` input field with the currently authenticated user's display name by default.
- **FR-005**: The system MUST permit the user to edit or overwrite the `reservedFor` field with any non-blank text before submitting the reservation.
- **FR-006**: The system MUST reject any reservation submission where `reservedFor` is empty, blank, or exceeds 255 characters.
- **FR-007**: The application home/entry page MUST display a dedicated overview section titled "My Upcoming Reservations" (localized as "Meine nächsten Reservierungen") for authenticated users.
- **FR-008**: The "My Upcoming Reservations" overview MUST present a table containing at minimum: Reservation Time (start date and time window), Room (room name as a link), and Duration (calculated duration).
- **FR-009**: The room name link in the "My Upcoming Reservations" table MUST navigate the user to the corresponding room detail page where reservation management actions reside; the home page table itself remains read-only navigation.
- **FR-010**: The "My Upcoming Reservations" table MUST strictly filter entries where `createdBy` matches the currently authenticated user's unique account identifier.
- **FR-011**: The "My Upcoming Reservations" table MUST include upcoming and currently active reservations (end time is in the future and status is `RESERVED` or `ACTIVE`), ordered chronologically by start time ascending, displaying up to the next 10 upcoming reservations.
- **FR-012**: The system MUST display an informative empty state message when an authenticated user has no upcoming reservations.
- **FR-013**: The system MUST hide the "My Upcoming Reservations" section completely when the user is not authenticated, presenting only public and general home page content.
- **FR-014**: The room detail page and reservation schedule views MUST display `reservedFor` for each reservation.
- **FR-015**: The system MUST allow the creator (or authorized user) to update the `reservedFor` field on existing reservations in `RESERVED` status, validated against non-blank requirements.

### Key Entities *(include if feature involves data)*

- **Reservation**:
  - `id`: Unique identifier of the reservation.
  - `createdBy`: Unique identifier referencing the user account that created the reservation. Immutable upon creation.
  - `reservedFor`: Mandatory text descriptor (max 255 chars) indicating the intended recipient, host, or purpose of the booking.
  - `room`: Reference to the reserved room entity.
  - `startTime`: Timestamp marking the beginning of the reservation.
  - `endTime`: Timestamp marking the conclusion of the reservation.
  - `status`: Lifecycle state (`RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`).
  - `expectedAttendees`: Number of anticipated participants.
  - `seatingArrangement`: Selected layout.
  - `additionalEquipment`: Supplementary portable equipment items.
  - `note`: Optional descriptive notes.

- **User Account**:
  - `id`: Unique user identifier.
  - `displayName`: Human-readable name used to pre-fill `reservedFor` during reservation creation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Authenticated users can see their upcoming reservations on the home page immediately upon login, loading the table within 1.5 seconds under normal network conditions.
- **SC-002**: 100% of newly created reservations are persistently linked to the creating user's account identifier (`createdBy`) and contain a validated, non-blank `reservedFor` value.
- **SC-003**: 100% of room links in the "My Upcoming Reservations" table correctly navigate to the target room's detail view in a single click.
- **SC-004**: Users can complete a booking on behalf of another individual or group by modifying `reservedFor` without requiring additional permissions or workflows.
- **SC-005**: 0% of other users' reservations are exposed in an authenticated user's "My Upcoming Reservations" list.
- **SC-006**: 100% of unauthenticated visitor sessions on the home page omit the "My Upcoming Reservations" section entirely without generating client errors or unnecessary personalized data queries.

## Assumptions

- **Table Title**: "My Upcoming Reservations" (localized as "Meine nächsten Reservierungen") is adopted as the primary title on the home page; alternative phrasing such as "My Bookings" or "Upcoming Bookings" conveys equivalent meaning.
- **Temporal Scope of "Upcoming Reservations"**: Includes future bookings (`startTime > now`) and ongoing active bookings (`startTime <= now < endTime` with status `ACTIVE` or `RESERVED`), but excludes concluded (`COMPLETED`), lapsed (`EXPIRED`), or `CANCELLED` bookings.
- **Duration Display Format**: Durations are derived from `startTime` and `endTime` and formatted in human-readable terms (e.g., "45 min", "1 hr 30 min", with German locale equivalents "45 Min.", "1 Std. 30 Min.").
- **Historical Data**: Existing reservations created prior to user authentication will be handled safely (e.g., mapped to an administrative system account) to maintain system consistency.
