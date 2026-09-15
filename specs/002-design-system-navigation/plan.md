# Implementation Plan: Design System & Navigation

**Branch**: `002-design-system-navigation` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-design-system-navigation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Give the existing four frontend pages (Home, Location Catalog, Room List, Room Form) a single shared,
e-paper-inspired visual language — CSS custom-property design tokens (≤3 accent colors, everything else
semantic), function-only visual elements, WCAG AA contrast — and add a persistent primary navigation
component so every main area (Home, Standorte, Räume) is reachable from any page with active-state
indication. No backend or API changes; purely a frontend styling + navigation addition using the
project's existing plain-CSS approach (no new UI/styling dependency).

## Technical Context

**Language/Version**: TypeScript (strict mode), React 19, on Node/Vite 8 tooling (existing frontend stack)

**Primary Dependencies**: React Router 7 (already used for routing; navigation links/active-state via
`NavLink`), `lucide-react` (already a dependency; used only for functional icons, e.g. marking the
active nav item or a status icon — never purely decorative). Styling uses plain CSS with CSS custom
properties, following the pattern already established in `frontend/src/index.css` — no CSS framework or
CSS-in-JS library is introduced (see research.md).

**Storage**: N/A — no data model or persistence changes; feature is visual/navigational only

**Testing**: Vitest + `@testing-library/react` (existing pattern, see `RoomListPage.test.tsx`,
`BuildingCatalog.test.tsx`, `RoomForm.test.tsx`) for the new Navigation component (renders all items,
marks the active route, all destinations link correctly) and for updated snapshots/assertions on
existing pages if restyling changes queryable markup (e.g. added `className`s or roles)

**Target Platform**: Web browser, via the existing Vite dev/build pipeline; must render correctly at
common desktop (~1280px) and mobile (~375px) viewport widths per FR-010/SC-005

**Project Type**: Web application (existing `backend/` + `frontend/` split); this feature is
frontend-only

**Performance Goals**: No perceptible regression to page load or interaction responsiveness; changes
are CSS plus one small, dependency-light Navigation component — no added network requests or heavy
client-side computation

**Constraints**: ≤3 accent colors total (FR-004); all other colors semantic (FR-005); visual elements
only where functional (FR-006); e-paper aesthetic — high contrast, flat surfaces, no/minimal
shadows-gradients-animation (FR-007); WCAG 2.1 AA contrast (FR-011); no backend/API/contract changes
(Principle III); no new runtime dependency without justification (Principle V)

**Scale/Scope**: 4 existing pages, 3 existing components (`BuildingCatalog`, `EquipmentCatalog`,
`RoomForm`), 1 new `Navigation` component, 1 shared design-token stylesheet

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Applies? | Assessment |
|-----------|----------|------------|
| I. Test-First Development | Yes | The new `Navigation` component gets a failing Vitest/RTL test (renders all main areas, marks active route, links navigate) written before its implementation, per the existing colocated `Component.tsx` + `Component.test.tsx` pattern. Existing page tests are updated only where restyled markup changes what they assert on — no test is weakened to make styling pass. |
| II. Modern, Typed, and Consistent Codebases | Yes | New/changed code stays TypeScript-strict, functional-component React, and must pass the existing ESLint config; no `any` or lint suppressions introduced. |
| III. Contract-First API Design | No | This feature makes no backend or API changes; existing REST contracts are untouched. Gate trivially satisfied. |
| IV. Secure and Data-Respecting by Default | No | No new data handling, forms, or secrets are introduced; existing validated forms keep their current validation logic, only their presentation changes. |
| V. Simplicity and Observability | Yes | No new styling/UI dependency is added — the design system is built with plain CSS custom properties extending the pattern already in `index.css`, matching YAGNI and avoiding an unjustified library addition. No new services or health-check surface is introduced. |

**Result**: PASS — no violations, no entries required in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/002-design-system-navigation/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── index.css                       # extended: design tokens (colors, type scale, spacing)
│   ├── App.tsx                         # updated: renders <Navigation /> + routed pages
│   ├── components/
│   │   ├── Navigation/
│   │   │   ├── Navigation.tsx          # new: primary navigation, active-state via NavLink
│   │   │   ├── Navigation.css          # new: nav-specific layout (colocated, no framework)
│   │   │   └── Navigation.test.tsx     # new: covers acceptance scenarios for User Story 1
│   │   ├── BuildingCatalog/            # restyled to use shared tokens/classes (FR-008)
│   │   ├── EquipmentCatalog/           # restyled to use shared tokens/classes (FR-008)
│   │   └── RoomForm/                   # restyled; validation/conflict states use semantic classes (FR-009)
│   │       └── RoomForm.css            # new: fieldset/checkbox layout (colocated, T015)
│   └── pages/
│       ├── HomePage.tsx                # restyled to use shared tokens/classes (FR-008)
│       ├── HomePage.css                # new: loading/unreachable status classes (T025)
│       ├── LocationCatalogPage.tsx     # restyled
│       ├── RoomListPage.tsx            # restyled; empty/loading states use semantic classes
│       ├── RoomListPage.css            # new: list/filter layout (colocated, T014; status colors live in index.css)
│       └── RoomFormPage.tsx            # restyled
└── (backend/ unchanged — no files touched by this feature)
```

**Structure Decision**: Existing web-application layout (`backend/` + `frontend/`) is kept as-is; this
feature only touches `frontend/src`. Design tokens live in the already-established `frontend/src/index.css`
(no new global stylesheet file, avoiding a second source of truth). The new `Navigation` component is
colocated under `frontend/src/components/Navigation/` following the existing `Component.tsx` +
`Component.test.tsx` (+ component-scoped `.css` where a component needs layout beyond shared tokens)
convention already used by `BuildingCatalog/`, `EquipmentCatalog/`, and `RoomForm/`.

## Complexity Tracking

*No violations — table intentionally omitted.*
