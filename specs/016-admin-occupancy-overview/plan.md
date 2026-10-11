# Implementation Plan: Admin-Belegungsübersicht

**Branch**: `016-admin-occupancy-overview` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/016-admin-occupancy-overview/spec.md`

## Summary

A new admin page "Belegung" shows the current situation (checked-in persons = Σ expected attendees of covering `ACTIVE` bookings, occupied rooms, per-building breakdown, list of running bookings) and an hourly day forecast (reserved and checked-in bookings) per building and in total, with peaks and the booked rooms per hour. Two read-only endpoints, `GET /api/admin/occupancy` and `GET /api/admin/occupancy/forecast`, are guarded by the existing `STATISTICS_READ` permission and follow the structure of the statistics feature (011). Current values reuse the room display's covering rule (feature 009/014) so that both views always agree. The forecast's hourly maximum is computed in memory by a pure, clock-driven service. No schema change.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript strict (frontend)

**Primary Dependencies**: Spring Boot (Web MVC, Data JPA, Validation), React 19, React Router 7, lucide-react. No new dependencies.

**Storage**: PostgreSQL 17, existing `reservation`, `room`, `floor`, `building` tables; no migration (research R7).

**Testing**: JUnit 5 + AssertJ + MockMvc (backend), Vitest + Testing Library (frontend)

**Target Platform**: Linux server via Docker Compose; desktop and tablet browsers

**Project Type**: Web application (Spring Boot backend + React SPA)

**Performance Goals**: First complete render ≤ 3 s with 100 rooms and 500 running bookings (SC-006)

**Constraints**: Refresh ≤ 60 s (30 s polling); time zone Europe/Berlin including DST; no personal data in responses; read-only

**Scale/Scope**: ~100 rooms, a few buildings, ≤ 500 concurrent bookings; 1 new page, 2 endpoints

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan complies |
|---|---|---|
| I. Test-First | ✅ | Tasks start with failing tests: service unit tests with a fixed `Clock` (R1/R2 cases, DST, past hours), controller/MockMvc tests for status codes, access tests in `AuthorizationMatrixTest`/`ProtectedBusinessRouteTest`, and a page test before the UI. |
| II. Typed, consistent code | ✅ | Controller → service → repository like feature 011; records for DTOs; constructor injection; strict TS types in `types/adminOccupancy.ts`. |
| III. Contract-first | ✅ | [contracts/admin-occupancy.yaml](contracts/admin-occupancy.yaml) defines both endpoints before implementation; the frontend uses only these shapes. No existing endpoint changes. |
| IV. Secure by default | ✅ | `STATISTICS_READ` rule in `RoleAccessFilter`; JPQL with bound parameters; `buildingId`/`date` validated; DTOs omit `reservedFor`, `createdBy`, `note`, user ids; `Cache-Control: no-store`; logs contain no personal data. |
| V. Simplicity & observability | ✅ | Polling instead of push, in-memory aggregation instead of SQL bucketing, no new index or library. Errors go through the existing `GlobalExceptionHandler` with structured logs. |

**Result**: PASS, no violations. Re-checked after Phase 1: still PASS. The design added no abstraction beyond one service, one repository class and DTO records.

## Project Structure

### Documentation (this feature)

```text
specs/016-admin-occupancy-overview/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/admin-occupancy.yaml
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/at/mci/igp/raumlotse/
├── controller/AdminOccupancyController.java          # new: GET /api/admin/occupancy, /forecast
├── service/AdminOccupancyService.java                # new: snapshot + forecast (Clock-driven, pure aggregation)
├── service/RoleAccessFilter.java                     # + rule for /api/admin/occupancy(/forecast) → STATISTICS_READ
├── repository/OccupancyRepository.java               # new: EntityManager queries returning row records (pattern of StatisticsRepository)
└── dto/AdminOccupancyResponse.java                   # new: Snapshot, BuildingOccupancy, BookingEntry, Forecast, HourForecast records

backend/src/test/java/at/mci/igp/raumlotse/
├── service/AdminOccupancyServiceTest.java            # new
├── controller/AdminOccupancyControllerTest.java      # new
├── repository/OccupancyRepositoryIntegrationTest.java   # new (AbstractIntegrationTest)
└── security/AuthorizationMatrixTest.java, ProtectedBusinessRouteTest.java  # + new routes

frontend/src/
├── API/adminOccupancy.ts                             # new
├── types/adminOccupancy.ts                           # new
├── pages/AdminOccupancyPage.tsx (+ .css, .test.tsx)  # new: KPIs, building breakdown/filter, running list, day forecast
├── components/Navigation/Navigation.tsx              # + "Belegung" entry (rights: STATISTICS_READ)
└── App.tsx                                           # + /admin/occupancy route under RequirePermission STATISTICS_READ
```

**Structure Decision**: Existing web-application layout (`backend/` + `frontend/`). The feature sits next to the statistics feature (011) and reuses its access, caching and page patterns.

## Complexity Tracking

No constitution violations; nothing to justify.
