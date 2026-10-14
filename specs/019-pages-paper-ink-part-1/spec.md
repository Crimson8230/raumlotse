# Feature Specification: Pages in "Papier & Tinte", Part 1

**Feature Branch**: `019-pages-paper-ink-part-1`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Seiten auf „Papier & Tinte“ umstellen, Teil 1 (Schicht 3 der Designumstellung, docs/DESIGN-UMSTELLUNG.md Abschnitt 3): Sprunglink und Hauptinhalt-Ziel auf allen Seiten; Raumliste als Listenzeile mit Streifen, Jetzt-Marke und genau einer Aktion; Hauptaktionen als primäre Schaltflächen; Fehlermuster und Feldlabels statt technischer Feldnamen in den Formularen; Tabellen mit Beschriftung und Spaltenköpfen, echte Listen; Zeiten in Monospace, Raumnamen in Serif; Navigation im Administrationsmodus bei 1280 px ohne Umbruch. Nur mit den Bausteinen aus Feature 018; kein Backend, keine neuen Funktionen."

## Context

Feature 018 delivered the shared building blocks (time-window strip, chip, field, error message, skip link, data list, data table). No page uses them yet: the skip link is not mounted, `.btn-primary` is unused, room rows are plain text, tables lack captions, and validation errors show technical field names such as "startTime: darf nicht null sein".

This feature adopts the blocks in the existing pages for the parts that need **no new data and no new behavior**. Larger page redesigns (room detail/booking layout, locations master-detail, student view) and everything that needs backend changes (event title, floor order, role restriction) are deliberately **out of scope** and covered by follow-up features.

The audience is every user of Raumlotse, especially keyboard and screen-reader users, for whom the skip link, labels, table structure and error wording make the difference.

## Clarifications

### Session 2026-10-14

- Q: Woher kommen die Tageszeitfenster für die Streifen in der Raumliste? → A: Je Raum über die vorhandene Belegungsabfrage, nur für sichtbare Zeilen (begrenzt, Rest beim Nachladen); bis zur Antwort zeigt der Streifen „lädt“.
- Q: Zählt der Favoriten-Schalter als zweite Aktion in der Raumzeile? → A: Nein; er bleibt als Zustandsschalter in der Zeile, die eine Aktion ist „Raum öffnen/buchen“.
- Q: Wie erscheinen Verwaltungsaktionen (Bearbeiten, Deaktivieren, Löschen) in der Raumzeile im Administrationsmodus? → A: Die Hauptaktion bleibt; die Verwaltungsaktionen stehen als abgesetzte, sekundär gestaltete Gruppe „Verwalten“ in der Zeile, nicht primär.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reach the content and recognize errors on every page (Priority: P1)

As a keyboard or screen-reader user, I want a "Zum Inhalt springen" link as the first focusable element of every page and a clearly marked main content area, so that I do not tab through the navigation on every page.

**Why this priority**: It is the smallest change with the largest accessibility effect, touches every page, and is a prerequisite for all other page work.

**Independent Test**: Open any page, press Tab once; the skip link becomes visible; activating it moves focus into the main content.

**Acceptance Scenarios**:

1. **Given** any page of the application, **When** the user presses Tab for the first time, **Then** the skip link "Zum Inhalt springen" receives visible focus.
2. **Given** the skip link has focus, **When** the user activates it, **Then** keyboard focus moves to the main content and the next Tab stop is inside it.
3. **Given** a page in a loading, error or "not available" state, **When** it is shown, **Then** the skip link target still exists exactly once.
4. **Given** any page, **When** its structure is inspected, **Then** it has exactly one main content area.

---

### User Story 2 - Scan the room list and act in one step (Priority: P1)

As a person looking for a room, I want each room in the list as one row with its number, its name, its day strip with the "Jetzt" marker, and exactly one clear action, so that I can see availability without opening each room.

**Why this priority**: The room list is the most used page and the main place where the design's pattern language (strip + word) becomes visible.

**Independent Test**: Open the room list with rooms that are free, booked now, and blocked; verify each row's strip, its words, and that each row offers one primary action.

**Acceptance Scenarios**:

1. **Given** the room list, **When** it is shown, **Then** each room appears as one row with room number, room name in serif, a strip for the day, and one action.
2. **Given** a room that is booked at the current time, **When** the row is shown, **Then** the strip shows the "Jetzt" marker inside a hatched window and the state is also given as a word.
3. **Given** a screen-reader user, **When** a row is focused, **Then** the strip's accessible name lists all windows and the current state in words.
4. **Given** the search or filter result is empty, **When** the list is shown, **Then** an empty-state text is displayed instead of an empty area.
5. **Given** the room list is open for more than one minute, **When** time passes, **Then** the "Jetzt" marker moves without reloading the page.

---

### User Story 3 - Correct form errors without guessing (Priority: P1)

As a person filling in booking, login, room or administration forms, I want every field labeled in plain German, required fields marked, and errors shown as symbol + "Fehler:" + text naming the field by its label, so that I know exactly what to change.

