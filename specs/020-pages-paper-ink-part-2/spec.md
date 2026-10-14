# Feature Specification: Pages in "Papier & Tinte", Part 2

**Feature Branch**: `020-pages-paper-ink-part-2`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Seiten in Papier & Tinte, Teil 2 (Schicht 3, Feature 020): Raumdetail/Buchen nach Lektorat-Board mit Abschnitt „Raum auf einen Blick“ (Sitzordnungen, Ausstattung, Zugang) unter „Meine Buchungen“, Belegungen des Tages, Formular rechts; Mono für Zeiten und Serif für Raumnamen in zusammengesetzten Textzeilen (Belegungslisten der Auslastungsseite); Katalog- und Rollenlisten auf DataList; Barrierefreiheit (Pflichtfeld-Hinweis in allen Formularen, 12-px-Großbuchstaben-Beschriftungen C2, Footer-Hilfelink B1, Navigationsbreite bei 1280 px). Kein Backend, kein Veranstaltungstitel, keine Studierenden-Ansicht, kein Standorte-Master-Detail."

## Context

Feature 019 (Part 1) applied the shared building blocks to all pages: skip link, room rows with strips, error pattern, tables, typography. What remains of layer 3 needs **no new data and no new behavior** but changes page layout or touches many small places: the room detail page still has its pre-design structure, some composed text lines mix fonts, catalog and role lists are not yet `DataList`s, and several accessibility review items are open.

Everything that needs backend changes or new product decisions (event title, floor order and location editor, role restriction for booking, student view, floor display, door sign) stays out of scope and is covered by layer 4 features.

The audience is lecturers and administrators who book and manage rooms, and keyboard and screen-reader users in particular.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See a room and book it on one page (Priority: P1)

As a lecturer, I want the room detail page arranged like the "Lektorat" board: the room's own bookings and the day's occupancy on the left, the booking form on the right, and a "Raum auf einen Blick" section with seating arrangements, equipment and access below my bookings, so that I can judge the room and book it without scrolling back and forth.

**Why this priority**: It is the main booking page and the largest visible gap to the design.

**Independent Test**: Open a room as a lecturer with existing bookings; verify order of sections, that the form sits beside the day overview on wide screens, and that booking still works.

**Acceptance Scenarios**:

1. **Given** the room detail page of an active room, **When** it is shown on a wide viewport, **Then** "Meine Buchungen" and the day's occupancy appear in the left column and the booking form in the right column.
2. **Given** the same page, **When** the left column is read top to bottom, **Then** "Meine Buchungen" comes first, then the day's occupancy, then "Raum auf einen Blick".
3. **Given** "Raum auf einen Blick", **When** it is shown, **Then** it lists seating arrangements with capacity, equipment, and access information as a labeled definition-style list; a part without data says so in words ("keine Angabe").
4. **Given** a narrow viewport, **When** the page is shown, **Then** the sections stack in a sensible reading order (form directly after "Meine Buchungen") without horizontal scrolling.
5. **Given** the user may not reserve, **When** the page is shown, **Then** no form is shown and the overview sections still appear unchanged.
6. **Given** the day's occupancy, **When** it is shown, **Then** each booking appears with its time range in monospace and its state as pattern and word.

---

### User Story 2 - Read composed lines in the right type (Priority: P2)

As a user of the occupancy pages, I want times in monospace and room names in serif also inside composed text lines (e.g. "Raum 2.14 · 10:00–12:00"), so that the type system is consistent everywhere.

**Why this priority**: Visual consistency; small and independent.

**Independent Test**: Open the occupancy page with bookings; inspect each composed line: room names use serif, times and numbers use monospace.

**Acceptance Scenarios**:

1. **Given** an occupancy list line with room name and time range, **When** it is shown, **Then** the room name is serif and the times are monospace, while the surrounding text keeps its face.
2. **Given** such a line is read by a screen reader, **When** it is announced, **Then** the text content and order are unchanged from before.

---

### User Story 3 - Catalog and role lists as real lists with one layout (Priority: P2)

As an administrator, I want building, floor, equipment, connection and role lists to be built on the shared list block, so that they look alike, announce their item count and show an empty-state text.

**Why this priority**: Consistency and structure; no new function.

**Independent Test**: Open each catalog and the role page with and without records; check list semantics, empty text and unchanged actions.

**Acceptance Scenarios**:

1. **Given** a catalog or role list with records, **When** it is shown, **Then** it is a real list with one item per record, an accessible name, and the same actions as before.
2. **Given** a list without records, **When** it is shown, **Then** an empty-state text appears instead of an empty area.
3. **Given** an action in a list item, **When** it is operated, **Then** behavior, permissions and confirmation steps are exactly as before.

---

### User Story 4 - Forms and chrome that meet the accessibility review (Priority: P2)

As a keyboard or assistive-technology user, I want every form to explain the required marking once, small labels to be readable, a consistent help link in the footer, and an administration navigation that fits, so that the open review items B1, C2 and the navigation check are closed.

**Why this priority**: Closes documented review findings; independent of the layout work.

**Independent Test**: Open every form page; check the required hint. Check footer on several pages; check label sizes; open administration mode at 1280 px.

**Acceptance Scenarios**:

1. **Given** any form with at least one required field, **When** it is shown, **Then** one hint explains the required marking.
2. **Given** any page with the application frame, **When** its footer is shown, **Then** it contains a help/contact link at the same position with the same wording.
3. **Given** a small caption or label previously set in 12 px capitals with wide letter spacing, **When** it is shown, **Then** it is at least 14 px with normal letter spacing and still clearly distinct from body text.
4. **Given** administration mode with all links visible at 1280 px, **When** the navigation is shown, **Then** it is verified to fit in one line or wrap in a controlled way without overlap, and the result is documented.

