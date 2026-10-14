# UI Contract: Shared Building Blocks

**Feature**: `018-shared-ui-building-blocks` | **Date**: 2026-10-14

Public API of `frontend/src/components/ui/` (import from the barrel `components/ui`). All text is German. All blocks: tokens only, 44 px interactive targets, visible focus, no outer margins (layout is the page's job), pass-through of `className`.

## Strip
`<Strip name? rangeStart rangeEnd windows now? />`
- Renders `role="img"` with `aria-label`: `"{name}: {HH:MM–HH:MM state, …}. Jetzt: {HH:MM}, {state}."` in time order; no windows → `"… ganztägig frei."`; marker omitted outside range.
- Visual: empty / hatched / full ink / dotted segments; "Jetzt" marker 2 px primary line with visible label "Jetzt"; times in mono.
- `StatusDot({ state })`: small dot (pattern + sr-only word) for list rows.

## Chip
`<Chip state | tone="neutral" children? onClick? />`
- Always contains a word (default from vocabulary). Pattern swatch + word + tone. `onClick` → `<button>`, else `<span>`.

## Field
`<Field label required? hint? error? >{(control) => <input {...control} />}</Field>`
- `control`: `{ id, 'aria-describedby'?, 'aria-invalid'?, 'aria-required'? }`.
- Label visible and bound; required shows the word "Pflichtfeld"; `aria-describedby` = error id then hint id; only existing parts are referenced.

## ErrorMessage
`<ErrorMessage id? field? >{text}</ErrorMessage>`
- `role="alert"`; content = symbol (decorative) + "Fehler:" + text; `field` is optional context shown as label ("Beginn: …") using `fieldLabel`.

## fieldLabel
`fieldLabel(name: string, overrides?: Record<string,string>) => string`

## SkipLink
`<SkipLink targetId="inhalt" />`
- Hidden until focused; activation moves focus to `#targetId` (sets `tabindex="-1"` if missing). Contract for pages: first focusable element; `<main id="inhalt">`.

## DataList
`<DataList items renderItem emptyText aria-label? />`
- `<ul>` with `<li>` per item; empty → `<p>` with `emptyText` (worded, not an empty list).

## DataTable
`<DataTable caption columns rows rowKey rowHeader? emptyText />`
- `caption` required; header cells `scope="col"`; `rowHeader` column key renders `<th scope="row">`; wrapper scrolls horizontally on narrow screens; empty → worded row/message.

## Wayfinding
`<Wayfinding direction target distance? onClick? href? />`
- Naming: code name `Wayfinding`; "Wegweiser" only in German UI text and docs.
- Text "{target} · {direction}{ · distance}" visible; icon decorative; interactive when `onClick`/`href` given.

## Accessibility invariants (tested for every block)
1. Every state/meaning available as text. 2. Interactive controls reachable and operable by keyboard. 3. No fixed color literals in block CSS. 4. Optional parts absent → no dangling `aria-*` references.
