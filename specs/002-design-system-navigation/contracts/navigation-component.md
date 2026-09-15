# Contract: `Navigation` Component

## Purpose

Renders the persistent primary navigation required by FR-001/FR-002, satisfying User Story 1.

## Props

```ts
export interface NavigationProps {}
```

No props: the Navigation Item list (Home `/`, Standorte `/locations`, Räume `/rooms`) is a fixed,
internal constant per the Data Model — it is not externally configurable by this feature's scope
(Assumptions: no new pages/sections are introduced).

## Rendering contract

- Renders one link per Navigation Item (see data-model.md), each using React Router's `NavLink` so the
  active item is derived from the current route rather than tracked in component state.
- The active item MUST be visually distinguished using an accent Design Token; inactive items use
  neutral tokens only (contracts/design-tokens.md).
- MUST render identically (same items, same markup shape) regardless of which page currently hosts it —
  it is mounted once in `App.tsx`, not per-page.
- MUST remain fully visible and operable (no hidden/collapsed items) at both reference viewport widths
  (~375px, ~1280px) per FR-010/SC-005.
- Icons (if used) come from the existing `lucide-react` dependency and are decorative-adjacent only —
  each nav item's label text is always present; icons reinforce, they never replace, the label.

## Test contract (drives the failing test written before implementation, per Constitution Principle I)

`Navigation.test.tsx` MUST assert:
1. All three main areas (Home, Standorte, Räume) are rendered as links to their respective routes.
2. When rendered at a given route (e.g. `/rooms/some-id`), the corresponding top-level item (Räume) is
   marked active and no other item is.
3. Activating a link updates the route (integration with `react-router-dom`'s router, consistent with
   existing routing tests in the codebase).

## Non-goals

- No authentication/role-based item visibility (Assumptions in spec.md).
- No collapsible/hamburger state (research.md Decision 2) — nothing to test for open/closed states.
