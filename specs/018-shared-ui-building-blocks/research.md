# Research: Shared UI Building Blocks

No NEEDS CLARIFICATION remained after the spec clarification session. Decisions:

## R1 – Strip accessible name
- **Decision**: Container `role="img"` with one `aria-label` built from the windows in time order, e.g. "Heute 08:00–10:00 belegt, 10:00–12:00 frei, 12:00–18:00 gesperrt. Jetzt: 09:15, belegt." Visual segments are `aria-hidden`; a visible legend/word per segment is optional but the word is always in the label.
- **Rationale**: SC-002 requires the whole day from one name. Per-segment focus would force many tab stops.
- **Alternatives**: `<ul>` of segments (verbose, many nodes); `aria-describedby` to a hidden list (two sources of truth).
- **Bound**: merge adjacent windows of equal state and cap at 12 spoken windows, then "und N weitere Zeitfenster" (edge case: very many windows).

## R2 – Strip geometry
- **Decision**: Segment positions as percentages of the range (`left`/`width`), clamped to the range; zero-length windows are dropped from visual and name; overlapping windows resolved by priority gesperrt > belegt > meine > frei (the stricter state wins), "now" marker as a separate absolutely positioned element.
- **Rationale**: No measurement, deterministic, testable in jsdom.

## R3 – Patterns
- **Decision**: frei = empty (surface), belegt = `--hatch` token, meine = full ink with inverted text, gesperrt = dotted (radial-gradient built from `--color-text`), jetzt = 2 px `--color-primary` line plus word. Tones `--color-free` / `--color-busy` only as faint support.
- **Rationale**: Matches the design canvas; patterns survive grayscale and forced-colors (`forced-color-adjust: none` as in the existing `.strip`, with text always present).
- **Alternatives**: color only (rejected by SC-001).

## R4 – Field association
- **Decision**: `Field` generates ids with `useId` and passes `{ id, 'aria-describedby', 'aria-invalid', 'aria-required' }` to its child through a render prop `children(controlProps)`. `aria-describedby` lists error first, then hint (edge case).
- **Rationale**: Works with `<input>`, `<select>`, `<textarea>` and native date/time inputs without cloning elements; strictly typed.
- **Alternatives**: `cloneElement` (fragile with custom components); wrapping `<label>` (breaks hint layout).

## R5 – Required marker
- **Decision**: Visible text "Pflichtfeld" (small) next to the label plus `aria-required`; a bare "*" is never used alone. Forms get the optional note "Alle mit Pflichtfeld markierten Angaben sind erforderlich" by the page (out of scope here).

## R6 – Error announcement
- **Decision**: `ErrorMessage` renders `role="alert"` with symbol (`aria-hidden`), visible "Fehler:" and text. Field errors use the same component, linked via `aria-describedby`.
- **Rationale**: `role="alert"` announces on insertion (FR-005); one pattern everywhere.
- **Caveat**: A live region only announces content added after it exists; tests mount the error after initial render and also assert the static association.

## R7 – Field-label mapping
- **Decision**: `fieldLabels.ts` exports `defaultFieldLabels` (e.g. `startTime` → "Beginn", `endTime` → "Ende", `roomId` → "Raum", `name` → "Name") and `fieldLabel(name, overrides?)`, returning the label or a humanized fallback. Pages extend by passing `overrides`.
- **Rationale**: FR-006 "single mapping that pages can extend" without a global mutable registry.
- **Note**: The default entries are seeded from the field names the backend validation currently reports; the page migration extends them.

## R8 – Skip link and target contract
- **Decision**: `SkipLink({ targetId = 'inhalt', children = 'Zum Inhalt springen' })` renders an anchor; on activation it focuses the element with that id (adding `tabindex="-1"` if absent) so focus really moves in all browsers. Contract documented: the page's `<main>` carries `id="inhalt"`. Block is not mounted by any existing page.
- **Rationale**: Clarification 2026-10-14 (B). The block CSS uses `.ui-skip-link`, not the legacy `.skip-link`.

## R9 – Table on narrow screens
- **Decision**: `DataTable` sits in a wrapper with `overflow-x: auto`, `role="region"`, an `aria-label` from the caption, and `tabIndex=0` so keyboard users can scroll; the page itself does not scroll horizontally.
- **Alternatives**: stacked "card" layout via CSS (loses table semantics when `display` is changed); rejected.

## R10 – Verifying contrast, color and size rules
- **Decision**: `ui.rules.test.ts` (a) parses token hex values from `index.css` and asserts every text/background pair used by the blocks (listed in the test) ≥ 7:1 with a WCAG luminance helper, (b) fails if any block CSS contains a hex/rgb/hsl literal, (c) checks that interactive block selectors set `min-height`/`min-width` ≥ 44px via the shared class.
- **Rationale**: jsdom does not compute layout; static checks make FR-011/FR-012 testable. Browser review of focus and grayscale covers the remaining visual part (spec assumption).

## R11 – Reduce motion and forced colors
- **Decision**: Blocks have no animation. Pattern and word are independent of colors, so forced-colors mode keeps all states distinguishable; the `Jetzt` marker has a word in the accessible name and a visible label.

## R12 – Chip states
- **Decision**: Chip variants = the vocabulary states plus a plain `neutral`. Each shows its word (default from vocabulary, overridable text) and a leading shape/pattern swatch (empty, hatch, solid, line, dotted). Interactive chips render as `<button>` (≥ 44 px) with focus ring; static chips render as `<span>`.

## R13 – Wayfinding
- **Decision**: `Wayfinding({ direction, target, distance?, onClick|href? })`; direction ∈ `geradeaus | links | rechts | hoch | runter`. Visible text "Aufzug · hoch" style assembled from words; lucide icon `aria-hidden`. Distance/time is passed as already-formatted text.
