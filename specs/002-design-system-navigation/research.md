# Research: Design System & Navigation

All Technical Context items were resolvable directly from the existing repository (frontend stack,
constitution, current styling approach); no NEEDS CLARIFICATION markers remained after Phase 0. The
decisions below record the choices made and why, for the alternatives that were genuinely open.

## Decision 1: Styling approach — plain CSS custom properties, no new dependency

**Decision**: Implement the design system as CSS custom properties (design tokens) defined in the
existing `frontend/src/index.css`, plus small, colocated component `.css` files where a component needs
layout beyond what shared tokens/utility classes provide (e.g. `Navigation.css`).

**Rationale**: The frontend already has zero styling dependencies — no Tailwind, no CSS-in-JS, no
component library — and styles purely through global tag selectors and CSS custom properties
(`--color-background`, `--color-primary`, etc. already exist in `index.css`). Extending that exact
pattern satisfies Constitution Principle V (Simplicity: no new dependency without a stated reason) and
keeps the bundle and build pipeline unchanged.

**Alternatives considered**:
- *Tailwind CSS / utility-CSS framework* — rejected: adds a build-time dependency and a utility-class
  vocabulary the project doesn't have today, for a scope (4 pages, ~4 components) too small to justify
  the switch; also tends to produce visual noise (many utility classes) that cuts against the "minimal,
  function-only elements" requirement.
- *CSS-in-JS (styled-components/Emotion)* — rejected: adds a runtime dependency and a styling paradigm
  shift; React 19 + Vite + plain CSS is already fast and simple, and the constitution favors the
  simplest design that satisfies the spec.
- *CSS Modules* — considered viable but not chosen: the project has no build config for them yet, and a
  handful of colocated plain `.css` files (already the project's file-colocation convention for
  `Component.tsx`/`Component.test.tsx`) achieve the same scoping-by-convention without a build change.

## Decision 2: Navigation placement and responsive behavior

**Decision**: A persistent horizontal top navigation bar, always visible (not hidden behind a hamburger
toggle), showing all main areas (Home, Standorte, Räume) as text+icon links. At mobile widths the bar
keeps all items visible but compresses (e.g. icon-first, tighter spacing, horizontal scroll only as a
last resort) rather than collapsing into a hidden drawer.

**Rationale**: Spec success criterion SC-001 requires reaching any main area "in a single interaction."
A hamburger/drawer pattern turns that into two interactions (open the menu, then choose) and requires an
open/close animation state, which also cuts against the e-paper "minimal, no animation" aesthetic
(FR-007). With only three main areas, a top bar comfortably fits without needing to hide items, at both
reference viewport widths (SC-005: ~375px and ~1280px).

**Alternatives considered**:
- *Hamburger/off-canvas drawer* — rejected for the reasons above (extra interaction, extra animated
  state, unnecessary for only 3 nav items).
- *Sidebar navigation* — rejected: the existing page layout is a single centered column
  (`main { width: min(1120px, ...) }`); a persistent sidebar would need a layout restructuring across
  every page for a benefit (supporting many nav items) the app doesn't need with only 3 areas.
- *Bottom navigation bar (mobile pattern)* — rejected: adds a second navigation location to design and
  maintain (top on desktop, bottom on mobile) for no added clarity at this scope; one consistent
  placement is simpler and satisfies FR-001/FR-002 at all widths.

## Decision 3: Active-state and semantic-color mapping

**Decision**: Use React Router's `NavLink` (already available via `react-router-dom`) for active-state
detection instead of hand-rolled path matching. Reserve the accent color(s) for the active nav item and
primary actions; map existing feedback states (validation errors, the concurrent-update conflict
warning, success confirmations, empty/loading states) to semantic token names (`--color-danger`,
`--color-warning`, `--color-success`, `--color-info`, `--color-muted`) distinct from the accent palette.

**Rationale**: `NavLink` is a zero-cost, already-installed dependency that removes a class of manual
bugs (matching nested routes like `/rooms/:roomId` back to the `/rooms` nav item). Keeping semantic
colors strictly separate from the ≤3 accent colors is what FR-004/FR-005 require and is what makes
User Story 3 (distinguishable feedback) testable independently of the navigation/branding colors.

**Alternatives considered**:
- *Manual `useLocation().pathname` matching* — rejected: reimplements what `NavLink` already provides,
  with more room for edge-case bugs (e.g. `/rooms` vs `/rooms/new` matching).
- *Reusing the single accent/primary color for both navigation emphasis and success confirmations* —
  rejected: collapses two distinct meanings ("this is the current section" vs. "this action succeeded")
  into one color, which would fail FR-005 (semantic-only outside the accent set) and weaken User Story 3.

## Output

All NEEDS CLARIFICATION items: none remaining. Proceeding to Phase 1 design.