**Why this priority**: Forms are where users fail; technical field names in error messages are the most visible quality gap.

**Independent Test**: Submit the booking form with an empty start time; the error names "Beginn" (not `startTime`), is attached to the field, and is announced.

**Acceptance Scenarios**:

1. **Given** a form field, **When** it is shown, **Then** it has a visible label bound to the input and, if required, a visible "Pflichtfeld" marking in words.
2. **Given** the server rejects a submission with a technical field name, **When** the error is shown, **Then** the label of that field appears, not the technical name.
3. **Given** an error for a specific field, **When** it is shown, **Then** it appears next to the field as symbol + "Fehler:" + text and the field is marked invalid.
4. **Given** an error that belongs to no field, **When** it is shown, **Then** it uses the same pattern at the top of the form and is announced when it appears.
5. **Given** a form that has required fields, **When** it is shown, **Then** a single hint explains what the required marking means.
6. **Given** a booking form with date and time, **When** it is shown, **Then** date and time are entered with the platform's native date and time inputs.

---

### User Story 4 - Read tables and lists with a screen reader (Priority: P2)

As a screen-reader user, I want every table to have a caption and column/row headers and every list to be a real list, so that I can navigate by structure and hear what a table is about.

**Why this priority**: Pure structure change with high accessibility value, independent of visual work.

**Independent Test**: Inspect each table and list page with the accessibility tree: tables expose caption and header scope; lists expose list and list-item roles with item counts.

**Acceptance Scenarios**:

1. **Given** any data table in the application, **When** it is shown, **Then** it has a caption naming its content and column headers (and a row header where rows have an identifying name).
2. **Given** any list of records (reservations, rooms, buildings, floors, roles), **When** it is shown, **Then** it is a real list with one list item per record.
3. **Given** a table or list without records, **When** it is shown, **Then** an empty-state text is shown, not an empty table.

---

### User Story 5 - Consistent typography and a calm admin navigation (Priority: P2)

As a user, I want room names always in the serif face and dates, times and numbers always in the monospace face, and in administration mode a navigation bar that does not wrap at 1280 px, so that the application looks and reads consistently.

**Why this priority**: Visual consistency and a layout defect fix; valuable but not blocking.

**Independent Test**: Compare the same room name and time on the list, detail, reservation and display pages; and open administration mode at 1280 px width.

**Acceptance Scenarios**:

1. **Given** a room name anywhere in the application, **When** it is shown, **Then** it uses the serif face.
2. **Given** a date, time or time range anywhere in the application, **When** it is shown, **Then** it uses the monospace face.
3. **Given** administration mode with all links visible and the viewport at 1280 px width, **When** the navigation is shown, **Then** links and the mode indicator fit on one line without overlap.
4. **Given** a narrower viewport, **When** the navigation does not fit, **Then** it wraps in a controlled way and remains fully usable by keyboard.

---

### User Story 6 - Recognize the main action (Priority: P3)

As a user, I want the one main action of a page or form (e.g. "Buchen", "Reservierung bestätigen", "Speichern") to look like the primary button and secondary actions to look secondary, so that I know where to continue.

**Why this priority**: Orientation aid; lowest risk and effort.

**Independent Test**: On each page with a main action, verify exactly one primary-styled button per form/section.

**Acceptance Scenarios**:

1. **Given** a form with a submit action, **When** it is shown, **Then** the submit button is styled as primary and other buttons are not.
2. **Given** a page with several sections, **When** it is shown, **Then** each section has at most one primary action.

### Edge Cases

