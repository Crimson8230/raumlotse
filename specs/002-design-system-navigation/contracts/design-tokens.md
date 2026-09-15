# Contract: Design Tokens

This is the contract every page and component in `frontend/src` MUST consume for visual styling instead
of hard-coding values (FR-003). It's expressed as CSS custom properties on `:root` in
`frontend/src/index.css`, extending the tokens already defined there (`--color-background`,
`--color-surface`, `--color-text`, `--color-muted`, `--color-border`, `--color-primary`).

## Token categories and naming

| Prefix | Category | Cardinality | Example names |
|---|---|---|---|
| `--color-accent-*` | accent | ≤ 3 total across the whole token set | `--color-accent` (primary), `--color-accent-2`, `--color-accent-3` (only if a genuine second/third emphasis need exists — do not add unless used) |
| `--color-success`, `--color-error`, `--color-warning`, `--color-info` | semantic | exactly one per meaning | fixed names, one each |
| `--color-background`, `--color-surface`, `--color-text`, `--color-muted`, `--color-border` | neutral | unlimited, but each name used consistently for its one purpose | existing names, reused as-is |
| `--font-*`, `--space-*`, `--radius-*` | typography/spacing | small fixed scale | `--space-1`…`--space-5`, `--font-size-body`, `--font-size-heading` |

## Rules a consumer (page/component) MUST follow

1. Never write a literal color (hex/rgb/hsl) in component or page CSS — reference a token.
2. Never introduce a new accent-category token without removing/reusing an existing one, so the total
   stays ≤ 3 (FR-004).
3. Use a semantic token only for its named meaning (e.g. `--color-error` only for error states) — never
   reuse it for decoration (FR-005, FR-006).
4. Any new color token added to this contract must be checked for WCAG 2.1 AA contrast (4.5:1 normal
   text / 3:1 large text) against the neutral background/surface it will realistically appear on
   (FR-011) before being added.
5. Visual embellishments (shadow, gradient, border-radius, animation) are opt-in per element and MUST be
   justified by a function (grouping, interactivity, state) in the PR that adds them (FR-006, FR-007) —
   not applied as a blanket default.

## Consumers

All existing pages (`HomePage`, `LocationCatalogPage`, `RoomListPage`, `RoomFormPage`) and components
(`Navigation`, `BuildingCatalog`, `EquipmentCatalog`, `RoomForm`) are consumers of this contract per
FR-008/FR-009.
