# Data Model: Design System & Navigation

This feature introduces no persisted or backend data. The "entities" below are frontend UI-configuration
concepts — the shared vocabulary the design system and navigation are built from — not database records.

## Design Token

Represents one named visual value that pages/components reference instead of hard-coding styles.

| Field | Type | Description |
|---|---|---|
| `name` | string (CSS custom property name, e.g. `--color-accent`) | Unique identifier used across all stylesheets |
| `category` | `accent` \| `semantic` \| `neutral` \| `typography` \| `spacing` | Governs the constraints that apply (see below) |
| `value` | CSS value (color, length, font stack) | The concrete value |
| `purpose` | string | Human-readable meaning, e.g. "error/danger state", "primary interactive emphasis" |

**Validation rules** (from FR-004/FR-005):
- At most 3 tokens may have `category = accent`.
- Every token with `category = semantic` MUST map to exactly one of a fixed set of meanings: `success`,
  `error`, `warning`, `info`. No two semantic tokens may share a meaning, and no semantic token may be
  used for pure decoration.
- `neutral` tokens (background/surface/text/border/muted) are unlimited in count but MUST NOT be used to
  create emphasis that competes with the accent tokens (FR-006).
- Every color token (accent, semantic, or neutral) MUST satisfy WCAG 2.1 AA contrast (FR-011) against
  the background/surface token(s) it is paired with in practice.

## Navigation Item

Represents one entry in the persistent primary navigation.

| Field | Type | Description |
|---|---|---|
| `label` | string | Visible text, e.g. "Home", "Standorte", "Räume" |
| `path` | string (route path) | Destination route, matches an existing entry in `App.tsx`'s `<Routes>` |
| `icon` | optional icon reference (from `lucide-react`) | Functional only — reinforces recognition/active state, never decorative-only |
| `isActive` | derived boolean | True when the current route matches `path` (via `NavLink`'s built-in matching, see research.md Decision 3) |

**Validation rules** (from FR-001/FR-002):
- The set of Navigation Items MUST cover exactly the app's current main areas: Home (`/`), Standorte
  (`/locations`), Räume (`/rooms`). Sub-routes (`/rooms/new`, `/rooms/:roomId`) are not separate nav
  items; they fall under the "Räume" item's active range.
- Exactly one Navigation Item is active at a time for any given route the app defines.

## Relationships

- Each Navigation Item's active/inactive visual state is expressed using Design Tokens (an accent token
  for "active", a neutral token for "inactive") — it does not introduce colors of its own.
- Existing feedback states already in the app (validation error, conflict warning, empty/loading state)
  each map to exactly one semantic Design Token; this mapping is what User Story 3 / FR-009 validates.

## State Transitions

None — both concepts are stateless/derived (Navigation Item active state is a pure function of the
current route; Design Tokens are static per theme). No state machine is introduced by this feature.
