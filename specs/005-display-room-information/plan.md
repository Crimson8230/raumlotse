# Implementation Plan: Display Room Information

**Branch**: `005-display-room-information` | **Date**: 2026-09-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-display-room-information/spec.md`

## Summary

Add a read-only room display view to the existing frontend. The view combines the room name from room-management data, the current local date/time, and the currently relevant reservation from feature 004. It presents `Booked by`, `Note`, `Start time`, and `End time`, plus explicit empty/error states. The room detail view must expose a `Display Room Information` button next to `Edit Room`, opening the display for the same room. Existing reservation and room APIs are reused.

## Technical Context

**Language/Version**: TypeScript 6.0 / JavaScript runtime supplied by Vite; Java 21 Spring Boot backend

**Primary Dependencies**: React 19, React Router 7, Vite 8, Vitest 5, React Testing Library; existing room and reservation API clients

**Storage**: Existing PostgreSQL reservation and room data; no schema changes are required

**Testing**: Vitest and React Testing Library component tests; frontend TypeScript build and ESLint

**Target Platform**: Modern desktop/mobile browser displaying a single-room e-ink mockup view at the project's intended display size

**Project Type**: Full-stack web application; this feature is frontend-only and consumes existing REST data

**Performance Goals**: Initial room data renders after the existing API requests complete; the visible clock refreshes often enough to remain within the 60-second freshness requirement

**Constraints**: Read-only display, no scrolling or navigation required to find required fields, display only `Booked by`, `Note`, `Start time`, and `End time` for the current reservation, current interval uses `[startTime, endTime)`, missing notes use visible fallback text, the room-management entry point is next to `Edit Room`, and unresolved data must not produce stale reservation content

**Scale/Scope**: One display view for one room at a time; at most one selected current reservation is rendered even when the API returns multiple reservations

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Validation in this Feature Design |
|---|---|---|
| **I. Test-First Development (NON-NEGOTIABLE)** | PASS | Add failing component and page tests for current-reservation selection, note fallback, room-detail navigation, empty/error states, and clock refresh before implementation. |
| **II. Modern, Typed, and Consistent Codebases** | PASS | Use strict TypeScript, functional React components, existing API/type utilities, and ESLint-compliant code. |
| **III. Contract-First API Design** | PASS | Reuse and document the existing reservation response contract for creator, note, start, and end values in `contracts/room-display-view.md`. |
| **IV. Secure & Data-Respecting by Default** | PASS | Render existing reservation text as text content and do not introduce logging or persistence beyond the reservation record. |
| **V. Simplicity & Observability** | PASS | Reuse existing room/reservation endpoints and date utilities, keep selection/formatting local to the display view, and expose loading/error states visibly without adding a new service boundary. |
| **Tech Stack Constraints** | PASS | Uses the existing React/TypeScript/Vite frontend and current REST contracts; no new dependency is required. |

## Project Structure

### Documentation (this feature)

```text
specs/005-display-room-information/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── room-display-view.md
└── tasks.md                 # Phase 2; not created by this command
```

### Source Code (repository root)

```text
frontend/
└── src/
    ├── API/
    │   ├── rooms.ts              # existing room retrieval
    │   └── reservations.ts       # existing room reservation retrieval
    ├── components/
    │   └── RoomDisplay/          # new display component, styles, and tests
    ├── pages/
    │   └── RoomDisplayPage.tsx   # new route/page and styles/tests
    ├── types/
    └── utils/
backend/                          # unchanged; existing reservation API is reused
```

**Structure Decision**: Extend the existing React SPA with a dedicated `RoomDisplay` component and `RoomDisplayPage`, keeping data access in the existing API modules and formatting helpers in `frontend/src/utils`. Add the `Display Room Information` link to `RoomDetailPage` beside `Edit Room`, and register `/rooms/:roomId/display`. Reuse feature 004's existing reservation entity, DTOs, and frontend type; no display-specific endpoint or schema change is needed.

## Complexity Tracking

No constitution violations. No new service, database table, dependency, schema change, or API endpoint is needed.

## Phase 0: Research Summary

See [research.md](./research.md) for decisions on API reuse, current-reservation selection, clock refresh, fallback behavior, and deterministic handling of inconsistent source data.

## Phase 1: Design Summary

- [data-model.md](./data-model.md) defines the display view model derived from `Room` and `Reservation`.
- [contracts/room-display-view.md](./contracts/room-display-view.md) defines the frontend display contract, route entry point, and state behavior.
- [quickstart.md](./quickstart.md) defines automated and manual validation scenarios.

## Post-Design Constitution Check

All gates remain PASS. The design adds a typed display layer and tests; it keeps the display read-only and observable through explicit loading, empty, and error states.
