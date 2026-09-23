# Implementation Plan: Expire Unclaimed Room Reservations

**Branch**: `007-expire-unclaimed-reservations` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-expire-unclaimed-reservations/spec.md`

## Summary

Automatically transition unattended room reservations in `RESERVED` status to `EXPIRED` once 5 minutes have elapsed from their scheduled `startTime` without a check-in (activation). The technical approach leverages Spring Boot's built-in `@Scheduled` periodic runner (every 30 seconds) executing within `ReservationExpirationScheduler`, calling `ReservationService.expireUnattendedReservations()`. A derived JPA repository query efficiently fetches overdue `RESERVED` bookings, transitions them to `EXPIRED`, updates modification timestamps, and emits structured audit logs. `Clock` injection guarantees deterministic unit testing of boundary conditions (TDD), and existing optimistic locking (`@Version`) resolves any concurrent check-in races.

## Technical Context

**Language/Version**: Java 21 (Backend), TypeScript 5.x / React 19 (Frontend)

**Primary Dependencies**: Spring Boot 3.4, Spring Data JPA, Hibernate, PostgreSQL JDBC Driver, Flyway

**Storage**: PostgreSQL 17 (relational database; existing schema and check constraints reused)

**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot Test / Testcontainers

**Target Platform**: Linux container runtime / Docker Compose deployment

**Project Type**: Full-stack web application (backend service & scheduling layer)

**Performance Goals**: Expiration sweep execution < 500ms; status updates reflected in room queries within 60s of deadline (SC-001)

**Constraints**: Test-First Development (TDD) mandatory per Constitution Principle I; No additional libraries or external brokers (Principle V: YAGNI)

**Scale/Scope**: Campus-wide rooms and reservations across university facilities

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitution Principle | Status | Evaluation & Evidence |
|---|---|---|
| **I. Test-First Development** | **PASS** | TDD mandatory: failing unit tests (`ReservationServiceTest`, `ReservationExpirationSchedulerTest`) and integration tests (`ReservationExpirationIntegrationTest`) must be committed and verified failing before implementation code is written. |
| **II. Modern, Typed Codebases** | **PASS** | Java 21 and Spring Boot idioms strictly followed (constructor injection, typed enums `ReservationStatus.EXPIRED`, clean layered architecture). |
| **III. Contract-First API Design**| **PASS** | Interface behavior and operational endpoint defined in [expiration-api.yaml](contracts/expiration-api.yaml). |
| **IV. Secure & Data-Respecting** | **PASS** | Parameterized Spring Data JPA query (`findByStatusAndStartTimeLessThanEqual`). Logs contain room and reservation UUIDs, timestamps, and counts without exposing PII. |
| **V. Simplicity & Observability** | **PASS** | Uses built-in Spring `@Scheduled` runner. No extra queuing dependencies (Quartz, Celery). Structured SLF4J log output on transitions. |

## Project Structure

### Documentation (this feature)

```text
specs/007-expire-unclaimed-reservations/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan output)
├── data-model.md        # Phase 1 output (/speckit-plan output)
├── quickstart.md        # Phase 1 output (/speckit-plan output)
├── contracts/           # Phase 1 output (/speckit-plan output)
│   └── expiration-api.yaml
└── checklists/
    └── requirements.md
```

### Source Code Layout

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/at/mci/igp/raumlotse/
│   │   │   ├── config/
│   │   │   │   └── ClockConfig.java                  # Provides java.time.Clock bean
│   │   │   ├── domain/
│   │   │   │   └── ReservationStatus.java            # Existing: RESERVED, ACTIVE, COMPLETED, EXPIRED, CANCELLED
│   │   │   ├── repository/
│   │   │   │   └── ReservationRepository.java        # Add findByStatusAndStartTimeLessThanEqual
│   │   │   ├── service/
│   │   │   │   ├── ReservationService.java           # Add expireUnattendedReservations()
│   │   │   │   └── ReservationExpirationScheduler.java # Add @Scheduled(fixedDelay = 30000)
│   │   │   └── controller/
│   │   │       └── ReservationController.java        # Expose POST /api/reservations/expire-unattended
│   │   └── resources/
│   │       └── application.yaml                      # Existing configuration
│   └── test/
│       └── java/at/mci/igp/raumlotse/
│           ├── service/
│           │   ├── ReservationServiceTest.java       # Unit tests for expiration logic & boundary timing
│           │   └── ReservationExpirationSchedulerTest.java # Unit test for scheduler delegation
│           └── ReservationExpirationIntegrationTest.java # End-to-end integration test with DB
```

**Structure Decision**: Standard Spring Boot multi-tier backend architecture within the existing `backend/` module. No frontend code changes are required because the frontend already renders `EXPIRED` status badges and disables controls for terminal reservation states.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations. All design choices conform to the constitution.*