---

### User Story 5 - Verified in the browser (Priority: P3)

As the project team, I want an automated accessibility check and a visual review of the changed pages, so that regressions are found before merge.

**Why this priority**: Quality gate for the above; no user-facing function of its own.

**Independent Test**: Run the accessibility check on all pages in scope; walk the visual-review checklist.

**Acceptance Scenarios**:

1. **Given** all pages in scope, **When** the accessibility check runs, **Then** it reports no violations for labels, table/list structure, landmarks, or contrast.
2. **Given** `prefers-reduced-motion` is set, **When** pages are used, **Then** no non-essential animation runs (verified, not only declared).

### Edge Cases

- Room without seating arrangements, equipment or access data: each part shows "keine Angabe" in words; the section is still present.
- Day with no bookings: the occupancy shows an empty-state text, not an empty area.
- User has no own bookings in this room: "Meine Buchungen" shows an empty-state text and the form stays on the right.
- Many bookings on one day: the left column grows; the form stays reachable (sticky position is optional, not required).
- Long room names or equipment names wrap without overflow.
- Inactive or forbidden rooms and loading/error states keep exactly one main area and the skip-link target.
- Rooms with check-in codes (administration): the existing codes panel keeps its place after "Raum auf einen Blick".
- Composed lines whose time or name is missing render without empty markup.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The room detail page MUST show, in the main column, "Meine Buchungen", the day's occupancy, and "Raum auf einen Blick" in this order, and the booking form in a second column beside them on wide viewports.
- **FR-002**: "Raum auf einen Blick" MUST present seating arrangements (with maximum capacity), equipment, and access information, each with a visible label; missing data MUST read "keine Angabe".
- **FR-003**: On narrow viewports the sections MUST stack without horizontal scrolling, with the form directly after "Meine Buchungen".
- **FR-004**: Bookings in the day's occupancy MUST show time ranges in monospace and state as pattern and word, using the shared strip/chip blocks.
- **FR-005**: Users without reservation rights MUST NOT see the form; all other sections MUST remain.
- **FR-006**: Composed text lines on the occupancy pages MUST render room names in serif and times and numbers in monospace without changing their text content.
- **FR-007**: Building, floor, equipment, connection and role lists MUST be built on the shared list block with an accessible name and empty-state text, keeping their actions, permissions and confirmations unchanged.
- **FR-008**: Every form with required fields MUST show one hint explaining the required marking.
- **FR-009**: The application frame MUST provide a help link in the footer on every page, same position and wording (WCAG 3.2.6), pointing to a static help page within the application. The page uses the standard frame (skip link, one main area), is reachable without sign-in where the login page shows a footer, and contains only fixed text.
- **FR-010**: Labels and captions previously 12 px uppercase with wide letter spacing MUST be at least 14 px without strong letter spacing.
- **FR-011**: The administration navigation at 1280 px MUST be checked in a real browser; if it does not fit in one line it MUST wrap without overlap and the finding MUST be recorded in `docs/DESIGN-UMSTELLUNG.md`.
- **FR-012**: Behavior MUST NOT change: routes, permissions, data, API calls, text content other than labels and layout.
- **FR-013**: All new or changed visible elements MUST use design tokens only and keep contrast (text ≥ 7 : 1) and target size (44 px).
- **FR-014**: Existing automated tests that check texts and roles MUST be adapted, and every behavior introduced here MUST be covered by a test written first.
- **FR-015**: An automated accessibility run MUST cover all pages in scope, and `prefers-reduced-motion` MUST be verified.

### Key Entities

- **Room overview ("Raum auf einen Blick")**: Read-only summary of an existing room: seating arrangements with capacity, equipment, access. No new data.
- **Booking line**: One reservation in a list: time range, state, room name; existing data.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A lecturer can see their own bookings, the day's occupancy and the room's seating, equipment and access, and start a booking, on one screen at 1280 px without scrolling to a different page area for the form.
- **SC-002**: 100 % of composed room/time lines in scope use the right face for names and times.
- **SC-003**: 100 % of catalog and role lists are real lists with an empty-state text.
- **SC-004**: 100 % of forms with required fields explain the required marking once; 100 % of pages carry the footer help link.
- **SC-005**: No text smaller than 14 px in capitals with strong letter spacing remains in scope.
- **SC-006**: The automated accessibility run reports zero violations for labels, structure, landmarks and contrast on all pages in scope.
- **SC-007**: The existing automated test suite passes after adaptation, with no test removed without replacement.

## Assumptions

- **"Meine Buchungen"** is the current user's reservations for this room, from the existing reservation list; no new query is introduced.
- **Access information** is whatever the room already carries (e.g. access notes, check-in rules); if the data model has none, the part reads "keine Angabe" and a model change is a layer-4 item.
- **Help link target** (decided 2026-10-14): a new static help page inside the application, reachable from the footer on every page. It contains short fixed German text (how to find and book a room, what the strip patterns mean, who to contact); the contact address is a placeholder to be set by the project. No backend, no editing.
- **Out of scope**: editable help content, event title, student view, locations master-detail and floor order, role restriction for booking, door sign and floor display, review items B2, B4, C3, room number as its own field.
- **Language**: all new labels German, consistent with the existing translation.
- **Dependencies**: building blocks (018), Part 1 changes (019), design tokens from layer 1.
