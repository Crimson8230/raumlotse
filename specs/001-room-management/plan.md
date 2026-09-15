# Implementation Plan: Room Management

**Branch**: `001-room-management` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-room-management/spec.md`

## Summary

Add the ability for an administrator to create, view, update, deactivate/reactivate, and
delete meeting rooms, each with a name, a building/floor selected from managed catalogs
(rather than free text), one or more seating arrangements (name + max capacity), and zero or
more equipment types drawn from an administrator-managed equipment catalog. Buildings and
Floors are themselves managed catalogs — mirroring the equipment-type pattern — with Floor
scoped to exactly one Building. This is the foundational data-management feature the rest of
the booking system builds on; no authentication/authorization or booking logic is included
yet. The approach follows the existing Spring Boot (backend) / React + TypeScript (frontend)
stack, adding a persistence layer (Spring Data JPA + Flyway + PostgreSQL) that does not exist
yet, a REST API under `/api/rooms`, `/api/buildings`, `/api/floors`, and
`/api/equipment-types`, and corresponding React pages.

## Technical Context

**Language/Version**: Java 21 (Spring Boot 4.1.1) for the backend; TypeScript (strict mode) with React 19 on Vite 8 for the frontend.

**Primary Dependencies**: Backend — `spring-boot-starter-webmvc`, `spring-boot-starter-validation` (present); `spring-boot-starter-data-jpa`, `org.postgresql:postgresql`, `flyway-core` + `flyway-database-postgresql` (to be added — not yet in `pom.xml`). Frontend — `react-router-dom` (present); no new runtime dependency required for the UI itself.

**Storage**: PostgreSQL 17 (already provisioned via `docker-compose.yml`), accessed through Spring Data JPA repositories; schema managed via Flyway SQL migrations.

**Testing**: Backend — JUnit 5 + `@WebMvcTest`/MockMvc for controllers (starter already present), plain JUnit + Mockito for services, Testcontainers (PostgreSQL) for repository/integration tests (to be added). Frontend — Vitest + React Testing Library (to be added; no frontend test tooling exists yet).

**Target Platform**: Linux containers (Docker Compose) for the backend/DB; evergreen browsers for the frontend.

**Project Type**: Web application (existing `backend/` + `frontend/` split).

**Performance Goals**: Standard interactive admin-tool expectations — room list and detail views respond in well under 1s for the realistic scale below; no high-throughput requirement.

**Constraints**: Must reuse the existing Docker Compose Postgres instance and existing `/api` base path convention (see `HealthController`); no new infrastructure services.

**Scale/Scope**: Small internal admin tool — realistically tens to low hundreds of rooms and a handful of equipment types; negligible concurrent-write volume (optimistic locking per FR-017 is a correctness safeguard, not a throughput concern).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Test-First Development (NON-NEGOTIABLE) | PASS — `tasks.md` (next command) will sequence a failing test before each implementation task, for both backend (JUnit/MockMvc/Testcontainers) and frontend (Vitest/RTL). |
| II. Modern, Typed, Consistent Codebases | PASS — backend follows Spring Boot layering (`controller → service → repository`) with constructor injection and Bean Validation; frontend stays TypeScript-strict, functional components, and must pass the existing ESLint config. |
| III. Contract-First API Design | PASS — `contracts/openapi.yaml` (Phase 1) defines every endpoint, request/response shape, and status code before implementation; the frontend API layer consumes only these documented shapes. |
| IV. Secure and Data-Respecting by Default | PASS — all inputs validated with Bean Validation (backend) and mirrored client-side checks (frontend); all persistence goes through Spring Data JPA (parameterized queries only); no secrets introduced (reuses existing Compose env vars). |
| V. Simplicity and Observability | PASS — no new services/infrastructure; CRUD kept straightforward (no speculative abstractions beyond the entities the spec requires); errors return structured JSON problem responses and are logged via Spring's standard logging so `docker compose logs` stays useful. |

No violations identified. Complexity Tracking table is not needed.

**Post-Phase 1 re-check**: Design artifacts (`data-model.md`, `contracts/openapi.yaml`,
`quickstart.md`) introduce no new services, dependencies, or abstractions beyond what's
listed in Technical Context/Research — table above still holds, no re-scoring needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-room-management/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── openapi.yaml       # Phase 1 output (/speckit-plan command)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/at/mci/igp/raumlotse/
│   ├── config/
│   │   └── FlywayConfig.java             # manual Flyway wiring — Spring Boot 4.1.1 has no FlywayAutoConfiguration (research.md §1)
│   ├── controller/
│   │   ├── HealthController.java        # existing
│   │   ├── RoomController.java           # new
│   │   ├── BuildingController.java       # new
│   │   ├── FloorController.java          # new
│   │   └── EquipmentTypeController.java  # new
│   ├── service/
│   │   ├── RoomService.java
│   │   ├── BuildingService.java          # includes cascade-deactivate-floors logic
│   │   ├── FloorService.java
│   │   └── EquipmentTypeService.java
│   ├── repository/
│   │   ├── RoomRepository.java
│   │   ├── SeatingArrangementRepository.java
│   │   ├── BuildingRepository.java
│   │   ├── FloorRepository.java
│   │   └── EquipmentTypeRepository.java
│   ├── domain/
│   │   ├── Room.java
│   │   ├── EntityStatus.java             # shared ACTIVE/DEACTIVATED enum (Room, Building, Floor, EquipmentType)
│   │   ├── SeatingArrangement.java
│   │   ├── Building.java
│   │   ├── Floor.java
│   │   └── EquipmentType.java
│   ├── dto/
│   │   ├── RoomRequest.java / RoomResponse.java
│   │   ├── SeatingArrangementRequest.java / SeatingArrangementResponse.java
│   │   ├── BuildingRequest.java / BuildingResponse.java
│   │   ├── FloorRequest.java / FloorResponse.java
│   │   └── EquipmentTypeRequest.java / EquipmentTypeResponse.java
│   └── exception/
│       ├── ConflictException.java        # optimistic-lock / dependent-history conflicts
│       ├── NotFoundException.java
│       └── GlobalExceptionHandler.java
├── src/main/resources/db/migration/
│   ├── V1__create_building_and_floor_tables.sql
│   ├── V2__create_room_tables.sql
│   └── V3__create_equipment_type_tables.sql
└── src/test/java/at/mci/igp/raumlotse/
    ├── controller/  (MockMvc contract tests)
    ├── service/     (unit tests)
    └── repository/  (Testcontainers integration tests)

frontend/
├── src/API/
│   ├── health.ts        # existing
│   ├── rooms.ts          # new — talks to /api/rooms
│   ├── buildings.ts       # new — talks to /api/buildings
│   ├── floors.ts          # new — talks to /api/buildings/{id}/floors, /api/floors
│   └── equipmentTypes.ts # new — talks to /api/equipment-types
├── src/types/
│   └── room.ts            # Room, SeatingArrangement, Building, Floor, EquipmentType TS types
├── src/pages/
│   ├── HomePage.tsx           # existing
│   ├── RoomListPage.tsx        # new
│   ├── RoomFormPage.tsx        # new (create + edit + detail view)
│   └── LocationCatalogPage.tsx  # new — manage buildings and their floors
├── src/components/
│   ├── RoomForm/                       # form + seating-arrangement editor, equipment picker, building/floor selector
│   ├── BuildingCatalog/                 # building + floor management UI (used by LocationCatalogPage and inline from RoomForm's "add building/floor" flow)
│   └── EquipmentCatalog/                # equipment-type management UI
└── (Vitest specs co-located as *.test.tsx next to the component/page they cover)
```

**Structure Decision**: Reuses the existing `backend/` (Spring Boot, layered `controller → service → repository`) and `frontend/` (React + Vite, `API/` + `pages/` already established by `health.ts`/`HomePage.tsx`) split — Option 2 (web application) from the template, with a new `components/` directory on the frontend since this feature is the first to need reusable form components, and a `domain/`/`dto/`/`exception/` split on the backend to keep persistence entities, wire-format DTOs, and error handling from bleeding into each other. Building and Floor follow the exact same controller/service/repository/domain/dto shape as Room and EquipmentType (added mid-planning per change request) rather than inventing a different pattern for them.

## Complexity Tracking

> No Constitution Check violations — this section intentionally left without entries.
