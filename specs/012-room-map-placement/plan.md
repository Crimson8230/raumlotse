# Implementation Plan: Room Map Placement

**Branch**: `012-room-map-placement` | **Date**: 2026-10-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/012-room-map-placement/spec.md`

## Summary

Administrators upload one floor-plan image per existing floor, place rooms of that floor on it, and define named stairs/elevator connections that have one point per map. Positions are stored as fractions (0..1) of the image width/height so they are resolution- and zoom-independent. All signed-in sessions can read; only administrators can write. The data is exposed through REST so the later route feature can consume maps, room positions, and connections. Images are stored in PostgreSQL (no new infrastructure) and served with ETag caching.

## Technical Context

**Language/Version**: Java 21 / Spring Boot backend; TypeScript 6 / React 19 frontend

**Primary Dependencies**: Existing only — Spring Web MVC (multipart), Spring Data JPA, Bean Validation, Flyway, Spring Security; React Router, existing typed API client, zod, Vitest + React Testing Library. Image dimension/format validation uses the JDK `ImageIO`. No map/canvas library.

**Storage**: PostgreSQL 17; one migration (`V13`) adding `floor_map`, `room_placement`, `connection`, `connection_point`; image bytes in a `bytea` column

**Testing**: Backend JUnit/Spring integration (Testcontainers) + service tests; frontend Vitest/RTL, `tsc -b`, ESLint

**Target Platform**: Existing Docker Compose deployment, browser SPA

**Project Type**: Full-stack web application (REST API + React SPA)

**Performance Goals**: Map metadata + placements load in <1 s p95; image served from cache (ETag/304) after first load; placing a room completes in <1 s

**Constraints**: Image ≤ 10 MB, PNG/JPEG only (content verified server-side, not by extension); positions within [0,1]; admin-only writes; unauthenticated access denied; no secrets/personal data in logs

**Scale/Scope**: Tens of floors, hundreds of rooms, a few dozen connections; one map per floor; no route calculation, no display placement

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Validation in this Feature Design |
|---|---|---|
| **I. Test-First Development** | PASS | Tasks begin each story with failing service/API/component tests (placement bounds, floor match, admin-only writes, connection cascades, upload validation). |
| **II. Modern, Typed, Consistent Codebases** | PASS | Layered controller → service → repository, constructor injection, Bean Validation; strict TS, functional components, ESLint. |
| **III. Contract-First API** | PASS | [contracts/room-map-api.yaml](./contracts/room-map-api.yaml) defines all endpoints, bodies, and status codes before implementation. |
| **IV. Secure by Default** | PASS | Admin check on all writes (server-side), bean validation of coordinates, magic-byte/ImageIO verification, SVG rejected, `X-Content-Type-Options: nosniff` on image responses, JPA/parameterized queries only, no image/user data in logs. |
| **V. Simplicity & Observability** | PASS | No new library or service; images in existing DB; structured logs for rejected uploads and placement failures. |
| **Technology Stack Constraints** | PASS | No dependency outside the stack. |

Post-design re-check: PASS (design added no new dependency or service boundary).

## Project Structure

### Documentation (this feature)

```text
specs/012-room-map-placement/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/room-map-api.yaml
└── tasks.md          # created by /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/
├── java/at/mci/igp/raumlotse/
│   ├── domain/        FloorMap, RoomPlacement, Connection, ConnectionPoint, ConnectionType
│   ├── repository/    FloorMapRepository, RoomPlacementRepository, ConnectionRepository, ConnectionPointRepository
│   ├── service/       FloorMapService, RoomPlacementService, ConnectionService, MapImageValidator
│   ├── controller/    FloorMapController, RoomPlacementController, ConnectionController
│   ├── dto/           map/placement/connection requests and responses
│   ├── service/RoleAccessFilter.java   (extend: admin-only for non-GET map/connection paths)
│   └── config/SecurityConfig.java      (no change expected; endpoints stay `authenticated()`)
└── resources/
    ├── db/migration/V13__create_room_map_tables.sql
    └── application.yaml                  (multipart max-file-size/max-request-size 10MB)
backend/src/test/java/...                (service, controller, migration/integration tests)

frontend/src/
├── API/maps.ts                          (typed client incl. multipart upload)
├── types/map.ts
├── components/FloorMap/                 (MapCanvas, markers, unplaced list, admin controls, tests)
├── components/ConnectionCatalog/        (create/edit connections, point placement)
└── pages/MapPage.tsx                    (route `/maps`, `/maps/:mapId`; admin controls via useCurrentRoles)
```

**Structure Decision**: Existing backend/frontend web-app layout; map editing is a new page and a small set of components following the `RoomDisplay`/`BuildingCatalog` conventions.

## Complexity Tracking

No constitution violations.
