# Implementation Plan: Display Room Information

**Branch**: `005-display-room-information` | **Date**: 2026-09-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/005-display-room-information/spec.md`

## Summary

Add a read-only room display view to the existing frontend. The view combines the room name from room-management data, the current local date/time, the currently relevant reservation, and the next eligible reservation on the same local calendar day from feature 004. It presents the reservation note as a bounded, readable preview, the current reservation's creator as `Booked by: <name>`, start/end times as separate values, an always-visible one-line upcoming section when available, and explicit empty/error states. Existing reservation and room APIs are sufficient; no backend schema or endpoint changes are required.

## Technical Context

**Language/Version**: TypeScript 6.0 / JavaScript runtime supplied by Vite; Java 21 backend remains unchanged

**Primary Dependencies**: React 19, React Router 7, Vite 8, Vitest 5, React Testing Library; existing room and reservation API clients

**Storage**: Existing PostgreSQL reservation and room data accessed through current REST endpoints; no new storage

**Testing**: Vitest and React Testing Library component tests; frontend TypeScript build and ESLint

**Target Platform**: Modern desktop/mobile browser displaying a single-room e-ink mockup view at 800 × 480 CSS pixels

**Project Type**: Full-stack web application; this feature is frontend-only and consumes existing REST data

**Performance Goals**: Initial room data renders after the existing API request completes; the visible clock refreshes every 30 seconds and never intentionally displays a value more than 60 seconds old

**Constraints**: Read-only, 800 × 480 CSS-pixel mockup, body text no smaller than 14 px, no scrolling or navigation required for required fields, no separate title/lecturer fields, current interval uses `[startTime, endTime)`, current reservation displays required `createdBy` as `Booked by: <name>`, upcoming reservations are same-local-day only, the upcoming section uses one line, note space is reduced before upcoming content is omitted, long notes use an explicit truncation marker such as `...`, and unresolved data must not produce stale reservation content

**Scale/Scope**: One display view for one room at a time; at most one selected current reservation is rendered even when the API returns multiple reservations

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Validation in this Feature Design |
|---|---|---|
| **I. Test-First Development (NON-NEGOTIABLE)** | PASS | Add failing component tests for current-reservation selection, note fallback/truncation, empty/error states, and clock refresh before implementation. |
| **II. Modern, Typed, and Consistent Codebases** | PASS | Use strict TypeScript, functional React components, existing API/type utilities, and ESLint-compliant code. |
| **III. Contract-First API Design** | PASS | Reuse the existing room and reservation response shapes and document the frontend display contract in `contracts/room-display-view.md`; no API change is introduced. |
| **IV. Secure & Data-Respecting by Default** | PASS | Display only data already authorized by existing room/reservation views; render note text as text content and do not introduce logging or persistence of personal data. |
| **V. Simplicity & Observability** | PASS | Keep selection and formatting local to the display view, reuse existing API clients/date utilities, and expose loading/error states visibly without adding a new service boundary. |
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
backend/                          # unchanged; existing feature-004 API is reused
```

**Structure Decision**: Extend the existing React SPA with a dedicated `RoomDisplay` component and `RoomDisplayPage`, keeping data access in the existing `frontend/src/API` modules and formatting helpers in `frontend/src/utils`. The page shell is created before the `/rooms/:roomId/display` route is registered. The backend and database remain unchanged because feature 004 already exposes the required `roomName`, `note`, `createdBy`, `startTime`, and `endTime` fields.

## Complexity Tracking

No constitution violations. No new backend service, database table, dependency, or API endpoint is needed.

## Phase 0: Research Summary

See [research.md](./research.md) for decisions on current/upcoming reservation selection, API reuse, clock refresh, note preview behavior, and deterministic handling of inconsistent source data.

## Phase 1: Design Summary

- [data-model.md](./data-model.md) defines the display view model derived from `Room` and `Reservation`.
- [contracts/room-display-view.md](./contracts/room-display-view.md) defines the frontend display contract and state behavior.
- [quickstart.md](./quickstart.md) defines automated and manual validation scenarios.

## Post-Design Constitution Check

All gates remain PASS. The design adds only a typed frontend presentation layer and tests, reuses existing contracts/utilities, and keeps the display read-only and observable through explicit loading, empty, and error states.
