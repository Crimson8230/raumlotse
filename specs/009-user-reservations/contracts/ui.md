# UI Contract: User Reservation Integration

**Feature**: `009-user-reservations` | **Date**: 2026-09-28

Defines observable frontend contracts, presentation behavior, and interactions across the application.

---

## 1. Home Page Overview: "Meine nächsten Reservierungen" (`/`)

### Visibility Condition
- Rendered if and only if `useAuth().state === 'authenticated'`.
- If unauthenticated (`anonymous`, `loading`, `unavailable`), the section is completely omitted from the rendered DOM (FR-013, SC-006).

### Component: `MyUpcomingReservations`

Mounted on `HomePage.tsx` above or alongside general portal content.

#### Layout & Table Structure

| Column Header (German UI) | Content / Format | Interaction / Target |
|---------------------------|------------------|----------------------|
| **Zeitpunkt** | Date and time range formatted with locale: e.g. `28.09.2026, 10:00 – 11:30` | Static text |
| **Raum** | Room display name | Link navigating to `/rooms/:roomId` (FR-009) |
| **Dauer** | Human-readable duration computed from start and end instants: e.g. `45 Min.` (< 60m) or `1 Std. 30 Min.` (≥ 60m) | Static text |

#### Presentation Rules
- **Ordering**: Ascending by `startTime` (soonest upcoming reservation first).
- **Capped Count**: Up to 10 entries (FR-011).
- **Empty State**: If the user has 0 upcoming reservations, render an accessible status message:
  `Keine anstehenden Reservierungen vorhanden.` (FR-012).
- **Loading State**: Standard `status-loading` styling while fetching.
- **Error State**: Standard `feedback-error` styling if retrieval fails.

---

## 2. Room Reservation Form: `ReservationForm` (`/rooms/:id`)

### Authentication Requirement
- Unauthenticated users cannot submit bookings (FR-002). If unauthenticated, the form displays an authentication prompt:
  `Bitte melden Sie sich an, um eine Reservierung vorzunehmen.` with a link to `/login`.

### Field: "Reserviert für" (`reservedFor`)
- **Default Value**: Pre-filled automatically with the authenticated user's `displayName` (`auth.user.displayName`) (FR-004).
- **Interaction**: The user may freely edit or replace this text with another person, project team, or purpose (FR-005).
- **Validation**:
  - Required / non-blank: Trimmed length must be between 1 and 255 characters (FR-006).
  - Inline error if empty: `"Reserviert für ist ein Pflichtfeld."` / `"Reserved for is required."`
- **Replaces**: The legacy manual "Booked By" (`createdBy`) input field is removed from the form; the server automatically attributes `createdBy` to the session's authenticated user ID (FR-001).

---

## 3. Reservation List & Details: `ReservationList` (`/rooms/:id`)

### Display of Designated Recipient & Creator
- **Card View**:
  - Displays `Reserviert für: {reservation.reservedFor}` prominently on the reservation card (FR-014).
  - Displays `Erstellt von: {reservation.createdBy}` within audit/metadata details.

### Inline Editing (`RESERVED` Status)
- In addition to attendee count and notes, the inline edit form exposes:
  - Input: `Reserviert für` (`reservedFor`) initialized to `reservation.reservedFor`.
  - Validation: Non-blank, max 255 characters.
  - Submits to `PATCH /api/reservations/{id}` with `reservedFor` included in the update payload (FR-015).
