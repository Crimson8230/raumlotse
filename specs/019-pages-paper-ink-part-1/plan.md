# Implementation Plan: Pages in "Papier & Tinte", Part 1

**Branch**: `019-pages-paper-ink-part-1` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/019-pages-paper-ink-part-1/spec.md`

## Summary

Adopt the building blocks from feature 018 in the existing pages without changing routes, permissions, data or API: mount the skip link in the shared auth frame and give every `<main>` the id `inhalt`; turn the room list into rows with a lazily loaded day strip (per-room reservation query, visible rows only); replace ad-hoc form fields and error paragraphs with `Field` / `ErrorMessage` and map technical field names to labels centrally in `formatApiError`; replace the five hand-written tables with `DataTable` and record lists with `DataList`; apply serif/mono typography; fix the admin navigation wrap at 1280 px; use `.btn-primary` for main actions. Frontend only. Guarded by source-level rule tests so later pages cannot regress.

## Technical Context

**Language/Version**: TypeScript (strict), React 19, Vite; Node as in `frontend/package.json`

**Primary Dependencies**: existing only (React Router, `lucide-react`, `@fontsource/*`). No new dependency.

**Storage**: N/A (no data change; no backend change)

**Testing**: Vitest + Testing Library (jsdom), ESLint; tests-first per constitution I. Static rule tests (file scan) in the style of `components/ui/ui.rules.test.ts`.

**Target Platform**: Modern desktop and phone browsers; verified at 1280 px and ~360 px

**Project Type**: web application (frontend only change)

**Performance Goals**: Room list renders immediately; strips fill in without blocking it; at most 4 concurrent reservation queries; at most 20 rooms queried initially (FR-020).

**Constraints**: Text contrast ≥ 7 : 1, targets ≥ 44 px, tokens only (no color literals), all new text German, existing behavior unchanged (FR-017).

**Scale/Scope**: 14 pages/guards with `<main>`, 14 forms, 5 tables, ~14 lists, 69 call sites of `formatApiError`, 17 `feedback-error` usages.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|---|---|
| I. Test-First | Pass. Every change starts with a failing test; rule tests added first (skip-link/main id, no raw `<table>`, label mapping). Existing tests adapted, none dropped (FR-019, SC-007). |
| II. Typed/consistent | Pass. TypeScript strict, functional components, no `any`. |
| III. Contract-First | Pass. No API change; UI contract for pages in `contracts/ui-pages.md`. The existing reservation query is consumed unchanged. |
| IV. Secure/Data-respecting | Pass. No new data is requested; redacted reservations of others show only time windows (already enforced by backend, FR-023 of 013); error texts shown are label-mapped, not raw server data beyond today's. |
| V. Simplicity | Pass. No new dependency; no new abstraction beyond one small hook (`useRoomDayWindows`) and one helper (`fieldErrorsOf`), each justified in research.md (R3, R4). |

**Re-check after design (Phase 1)**: Pass, no violations. Complexity Tracking not needed.

## Project Structure

### Documentation (this feature)

```text
specs/019-pages-paper-ink-part-1/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── ui-pages.md
├── checklists/
└── tasks.md             # created by /speckit-tasks
```

### Source Code (repository root)

```text
frontend/src/
├── auth/RequireAuth.tsx                 # mounts <SkipLink/> before <Navigation/>
├── API/client.ts                        # formatApiError: label mapping; fieldErrorsOf()
├── components/
│   ├── Navigation/Navigation.{tsx,css}  # 1280 px single line
│   ├── RoomList/RoomRow.tsx             # NEW: row = number, name, Strip, action, favorite, "Verwalten"
│   ├── RoomList/useRoomDayWindows.ts    # NEW: lazy per-room day windows + now tick, day range 07:00–20:00
│   ├── ReservationForm, RoomForm, RoomSearch, BuildingCatalog, EquipmentCatalog,
│   │   ConnectionCatalog, UserRoles, FloorMap/MapUploadControl   # Field / ErrorMessage / primary
│   ├── MyUpcomingReservations, ReservationList                   # DataTable / DataList, typography
│   └── ui/                              # unchanged API (018); small additions only if a gap is found
├── pages/                               # <main id="inhalt">, ErrorMessage, DataTable/DataList, typography
│   ├── RoomListPage.tsx                 # rows via RoomRow
│   └── AdminOccupancyPage, AdminStatisticsPage   # DataTable
└── index.css                            # .room-name, typography utilities; .btn-primary reuse
```

**Structure Decision**: Frontend only; extend existing pages and components in place. One new folder `components/RoomList/` for the room row and its data hook; everything else is edited where it lives.

## Complexity Tracking

No constitution violations to justify.
