# Implementation Plan: Room Status Visualization

**Branch**: `009-room-status-visualization` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-room-status-visualization/spec.md`

## Summary

Extend the existing room display so viewers can identify availability immediately from a
status color and text label above the current date/time, while seeing the earliest future
`RESERVED` booking in a single-line `Next Reservation` presentation. The implementation remains a read-only frontend enhancement:
reuse the existing room and room-reservation endpoints, derive the display state from the
loaded records and current time, refresh the display data on the existing 30-second cadence,
and cover the decision rules with frontend unit/component tests.

## Technical Context

**Language/Version**: TypeScript strict mode, React 19/Vite frontend; Java 21/Spring Boot backend remains unchanged

**Primary Dependencies**: Existing React Router, Vitest, Testing Library, and room/reservation API client modules

**Storage**: Existing PostgreSQL reservation data accessed through the existing REST resources; no schema change

**Testing**: Vitest unit tests for status/selection logic and React Testing Library component/page tests; existing Maven tests remain the regression suite

**Target Platform**: Browser-based React room display at the existing e-ink display mockup size

**Project Type**: Web application with React frontend and Spring Boot REST backend

**Performance Goals**: Status and next-reservation derivation completes within 100 ms for the existing room-reservation response size; reservation refresh remains on the existing 30-second display cadence

**Constraints**: Preserve existing API contracts and half-open reservation intervals `[startTime, endTime)`; status meaning must not depend on color alone; render the status above the current date/time and keep the complete next-reservation content on one readable line at the intended display size; no new dependency or service boundary

**Scale/Scope**: One room display at a time; existing room reservation list size and endpoint pagination/limits remain unchanged; feature is limited to the room display view

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **PASS** — Test-first delivery: add failing logic/component tests before implementation changes.
- **PASS** — Typed frontend: use strict TypeScript, functional React components, and no unjustified `any` or lint suppression.
- **PASS** — Contract-first integration: reuse and document the existing room/reservation response shapes; no breaking API change is planned.
- **PASS** — Secure/data-respecting: display only already-authorized room/reservation fields; no new user-data logging, persistence, or input endpoint.
- **PASS** — Simplicity/observability: keep derivation in the existing RoomDisplay logic boundary; no new service or dependency is needed.

## Project Structure

### Documentation (this feature)

```text
specs/009-room-status-visualization/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
```text
backend/
├── src/main/java/at/mci/igp/raumlotse/
│   ├── controller/                 # existing room/reservation REST contracts
│   ├── service/                    # unchanged for this feature
│   └── repository/                 # unchanged for this feature
└── src/test/java/at/mci/igp/raumlotse/ # regression tests

frontend/
└── src/
    ├── components/RoomDisplay/
    │   ├── RoomDisplay.tsx
    │   ├── RoomDisplay.css
    │   ├── roomDisplayLogic.ts
    │   ├── roomDisplayTypes.ts
    │   └── *.test.ts(x)
    ├── pages/RoomDisplayPage.tsx
    │   └── RoomDisplayPage.test.tsx
    └── API/reservations.ts          # existing endpoint client
```

**Structure Decision**: Use the existing split web application structure. The feature is
implemented in `frontend/src/components/RoomDisplay` and `frontend/src/pages/RoomDisplayPage.tsx`.
The backend remains contract-compatible and needs no production-code changes unless validation
reveals that the existing room reservation response cannot supply the specified fields.

## Post-Design Constitution Check

- **PASS** — Tests can be written first for selection precedence, terminal-state exclusion,
  next-reservation selection, status labels, status-before-date layout, one-line readability,
  and failure states.
- **PASS** — The design uses existing strict TypeScript/React patterns and introduces no
  untyped boundary or dependency.
- **PASS** — Existing REST response contracts are documented and reused without a breaking
  change.
- **PASS** — No new persistence, authentication, logging, or personal-data flow is introduced.
- **PASS** — The design adds only a small derivation/view-model boundary and reuses the existing
  refresh behavior; no unnecessary abstraction or service is proposed.
