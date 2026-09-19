# Implementation Plan: Room Reservations

**Branch**: `004-room-reservations` | **Date**: 2026-09-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/004-room-reservations/spec.md`

## Summary

Implement end-to-end room reservation capabilities in Raumlotse. From the room view, users can book an active room for a given time window and duration. The system guarantees conflict-free bookings via serialized overlap detection, enforces seating arrangement capacity limits, allows requesting additional equipment not permanently present in the room, records notes, and stores administrative audit data (`createdBy`, `createdAt`). Stored persistent states (`RESERVED`, `CANCELLED`) combined with dynamic runtime evaluation provide temporal lifecycle states (`RESERVED`, `ACTIVE`, `EXPIRED`, `CANCELLED`).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript 5.7+ / Node.js 20+ (frontend)

**Primary Dependencies**:
- Backend: Spring Boot 3.4.x (Spring Web MVC, Spring Data JPA, Bean Validation), Flyway, PostgreSQL JDBC Driver
- Frontend: React 19, Vite, React Router 7, Vitest, React Testing Library

**Storage**: PostgreSQL 17 accessed via Spring Data JPA and Hibernate; schema versioned with Flyway migration `V4__create_reservation_tables.sql`

**Testing**:
- Backend: JUnit 5, AssertJ, Mockito, MockMvc (`@WebMvcTest`), Spring Boot integration tests with Testcontainers (`@SpringBootTest`, `postgres:17-alpine`)
- Frontend: Vitest, React Testing Library, mock service handlers

**Target Platform**: Linux / Docker Compose deployment, modern desktop and mobile browsers

**Project Type**: Full-stack web application (REST API service backend + Single Page Application frontend)

**Performance Goals**:
- Reservation creation with conflict check: response in < 200ms
- Room reservation schedule retrieval: response in < 100ms
- Cancellation and slot release: effective in < 1 second

**Constraints**:
- Half-open interval conflict check `[start, end)` (adjacent back-to-back bookings allowed)
- Concurrency control via pessimistic write lock on target `Room` record during reservation creation
- YAGNI runtime calculation of `ACTIVE` / `EXPIRED` states without background cron daemons
- Room deletion blocked when reservation history exists (via `RoomDependentHistoryChecker`)
- Room deactivation blocked when upcoming `RESERVED` or `ACTIVE` bookings exist

**Scale/Scope**: University campus facilities (hundreds of rooms, thousands of reservations per semester)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Validation in this Feature Design |
|---|---|---|
| **I. Test-First Development (NON-NEGOTIABLE)** | PASS | All backend endpoints/services and frontend components start with red unit/integration tests before green code. |
| **II. Modern, Typed, and Consistent Codebases** | PASS | Java 21 records, constructor injection, layered controller → service → repository; strict TypeScript types and functional React components with ESLint compliance. |
| **III. Contract-First API Design** | PASS | REST endpoints, request/response models, and status codes formalized in `contracts/reservations-api.yaml` prior to implementation. |
| **IV. Secure & Data-Respecting by Default** | PASS | Parameterized JPA queries, Bean Validation annotations on inputs, form validation, no sensitive personal data or secrets logged. |
| **V. Simplicity & Observability** | PASS | Dynamic lifecycle status evaluation avoids background scheduler overhead; standardized Problem JSON error payloads; clear log messages. |
| **Tech Stack Constraints** | PASS | Java 21, Spring Boot, PostgreSQL 17, React, TypeScript, Docker. No unapproved dependencies introduced. |

## Project Structure

### Documentation (this feature)

```text
specs/004-room-reservations/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: Concurrency, lifecycle, equipment resolution
├── data-model.md        # Phase 1 output: ER diagram, entities, schema migration
├── quickstart.md        # Phase 1 output: Automated and manual verification guide
├── contracts/           # Phase 1 output: OpenAPI REST specification
│   └── reservations-api.yaml
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/at/mci/igp/raumlotse/
│   │   │   ├── controller/
│   │   │   │   └── ReservationController.java
│   │   │   ├── domain/
│   │   │   │   ├── PersistentReservationStatus.java
│   │   │   │   ├── Reservation.java
│   │   │   │   └── ReservationStatus.java
│   │   │   ├── dto/
│   │   │   │   ├── ReservationCreateRequest.java
│   │   │   │   ├── ReservationResponse.java
│   │   │   │   └── ReservationUpdateRequest.java
│   │   │   ├── repository/
│   │   │   │   └── ReservationRepository.java
│   │   │   └── service/
│   │   │       ├── ReservationRoomHistoryChecker.java
│   │   │       └── ReservationService.java
│   │   └── resources/
│   │       └── db/migration/
│   │           └── V4__create_reservation_tables.sql
│   └── test/
│       └── java/at/mci/igp/raumlotse/
│           ├── ReservationCreationIntegrationTest.java
│           ├── controller/
│           │   └── ReservationControllerTest.java
│           └── service/
│               └── ReservationServiceTest.java
frontend/
├── src/
│   ├── API/
│   │   └── reservations.ts
│   ├── components/
│   │   ├── ReservationForm/
│   │   │   ├── ReservationForm.css
│   │   │   ├── ReservationForm.test.tsx
│   │   │   └── ReservationForm.tsx
│   │   └── ReservationList/
│   │       ├── ReservationList.css
│   │       ├── ReservationList.test.tsx
│   │       └── ReservationList.tsx
│   ├── pages/
│   │   ├── RoomDetailPage.css
│   │   ├── RoomDetailPage.test.tsx
│   │   └── RoomDetailPage.tsx
│   └── types/
│       └── reservation.ts
```

**Structure Decision**: Standard web application with distinct `backend/` and `frontend/` services, extending existing package and directory layouts.

## Complexity Tracking

*No violations. All design patterns align with the constitution.*
