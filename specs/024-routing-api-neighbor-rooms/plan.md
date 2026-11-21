# Implementation Plan: Routing and Neighbor Rooms on the Door Sign

**Branch**: `024-routing-api-neighbor-rooms` | **Date**: 2026-11-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/024-routing-api-neighbor-rooms/spec.md`

## Summary

The backend computes shortest routes (Dijkstra, walking-time cost) over the path network of Feature 023, across floors via stairs and elevators, and derives the nearest neighbors of a room (up to 3 left, up to 3 right, stairs/elevator as up/down entries). Two read-only endpoints expose this: neighbors of a room and a route between two rooms. The door sign (`/rooms/:roomId/display`) loads the neighbors next to its existing data and renders them with the existing `Wayfinding` block plus the neighbor's status; it omits the area when none can be shown.

Connections between different buildings are explicitly not supported (ignored by routing).

Research found one gap in the existing data: floors have no order, so "up/down" cannot be derived. Decision (user): floors get a sortable order per building, managed on the admin locations page; the lowest entry is automatically the ground floor. This replaces the manual ground-floor checkbox of Feature 008 (the `groundFloor` API field stays, now derived).

## Technical Context

**Language/Version**: Java 21 / Spring Boot backend; TypeScript / React frontend (as in 023)

**Primary Dependencies**: Existing only — Spring Web MVC, Spring Data JPA, Flyway, Spring Security; React, typed API client, zod, Vitest + React Testing Library. Dijkstra is hand-written on `java.util.PriorityQueue`; no graph library. Floor reordering uses up/down buttons, no drag-and-drop library.

**Storage**: PostgreSQL 17; migration `V20__floor_position.sql` adds `floor.position` (backfill). Routes and neighbors are computed on request and not stored.

**Testing**: Backend JUnit service tests (pure routing/neighbor logic on hand-built graphs) + Spring integration tests; frontend Vitest/RTL, `tsc -b`, ESLint

**Target Platform**: Existing Docker Compose deployment; browser SPA, door sign on shared displays

**Project Type**: Full-stack web application (REST API + React SPA)

**Performance Goals**: Neighbors/route in <1 s for 500 waypoints per floor × 5 floors (SC-004)

**Constraints**: Read-only; READ permission for routing endpoints (displays are authenticated sessions as in 005); unauthenticated denied; no estimates when room unattached, scale missing, or not connected; no personal data in logs

**Scale/Scope**: ~2500 waypoints in the whole graph, ≤ a few hundred rooms; graph built per request from the 023 read model (no cache in this feature)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Validation in this Feature Design |
|---|---|---|
| **I. Test-First Development** | PASS | Tasks begin each story with failing tests: Dijkstra on small graphs (shortest, cross-floor, accessible-only, unreachable), left/right/straight geometry, neighbor limits, error reasons, migration backfill, door-sign rendering. |
| **II. Modern, Typed, Consistent Codebases** | PASS | Controller → service → pure routing classes; constructor injection; strict TS, functional components. |
| **III. Contract-First API** | PASS | [contracts/routing-api.yaml](./contracts/routing-api.yaml) defines both endpoints plus the floor-order endpoint before implementation. `FloorResponse.groundFloor` keeps its shape (now derived), so no breaking change; `groundFloor` on floor create/update requests is accepted but ignored (documented). |
| **IV. Secure by Default** | PASS | Server-side READ permission via `RoleAccessFilter`; floor reorder requires the existing floor-management permission; UUID path/query params validated; no write path in routing; no personal data in logs. |
| **V. Simplicity & Observability** | PASS | No new library or service; no caching or persistence of results; structured log line for unroutable requests (reason code, no personal data). |
| **Technology Stack Constraints** | PASS | No dependency outside the stack. |

Post-design re-check: PASS (the floor `position` column is the only schema addition and is required by FR-007).

## Project Structure

### Documentation (this feature)

```text
specs/024-routing-api-neighbor-rooms/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/routing-api.yaml
└── tasks.md          # created by /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/
├── java/at/mci/igp/raumlotse/
│   ├── domain/        Floor (+ position)
│   ├── repository/    FloorRepository (order queries)
│   ├── service/       RoutingGraph (builds nodes/edges from NetworkWideResponse + floor positions),
│   │                  RouteCalculator (Dijkstra, time cost, accessibleOnly),
│   │                  NeighborCalculator (same-floor search, left/right/straight, up/down entries),
│   │                  RoutingService (loads data, maps problems to reasons, adds room status),
│   │                  FloorService (reorder, derived ground floor),
│   │                  RoleAccessFilter (new GET paths)
│   ├── controller/    RoutingController, FloorController (order endpoint)
│   └── dto/           RouteResponse, RouteStepResponse, NeighborsResponse, NeighborEntryResponse,
│                      RoutingProblem; FloorOrderRequest
└── resources/db/migration/V20__floor_position.sql
backend/src/test/java/...                  (routing unit tests, integration, migration)

frontend/src/
├── API/routing.ts, API/floors.ts          (neighbors client; reorder call)
├── types/routing.ts
├── components/RoomDisplay/                (NeighborSection + mapping to Wayfinding; props/types extended)
├── components/BuildingCatalog/            (sortable floor list, ground-floor checkbox removed)
└── pages/RoomDisplayPage.tsx              (loads neighbors, failure-tolerant)
```

**Structure Decision**: Extend the existing backend/frontend layout; the routing logic is isolated in plain classes (`RoutingGraph`, `RouteCalculator`, `NeighborCalculator`) with no database access so it is unit-testable and reusable by the floor display (026) through `RoutingService`.

## Complexity Tracking

No constitution violations.
