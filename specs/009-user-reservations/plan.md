# Implementation Plan: User Reservation Integration

**Branch**: `009-user-reservations` | **Date**: 2026-09-28 | **Spec**: [spec.md](file:///home/simon/Progg/MCI/sem_5/raumlotse/specs/009-user-reservations/spec.md)

**Input**: Feature specification from `/specs/009-user-reservations/spec.md`

## Summary

Integrate room reservation creation and tracking with authenticated user management. The system captures the authenticated user's unique account ID as the immutable `createdBy` attribute while capturing a mandatory, editable `reservedFor` field pre-filled by default with the user's display name. A new endpoint (`GET /api/reservations/my-upcoming`) serves up to 10 upcoming and active reservations belonging to the authenticated user. On the frontend, authenticated users see a dedicated "My Upcoming Reservations" / "Meine nächsten Reservierungen" overview table on the home page with direct navigation links to room details, while unauthenticated visitors see no personalized sections.

## Technical Context

**Language/Version**: Java 21 (Backend), TypeScript 5.x / React 19 (Frontend)

**Primary Dependencies**:
- Backend: Spring Boot 3.4.x (Web MVC, Security, Data JPA, Validation), Flyway, PostgreSQL JDBC Driver, Jackson
- Frontend: React 19, Vite 6.x, React Router 7.x, Lucide React

**Storage**: PostgreSQL 17 via Flyway migration `V10__add_reserved_for_and_user_reservation_index.sql`

**Testing**:
- Backend: JUnit 5, MockMvc, AssertJ, Spring Security Test, Testcontainers
- Frontend: Vitest, React Testing Library, jsdom

**Target Platform**: Linux server container (Docker / Docker Compose), evergreen web browsers

**Project Type**: Full-stack web application (REST API backend + React SPA frontend)

**Performance Goals**:
- Dashboard reservations table loads in ≤ 1.5 seconds (SC-001)
- Query backed by composite index `(created_by, status, end_time, start_time)`

**Constraints**:
- 0% cross-user reservation leakage (SC-005)
- Server-side identity attribution (reject unauthenticated booking with 401 `AUTH_REQUIRED`)
- `reservedFor` trimmed length between 1 and 255 characters
- Maximum 10 upcoming reservations rendered on dashboard

**Scale/Scope**: Single table migration, 2 modified API endpoints, 1 new API endpoint, 1 new frontend component, updates to 3 existing components/pages

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I: Test-First Development (NON-NEGOTIABLE)**: PASS. All tasks will enforce Red-Green-Refactor. Unit and integration tests must precede production code for both backend endpoints and frontend components.
- **Principle II: Modern, Typed, and Consistent Codebases**: PASS. Backend uses Java 21, constructor injection, layered controller → service → repository structure, and Bean Validation. Frontend uses strict TypeScript, functional components, and ESLint compliance.
- **Principle III: Contract-First API Design**: PASS. OpenAPI schema (`contracts/api.yaml`) and UI behavior specification (`contracts/ui.md`) created before implementation.
- **Principle IV: Secure and Data-Respecting by Default**: PASS. User identity is derived strictly from Spring Security's authenticated session principal (`AuthenticatedUser`), preventing client spoofing. Parameterized queries via Spring Data JPA. No sensitive personal data logged.
- **Principle V: Simplicity and Observability**: PASS. Uses standard Spring Data repository method with index-backed query (YAGNI). Zero unneeded external dependencies.

## Project Structure

### Documentation (this feature)

```text
specs/009-user-reservations/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: authentication, data attribution, query design
├── data-model.md        # Phase 1 output: schema changes, entity relationships, validation
├── quickstart.md        # Phase 1 output: end-to-end validation scenarios
├── contracts/           # Phase 1 output: API and UI interface specifications
│   ├── api.yaml
│   └── ui.md
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/at/mci/igp/raumlotse/
│   ├── controller/
│   │   └── ReservationController.java       # Updated with /my-upcoming & auth-enforced create
│   ├── domain/
│   │   └── Reservation.java                 # Added reservedFor field & column mapping
│   ├── dto/
│   │   ├── ReservationCreateRequest.java    # Added reservedFor validation
│   │   ├── ReservationUpdateRequest.java    # Added reservedFor validation
│   │   └── ReservationResponse.java         # Added reservedFor property
│   ├── repository/
│   │   └── ReservationRepository.java       # Added query for user's upcoming reservations
│   └── service/
│       └── ReservationService.java          # Business logic for creator attribution & upcoming list
├── src/main/resources/db/migration/
│   └── V10__add_reserved_for_and_user_reservation_index.sql
└── src/test/java/at/mci/igp/raumlotse/
    ├── controller/
    │   └── ReservationControllerTest.java   # MockMvc tests for /my-upcoming & security
    └── service/
        └── ReservationServiceTest.java      # Service unit tests

frontend/
├── src/
│   ├── API/
│   │   └── reservations.ts                  # Added getMyUpcomingReservations API client call
│   ├── components/
│   │   ├── MyUpcomingReservations/          # New component for dashboard table
│   │   │   ├── MyUpcomingReservations.tsx
│   │   │   ├── MyUpcomingReservations.css
│   │   │   └── MyUpcomingReservations.test.tsx
│   │   ├── ReservationForm/                 # Pre-fill reservedFor from useAuth(), remove manual createdBy
│   │   │   ├── ReservationForm.tsx
│   │   │   └── ReservationForm.test.tsx
│   │   └── ReservationList/                 # Display and edit reservedFor on reservation cards
│   │       ├── ReservationList.tsx
│   │       └── ReservationList.test.tsx
│   ├── pages/
│   │   ├── HomePage.tsx                     # Integrate MyUpcomingReservations for authenticated users
│   │   └── HomePage.test.tsx
│   ├── types/
│   │   └── reservation.ts                   # Updated types with reservedFor
│   └── utils/
│       ├── date.ts                          # Added formatDuration helper (e.g., "45 Min.", "1 Std. 30 Min.")
│       └── date.test.ts
```

**Structure Decision**: Standard web application structure separating `backend/` (Spring Boot layered MVC) and `frontend/` (React SPA modular components). Real paths recorded above.

## Complexity Tracking

*No violations identified. Design adheres strictly to the Raumlotse Constitution.*
