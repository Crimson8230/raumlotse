# Feature Specification: Shared UI Building Blocks

**Feature Branch**: `018-shared-ui-building-blocks`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Shared UI building blocks for the 'Papier & Tinte' design (layer 2 of docs/DESIGN-UMSTELLUNG.md): a set of reusable, accessible components — time-window strip with 'Jetzt' marker, chip, form field, error message, skip link, data list, data table and wayfinding element — each tested first, using design tokens only, meeting the contrast and target-size rules of the design, and expressing states as pattern plus word. Migrating the pages to use them is a separate feature."

## Context

The design "Papier & Tinte" (see `docs/DESIGN-UMSTELLUNG.md`) is applied in the frontend only as global styles: tokens, fonts and a few CSS classes (`.skip-link`, `.btn-primary`, `.strip`) exist, but no page uses them as real building blocks. Each page therefore builds its own lists, tables, fields and error displays, and accessibility measures (labels, captions, status wording) are repeated or missing.

This feature delivers the shared building blocks as a self-contained set. It deliberately does **not** change any existing page; adopting the blocks page by page is the follow-up work ("layer 3"). The audience of the blocks is the people building pages (developers) and, through them, everyone who uses Raumlotse, including keyboard and screen-reader users.

## Clarifications

### Session 2026-10-14

- Q: Should the skip link be wired into the shared page frame now, or delivered only as a standalone block? → A: Standalone block only, plus a "main content" target contract; wiring it into the page frame happens in the page-migration follow-up.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Understand availability at a glance (Priority: P1)

As a person looking at a room, I want the day's occupancy shown as one strip with a clear "Jetzt" marker and the same wording everywhere, so that I can see when a room is free or taken without reading a table, and a screen reader tells me the same.

**Why this priority**: The strip is the central visual element of the design and carries the main information of Raumlotse (is this room free now and when?). All room lists and displays depend on it.

**Independent Test**: Render the strip for a day with two bookings and the current time inside one of them; verify the visible pattern, the "Jetzt" marker position, and that the accessible name lists every window and the current state in words.

**Acceptance Scenarios**:

1. **Given** a day with free and booked windows, **When** the strip is shown, **Then** free time is shown empty, booked time hatched, and each state is also available as a word (not by pattern or color alone).
2. **Given** the current time lies within the strip's range, **When** the strip is shown, **Then** a "Jetzt" marker sits at the matching position and is announced as part of the accessible name.
3. **Given** a day with no bookings, **When** the strip is shown, **Then** it reads as "frei" for the whole range.
4. **Given** the current time is outside the strip's range, **When** the strip is shown, **Then** no "Jetzt" marker is displayed.
5. **Given** a window is the user's own, blocked, or otherwise special, **When** the strip is shown, **Then** it uses the design's distinct pattern and word for that state (own = full ink, blocked = dotted).

---

### User Story 2 - Fill in forms and recover from errors (Priority: P1)

As a person filling in a form, I want every field to have a visible label, a hint where needed, a clear required marker, and errors that tell me which field and what is wrong in plain German, so that I can correct mistakes without guessing.

**Why this priority**: Booking and administration are form-driven; inconsistent labels and error messages are the most frequent accessibility and usability gap listed in the design review.

**Independent Test**: Render a field with label, hint, required marker and an error; verify the label is bound to the input, the hint and error are announced with the input, and the error shows symbol, the word "Fehler:" and the text.

**Acceptance Scenarios**:

1. **Given** a field with a label, **When** it is shown, **Then** the label is visible and clicking it focuses the input.
2. **Given** a required field, **When** it is shown, **Then** the required state is marked visibly and in words, not by an asterisk alone.
3. **Given** a field with a hint, **When** a screen reader focuses the input, **Then** the hint is read together with the label.
4. **Given** a field with an error, **When** it is shown, **Then** the error appears as symbol + "Fehler:" + text, is connected to the input, and the input is marked invalid.
5. **Given** a form-wide error without a specific field, **When** it is shown, **Then** it uses the same error pattern and is announced to assistive technology when it appears.
6. **Given** the server reports a technical field name, **When** the error is displayed, **Then** the user-facing label is shown instead of the technical name.

