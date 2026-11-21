# Implementation Plan: Path Network (Wegenetz)

**Branch**: `023-path-network` | **Date**: 2026-11-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/023-path-network/spec.md`

## Summary

Administrators draw the walkways of each floor map (Feature 012) as waypoints and undirected straight segments, define one reference segment per map with its real length in meters (from which the scale in pixels per meter is derived), and attach placed rooms and stairs/elevator connection points to waypoints. Everything is stored relative to the map (fractions 0..1) in PostgreSQL. Segment lengths in meters, the scale, and connectivity problems are computed on read. A single read-only endpoint per map and one network-wide endpoint expose the data in a form the later routing feature (024) can consume without further joins. Writes are gated by the existing permission `MAP_MANAGE`; reads use the existing `READ` permission. The map view gains a toggleable SVG overlay plus an editing toolbar.

## Technical Context

**Language/Version**: Java 21 / Spring Boot backend; TypeScript 6 / React 19 frontend

**Primary Dependencies**: Existing only — Spring Web MVC, Spring Data JPA, Bean Validation, Flyway, Spring Security; React Router, typed API client, zod, Vitest + React Testing Library. No map, graph, or canvas library (connectivity via a small in-service union-find; overlay via inline SVG).

**Storage**: PostgreSQL 17; migration `V19__create_path_network.sql` adding `path_waypoint`, `path_segment`, `map_reference_segment`, `room_path_attachment`, `connection_point_path_attachment` (no permission changes)

**Testing**: Backend JUnit/Spring integration (Testcontainers) + service tests; frontend Vitest/RTL, `tsc -b`, ESLint

**Target Platform**: Existing Docker Compose deployment, browser SPA (editing designed for desktop-size screens)

**Project Type**: Full-stack web application (REST API + React SPA)

**Performance Goals**: Network of one map (500 waypoints) retrieved in <2 s p95 (SC-006); drawing actions (add/move/connect) persist in <1 s

**Constraints**: Positions within [0,1]; admin-only writes (server-side permission check); unauthenticated access denied; no segment between waypoints of different maps; no duplicate or self segments; at most one attachment per room and per connection point; no personal data in logs

**Scale/Scope**: Tens of maps, up to ~500 waypoints and ~600 segments per map, hundreds of attached rooms; no routing, no door-sign neighbors, no floor display

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Validation in this Feature Design |
|---|---|---|
| **I. Test-First Development** | PASS | Tasks start each story with failing service/API/component tests (bounds, same-map integrity, duplicate/self segments, split, scale derivation, cascades, permission checks, connectivity check). |
| **II. Modern, Typed, Consistent Codebases** | PASS | Layered controller → service → repository, constructor injection, Bean Validation; strict TS, functional components, ESLint. |
| **III. Contract-First API** | PASS | [contracts/path-network-api.yaml](./contracts/path-network-api.yaml) defines all endpoints, bodies, status codes before implementation. |
| **IV. Secure by Default** | PASS | Server-side `MAP_MANAGE` check on every write via `RoleAccessFilter`, bean validation of coordinates and length, composite foreign keys enforce same-map integrity in the database, parameterized JPA queries only, no personal data in logs. |
| **V. Simplicity & Observability** | PASS | No new library or service; derived values (lengths, scale, issues, components) are computed on read rather than stored; structured logs for rejected writes. |
| **Technology Stack Constraints** | PASS | No dependency outside the stack. |

Post-design re-check: PASS (design added no dependency or service boundary).

## Project Structure

### Documentation (this feature)

```text
specs/023-path-network/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/path-network-api.yaml
└── tasks.md          # created by /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/
├── java/at/mci/igp/raumlotse/
│   ├── domain/        PathWaypoint, PathSegment, MapReferenceSegment,
│   │                  RoomPathAttachment, ConnectionPointPathAttachment,
│   ├── repository/    PathWaypointRepository, PathSegmentRepository, MapReferenceSegmentRepository,
│   │                  RoomPathAttachmentRepository, ConnectionPointPathAttachmentRepository
│   ├── service/       PathNetworkService (edit + read model), MapScaleService,
│   │                  PathNetworkIssueChecker (union-find connectivity, missing scale, unattached),
│   │                  RoleAccessFilter (new GET/PUT/POST/DELETE paths)
│   ├── controller/    PathNetworkController
│   └── dto/           waypoint/segment/reference/attachment requests; PathNetworkResponse,
│                      NetworkWidePathResponse
└── resources/db/migration/V19__create_path_network.sql
backend/src/test/java/...                (service, controller, migration/integration tests)

frontend/src/
├── API/pathNetwork.ts                   (typed client)
├── types/pathNetwork.ts
├── components/PathNetwork/              (PathOverlay (SVG), PathToolbar, ReferenceSegmentTool,
│                                         AttachmentPanel, IssueList, tests)
└── pages/MapPage.tsx                    (overlay toggle + edit tools; permission via useCurrentRoles)
```

**Structure Decision**: Existing backend/frontend web-app layout. The network is an extension of the existing map page; the overlay shares the MapCanvas coordinate frame (relative 0..1 positions), so markers from Feature 012 and the network stay aligned.

## Complexity Tracking

No constitution violations.
