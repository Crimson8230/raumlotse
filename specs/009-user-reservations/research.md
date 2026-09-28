# Phase 0 Research: User Reservation Integration

## Technical Context Baseline

- **Language / Runtime**: Java 21 (Backend), TypeScript 5.x / React 19 (Frontend)
- **Frameworks**: Spring Boot 3.4.x (Web MVC, Security, Data JPA, Validation), Vite 6.x, React Router 7.x
- **Database & Storage**: PostgreSQL 17, Flyway migration mechanism
- **Security / Session**: Stateful HTTP session auth, `CurrentAccountFilter`, `AuthenticatedUser` principal, CSRF protection via cookie/header token
- **Testing**: JUnit 5, MockMvc, AssertJ, Spring Security Test, Testcontainers; Vitest, Testing Library React, jsdom

---

## Research Topics & Decisions

### 1. Authentication Context & `createdBy` Identity Attribution

- **Context**: Spec FR-001 & FR-002 mandate that every newly created room reservation is associated with the authenticated user's unique account identifier as `createdBy`, and unauthenticated requests must be rejected.
- **Decision**: In `ReservationController.createReservation(UUID roomId, @Valid @RequestBody ReservationCreateRequest request, Authentication authentication)`, extract `AuthenticatedUser` from `authentication.getPrincipal()`.
  - The backend assigns `user.userId().toString()` to `reservation.setCreatedBy(...)`.
  - Any client-submitted `createdBy` string in `ReservationCreateRequest` is either deprecated/optional or overridden by the server-side authenticated user ID.
  - If `authentication` is null or principal is not `AuthenticatedUser`, reject with HTTP 401 Unauthorized (`AUTH_REQUIRED`).
- **Rationale**: Server-side attribution eliminates identity spoofing and satisfies Constitution Principle IV (Secure by Default).
- **Alternatives Considered**:
  - *Trusting client-sent `createdBy` in request body*: Rejected due to high risk of spoofing and failure to enforce real account identity.
  - *Storing user entity reference (`@ManyToOne UserAccount`)*: Considered, but `Reservation` currently uses string-based `created_by` in database schema (`TEXT NOT NULL`). Storing canonical user ID as `user.userId().toString()` in `created_by` maintains compatibility with historical data while establishing a strict identifier link.

---

### 2. Designated Person (`reservedFor`) Schema & Validation

- **Context**: FR-003, FR-004, FR-005, FR-006, and FR-015 require a required text field `reservedFor` on every reservation, pre-filled with the authenticated user's `displayName` in the booking form, editable during creation and subsequent metadata updates (`RESERVED` status), and validated to be non-blank up to 255 characters.
- **Decision**:
  - Add `reserved_for VARCHAR(255) NOT NULL` to table `reservation` with constraint `CHECK (length(btrim(reserved_for)) BETWEEN 1 AND 255)`.
  - Add `reservedFor` to `Reservation` entity (`@Column(name = "reserved_for", nullable = false)`).
  - Add `@NotBlank @Size(max = 255) String reservedFor` to `ReservationCreateRequest`.
  - Add `@Size(min = 1, max = 255) String reservedFor` to `ReservationUpdateRequest` (optional in update payload, but non-blank if provided).
  - In `ReservationResponse`, include `String reservedFor`.
  - In `ReservationForm.tsx`, remove the old manual `createdBy` text input. Introduce `reservedFor` input pre-populated with `auth.user.displayName`.
  - In `ReservationList.tsx`, display `reservedFor` ("Reserved for: ...") and include `reservedFor` editing in the inline edit form for `RESERVED` bookings.
- **Rationale**: Clean separation between operational actor (`createdBy`) and functional beneficiary (`reservedFor`), fulfilling all acceptance criteria.
- **Alternatives Considered**:
  - *Encoding `reservedFor` in the `note` field*: Rejected because searching, displaying, and validating require a dedicated first-class field.

---

### 3. Personal Reservations Retrieval Endpoint

- **Context**: FR-007, FR-010, and FR-011 require a listing of the user's upcoming reservations on the home page, strictly filtered by `createdBy == currentUser.id`, including upcoming and active reservations (`endTime > now` and `status IN ('RESERVED', 'ACTIVE')`), sorted chronologically ascending by `startTime`, up to 10 entries.
- **Decision**: Introduce `GET /api/reservations/my-upcoming` on `ReservationController`.
  - Requires authenticated session (`Authentication authentication`).
  - Queries `ReservationRepository`:
    ```java
    List<Reservation> findTop10ByCreatedByAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc(
        String createdBy,
        Collection<ReservationStatus> statuses,
        Instant now);
    ```
  - Statuses checked: `[ReservationStatus.RESERVED, ReservationStatus.ACTIVE]`.
  - Uses `clock.instant()` for deterministic time evaluation and testing.
  - Returns `List<ReservationResponse>`.
- **Rationale**:
  - Avoids IDOR (Insecure Direct Object Reference) vulnerabilities since the endpoint does not accept a user ID parameter.
  - Efficiently executes a single bounded index query (`LIMIT 10`).
- **Alternatives Considered**:
  - *`GET /api/reservations?createdBy={id}`*: Rejected as it exposes user identifiers in query parameters and requires extra authorization logic to prevent viewing other users' private bookings.
  - *Client-side filtering of all room reservations*: Rejected as slow, unscalable, and a severe data privacy violation.

---

### 4. Home Page Presentation & Visitor Experience

- **Context**: FR-007, FR-008, FR-009, FR-012, FR-013, and SC-001/006 define the home page UX:
  - Unauthenticated visitors: section completely hidden.
  - Authenticated users: "My Upcoming Reservations" / "Meine nächsten Reservierungen" table with Reservation Time, Room (clickable link to `/rooms/:id`), and Duration.
  - Empty state when no reservations exist.
- **Decision**:
  - Create `MyUpcomingReservations` component in `frontend/src/components/MyUpcomingReservations/`.
  - Integrated into `HomePage.tsx`.
  - Evaluates `const { state, user } = useAuth()`.
  - If `state !== 'authenticated'`, render nothing for this section.
  - If `state === 'authenticated'`, fetch `getMyUpcomingReservations()`.
  - Duration formatter utility: computes difference between `startTime` and `endTime` in minutes, formatting as `X min` / `X Min.` (under 60 min) or `X hr Y min` / `X Std. Y Min.` (60 min or above).
- **Rationale**: Minimal DOM footprint, zero unauthenticated data requests, clean separation of concerns.

---

### 5. Database Schema & Migration Strategy

- **Context**: Existing reservations table has `created_by TEXT NOT NULL` from feature 004.
- **Decision**: Flyway migration `V10__add_reserved_for_and_user_reservation_index.sql`:
  ```sql
  ALTER TABLE reservation ADD COLUMN reserved_for VARCHAR(255);
  UPDATE reservation SET reserved_for = created_by WHERE reserved_for IS NULL;
  ALTER TABLE reservation ALTER COLUMN reserved_for SET NOT NULL;
  ALTER TABLE reservation ADD CONSTRAINT ck_reservation_reserved_for_nonempty
      CHECK (length(btrim(reserved_for)) BETWEEN 1 AND 255);

  CREATE INDEX ix_reservation_user_upcoming
      ON reservation (created_by, status, end_time, start_time);
  ```
- **Rationale**: Non-breaking backfill ensures existing database rows pass schema validation; the composite index guarantees sub-second query performance well within SC-001 (≤ 1.5s).