---

### User Story 3 - Skip to the content (Priority: P2)

As a keyboard or screen-reader user, I want a "Zum Inhalt springen" link as the first focusable element on every page that uses the page frame (this feature delivers the block; wiring it into existing pages is follow-up work), so that I do not have to tab through the whole navigation on each page.

**Why this priority**: Required by WCAG 2.2 (bypass blocks) and small to deliver; its style already exists but it is not wired anywhere.

**Independent Test**: Render a test page frame (composed in the test, not an existing page) with the skip link, press Tab once, verify the link becomes visible and activating it moves focus to the main content.

**Acceptance Scenarios**:

1. **Given** a page has loaded, **When** the user presses Tab for the first time, **Then** the skip link receives focus and becomes visible.
2. **Given** the skip link has focus, **When** the user activates it, **Then** keyboard focus moves to the main content area.
3. **Given** the skip link is not focused, **When** the page is shown, **Then** it is not visible but remains available to assistive technology.

---

### User Story 4 - Lists and tables that assistive technology can read (Priority: P2)

As a screen-reader user, I want lists to be real lists and tables to have a caption and proper header associations, so that I can navigate by structure and know how many items there are and what each column means.

**Why this priority**: Rooms, reservations, buildings and floors are all presented as lists or tables; structure is the basis for efficient navigation and for the later page migration.

**Independent Test**: Render a data list and a data table with sample rows; verify list semantics (item count), table caption, column and row header associations, and an empty state in words.

**Acceptance Scenarios**:

1. **Given** a data list with items, **When** it is read by a screen reader, **Then** it is announced as a list with the correct item count.
2. **Given** a data table, **When** it is read by a screen reader, **Then** it has a caption, column headers are associated with their cells, and a row header can be designated.
3. **Given** a list or table without entries, **When** it is shown, **Then** it shows a worded empty state instead of an empty box.
4. **Given** a narrow viewport (phone), **When** a table is shown, **Then** its content remains readable without the page scrolling horizontally.

---

### User Story 5 - Status chips and wayfinding (Priority: P3)

As a person using the app, I want status labels (chips) and directional hints (Wegweiser) that look and read the same everywhere, so that "belegt", "meine" or "Etage 2 → Aufzug" mean the same thing on every page.

**Why this priority**: These are the smaller vocabulary elements of the design. They are valuable once lists and strips exist and are needed by later features (floor display, distances), but nothing else in this feature depends on them.

**Independent Test**: Render each chip variant and a wayfinding element; verify word, pattern and accessible text, and that chip variants are distinguishable without color.

**Acceptance Scenarios**:

1. **Given** a chip for any supported state, **When** it is shown, **Then** it always contains a word, and states differ by pattern or form as well as by tone.
2. **Given** a wayfinding element with a direction and a target, **When** it is shown, **Then** direction and target are available as text, and the arrow or symbol is not the only carrier of the meaning.
3. **Given** a chip or wayfinding element is interactive, **When** it is focused, **Then** it shows a visible focus indicator and has a target size of at least 44 px.

---

### Edge Cases

