# Implementation Plan: Room Status Visualization

**Branch**: `009-room-status-visualization` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/009-room-status-visualization/spec.md`

## Summary

Update the existing frontend room display so the current status is derived from the current
reservation interval and check-in lifecycle: a future reservation leaves a free room
`Verfügbar` in green, an unclaimed reservation is `Reserviert` in yellow only during its
active time window, and a checked-in `ACTIVE` reservation is `Belegt` in red. Preserve the
existing next-reservation display, refresh cadence, API contracts, and unavailable-data state.

## Technical Context

**Language/Version**: TypeScript strict mode, React 19, Vite

**Primary Dependencies**: Existing React components, Vitest, Testing Library, and reservation API client

**Storage**: Existing PostgreSQL-backed room and reservation REST resources; no schema change

**Testing**: Frontend logic unit tests, RoomDisplay component tests, and RoomDisplayPage refresh/transition tests; backend regression tests remain unchanged

**Target Platform**: Browser-based room display at the existing display layout size

**Project Type**: React frontend with Spring Boot REST backend

**Performance Goals**: Status derivation remains below 100 ms for the existing reservation-list size; refresh remains on the existing 30-second cadence

**Constraints**: Preserve API contracts and half-open intervals `[startTime, endTime)`; status must be understandable without color alone; no new dependency, endpoint, persistence, or service boundary

**Scale/Scope**: One room display at a time; only `frontend/src/components/RoomDisplay` and its page/tests are in scope

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **PASS** — Test-first delivery: update failing status-derivation and component/page tests before implementation changes.
- **PASS** — Typed frontend: use existing strict TypeScript and functional React patterns.
- **PASS** — Contract-first integration: existing room/reservation response shapes are sufficient; no API change.
- **PASS** — Secure/data-respecting: no new persistence, authorization, logging, or personal-data flow.
- **PASS** — Simplicity/observability: change the existing derivation and presentation boundary only.

## Project Structure

### Documentation (this feature)

```text
specs/009-room-status-visualization/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/room-status-display.md
└── tasks.md
```

### Source Code

```text
frontend/src/components/RoomDisplay/
├── roomDisplayLogic.ts       # status and reservation selection rules
├── roomDisplayLogic.test.ts  # rule-level tests
├── RoomDisplay.tsx           # status labels and layout
├── RoomDisplay.css           # status colors and one-line layout
└── RoomDisplay.test.tsx      # presentation tests
frontend/src/pages/
├── RoomDisplayPage.tsx       # data loading and 30-second refresh
└── RoomDisplayPage.test.tsx  # loading and lifecycle transition tests
```

**Structure Decision**: Reuse the existing frontend display boundary. The backend remains
unchanged because the response already contains room id, reservation interval, lifecycle
status, and `reservedFor`.

## Post-Design Constitution Check

- **PASS** — Tests cover future/free, current unclaimed, current checked-in, terminal-state, precedence, refresh, and unavailable-data cases.
- **PASS** — No new untyped boundary, dependency, API, persistence, or service boundary is introduced.
- **PASS** — Existing REST response contracts and half-open interval semantics are retained.
- **PASS** — The design exposes only existing display data and preserves the current fallback behavior.
- **PASS** — The change is limited to the existing logic, labels, styles, and tests.