- A room has no bookings today: the strip reads "frei" over the whole range and the row still has its single action.
- A room has many windows in one day: the strip's accessible name stays complete and the row does not grow in height.
- A per-room window query fails or is slow: the row stays usable, its strip shows "nicht verfügbar" in words, and other rows are unaffected.
- The user's viewport is very narrow (phone): rows stack number/name above strip and action without horizontal page scrolling.
- Long room names or building names: they wrap instead of overflowing; the strip keeps its width.
- Server error text arrives for a field the label map does not know: the technical name is shown in readable form rather than hidden.
- The current time is outside the strip's range (night): no "Jetzt" marker, no wrong position.
- Pages that render several `main`-like states (loading, forbidden, error): the skip-link target is never missing or duplicated.
- The room display (Türschild) page is a full-screen display for a wall device: its visual conversion is out of scope; it only receives the main content target, because the skip link comes from the shared application frame.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every page that shows the standard application frame MUST provide the skip link "Zum Inhalt springen" as the first focusable element and a single main content area as its target.
- **FR-002**: Pages in loading, error or access-denied states MUST keep exactly one main content area.
- **FR-003**: The room list MUST show each room as one row with number, name, day strip (including "Jetzt" marker when applicable), and exactly one main action. For active rooms the main action is "Buchen" if the user may reserve, otherwise "Öffnen" (a link to the room). Inactive rooms (shown in administration mode only) have no main action, only the "Verwalten" group. The favorite toggle stays in the row as a state control and does not count as an action.
- **FR-004**: The strip in the room list MUST express states by pattern and word and expose all windows and the current state in its accessible name.
- **FR-005**: The "Jetzt" marker MUST update while the page stays open, at least once per minute.
- **FR-006**: The room list MUST show an empty-state text when no room matches.
- **FR-007**: All forms MUST use the shared field block: visible label, hint where needed, visible and verbal required marking.
- **FR-008**: All form errors MUST use the shared error pattern (symbol + "Fehler:" + text), attached to the field where one is known, and announced to assistive technology.
- **FR-009**: Error messages that name fields MUST show the user-facing label, never the technical field name.
- **FR-010**: Forms with required fields MUST show one explanation of the required marking.
- **FR-011**: The booking form MUST use native date and time inputs.
- **FR-012**: Every data table MUST have a caption, column headers with scope, and a row header where rows have a natural name.
- **FR-013**: Every list of records MUST be a real list with list items and an empty-state text.
- **FR-014**: Room names MUST use the serif face and dates, times, and numbers the monospace face on all pages in scope.
- **FR-015**: In administration mode with all links visible, the navigation MUST fit on one line at 1280 px viewport width; if the available rights produce more links than fit, it MAY wrap into a second row without overlap, and all links stay keyboard-operable.
- **FR-016**: Each form and each list row counts as its own section and MUST have at most one primary-styled action. Main actions: room row "Buchen"; booking form "Reservierung bestätigen"; edit forms "Speichern"; login "Anmelden". Search ("Suchen"), cancel and management actions are secondary.
- **FR-017**: Existing behavior MUST NOT change: routes, permissions, data, API calls and text content other than labels and error wording stay as they are.
- **FR-018**: All new or changed visible elements MUST use design tokens only and keep the contrast and target-size rules of the design (text ≥ 7 : 1, targets ≥ 44 px).
- **FR-019**: Existing automated tests that check texts and roles MUST be adapted to the new structure, and every behavior introduced here MUST be covered by a test written first.
- **FR-020**: The room list MUST load strip data only for visible rows (at most 20 initially, further rows when they become visible), MUST show a "lädt" state in the strip until data arrives, and MUST NOT block the list itself while strips load.
- **FR-021**: In administration mode, management actions in a room row (edit, deactivate, reactivate, delete) MUST be grouped as a visually separate, secondary-styled group labeled "Verwalten"; they MUST NOT be primary and MUST NOT replace the room's main action. Their behavior and permissions stay unchanged.

### Key Entities

- **Room row**: One room in the list: number, name, day strip with its windows and "Jetzt" marker, one action. Uses existing room and reservation data.
- **Field label map**: Assignment technical field name → user-facing German label, already provided by feature 018; extended only if a form field is missing from it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On 100 % of pages in scope, the first Tab stop is the skip link and activating it places focus in the main content.
- **SC-002**: 100 % of validation errors shown in the forms in scope name the field by its German label; no technical field name remains visible.
- **SC-003**: 100 % of tables in scope have a caption and header scope; 100 % of record lists are real lists.
- **SC-004**: A user can see in the room list, without opening a room, whether each room is free right now; this holds with a screen reader as well as visually.
- **SC-005**: In administration mode at 1280 px, the navigation occupies one line.
- **SC-006**: An automated accessibility check of every page in scope reports no new violations compared to before, and none for missing labels, table headers, or landmark structure.
- **SC-007**: The existing automated test suite passes after adaptation, with no test removed without replacement.

## Assumptions

- **Scope of pages**: all pages with the standard application frame (home, room list, room detail, check-in, map, login, administration pages, role and user pages, location catalog). The room display page (Türschild) and the floor display are excluded; they are converted in a separate feature.
- **Room detail** receives only the cross-cutting changes (skip link, fields, errors, tables/lists, typography, primary buttons); the new layout "Raum auf einen Blick" is Part 2.
- **Locations catalog** receives only cross-cutting changes; master-detail with floor reordering is a separate feature that needs a backend change.
- **Single action per room row**: the action is "open the room" (details/booking). The favorite toggle stays in the row as a state control (clarified 2026-10-14).
- **Strip range**: the range shown is the opening/booking range already used by the room day view; if none is defined, the current calendar day from 07:00 to 20:00 is used.
- **Freshness**: the "Jetzt" marker moves every minute; bookings made by others appear in the strips within 5 minutes or when the tab becomes visible again.
- **Data**: windows for the strips are loaded per room through the existing reservation query, only for rows that are visible (see FR-020); no backend change is made. A combined query for the whole list is a possible later optimization and would not change the interface.
- **Language**: all new labels, captions and hints are German, consistent with the existing translation.
- **Out of scope**: event title, floor order, role restriction for booking, student view, floor display, WCAG review items B1–B4 and C2–C4, pattern-off switch.
- **Dependencies**: building blocks and field-label map from feature 018; design tokens from layer 1.