- A strip with overlapping or adjacent windows, a window ending exactly at the range end, or a zero-length window must still render correctly and not break the accessible name.
- A strip with a very large number of windows must keep a usable accessible name (not unbounded) and a readable visual.
- Long room names, labels or table cell content must wrap or truncate without breaking the layout and without losing the full text for assistive technology.
- A field with several simultaneous messages (hint and error) must announce both, with the error first.
- Right-to-left or non-German text is out of scope; German is the only display language.
- User preference "reduce motion" and forced-colors (high-contrast) mode must not make any state indistinguishable.
- Using a block without an optional piece (e.g. a field without hint, a chip without icon) must not leave empty gaps or broken references.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide the following reusable building blocks as one shared set that any page can adopt: time-window strip (with status dot), chip, form field, error message, skip link, data list, data table and wayfinding element.
- **FR-002**: The strip MUST show free, booked, own, blocked and "now" states using the design's patterns and always accompany each state with a word.
- **FR-003**: The strip MUST expose an accessible name listing all windows in time order and the "Jetzt" state in German.
- **FR-004**: The form field MUST provide a visible label, optional hint, an explicit required marker with wording, and an error slot; label, hint and error MUST be programmatically associated with the input.
- **FR-005**: The error message MUST show a symbol, the word "Fehler:" and the message text, and MUST be announced to assistive technology when it appears.
- **FR-006**: Validation errors MUST be displayable with user-facing field labels instead of technical field names through a single mapping that pages can extend.
- **FR-007**: The skip link block MUST be designed to be the first focusable element of a page, be visible only on focus, and move focus to a main content target when activated. The block MUST document the target contract (the main content element it points to). Wiring the block into the existing page frame is out of scope for this feature.
- **FR-008**: The data list MUST render as a real list and the data table MUST provide a caption, column headers with scope and an optional row header; both MUST support a worded empty state.
- **FR-009**: The chip MUST always contain a word and MUST distinguish its states by more than color.
- **FR-010**: The wayfinding element MUST present direction and target as text in addition to any symbol.
- **FR-011**: All blocks MUST take their colors, type and spacing exclusively from the shared design tokens; no block MAY contain fixed color values.
- **FR-012**: All text in the blocks MUST meet a contrast ratio of at least 7:1 against its background, and interactive elements MUST have a target size of at least 44 px and a visible focus indicator.
- **FR-013**: Names of rooms MUST be shown in the design's serif face and times and numbers in the monospaced face wherever a block shows them.
- **FR-014**: Each block MUST be usable on its own, without the others, and MUST be documented with at least one usage example that is also exercised by a test.
- **FR-015**: Adding the building blocks MUST NOT change the behavior or appearance of any existing page.
- **FR-016**: Each block MUST have automated tests written before the implementation, covering its states, accessible names and keyboard behavior.
- **FR-017**: Every block MUST remain usable at a phone viewport width and with text enlarged to 200 % without loss of content or horizontal page scrolling.

### Key Entities

- **Time window**: A period within a day with a state (free, booked, own, blocked), shown by the strip. Has start, end and an optional label.
- **State vocabulary**: The shared set of states (frei, belegt, meine, jetzt, gesperrt) with their pattern, word and tone; used consistently by strip, chip and later pages. A chip may additionally use a plain "neutral" tone for labels that are not an occupancy state (e.g., "Spezial").
- **Field message**: A hint or error attached to a form field, with a kind (hint, error) and text.
- **Wayfinding hint**: A direction plus a target (e.g., floor, elevator, room) with an optional distance or walking time.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every state shown by a block can be identified without color: in a grayscale review of all block variants, 100 % of states remain distinguishable by pattern or word.
- **SC-002**: A screen-reader user can learn the full day's availability of a room from the strip's accessible name alone, without visiting individual elements.
- **SC-003**: All text in the blocks reaches a contrast ratio of at least 7:1, and all interactive elements are at least 44 px in each dimension; verified for 100 % of block variants.
- **SC-004**: All blocks can be operated using the keyboard only, and focus is visible at every step.
- **SC-005**: No page of the existing application changes visually or behaviorally after this feature is merged (existing frontend test suite passes unchanged).
- **SC-006**: A new page can be assembled using the blocks without adding any custom CSS for fields, errors, lists, tables, chips or strips; verified by composing one reference page in the tests.
- **SC-007**: Each block has automated tests that failed before the block existed and pass afterwards, with every acceptance scenario above covered by at least one test.

## Assumptions

- The tokens, fonts and global styles from layer 1 of the design migration are available and stable; this feature consumes them but does not change their values.
- German is the only display language; labels and wording are in German.
- The existing `.skip-link`, `.btn-primary` and `.strip` CSS may be reused or replaced by the blocks, as long as no existing page changes.
- Migrating pages to the blocks (including wiring the skip link into the shared page frame), event titles, the sort order of floors and any new backend functionality are out of scope and covered by later features.
- The Wayfinding element only presents information it is given; computing distances or directions is out of scope.
- The Administration mode's inverted bar and the page layout/navigation are not part of these blocks.
- Visual review in the browser and automated tests are both available to verify the blocks; a dedicated showcase page is not required.
