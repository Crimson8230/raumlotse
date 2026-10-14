# UI Contract: Pages (feature 019)

Rules every page in scope MUST satisfy. Each rule is covered by a test (static scan or component test).

## Frame
1. `RequireAuth` and `LoginPage` render `<SkipLink targetId="inhalt" />` as the first focusable element.
2. Every rendered state of every page has exactly one `<main id="inhalt">` (including loading, error, forbidden).

## Room list (`/rooms`)
- `ul` of rows; each row (`li`) contains, in reading order: favorite toggle (state control), room number, room name (link, class `room-name`), building and floor, `Strip`, main action.
- Main action: "Buchen" (accessible name "{Raum} buchen", class `btn-primary`) if the user has `RESERVE` and the room is active; otherwise a link "Öffnen".
- Strip accessible name per 018 contract; `stripState` words: "lädt", "nicht verfügbar".
- Admin mode: `div[role=group][aria-label="Verwalten"]` with secondary buttons; no `btn-primary` inside.
- Empty states unchanged in text.

## Forms
- Fields via `Field`; required ones show "Pflichtfeld"; one hint line per form with required fields.
- Field-bound error: `ErrorMessage` connected via `aria-describedby`, input `aria-invalid`.
- Form-wide error: `ErrorMessage` at top of the form.
- Exactly one `btn-primary` per form (the submit).

## Errors from the API
- `formatApiError(err)`: `"{detail} ({Label}: {message}; …)"` — labels from `fieldLabel`, never technical names.
- `fieldErrorsOf(err)`: `Record<string,string>`; keys are technical names.

## Tables and lists
- No raw `<table>` outside `components/ui`; all tables via `DataTable` (caption, `scope`, optional `rowHeader`).
- Record lists via `DataList` with `emptyText`.

## Typography
- Room names: class `room-name` (serif). Dates, times, durations, counts in tables: `<time class="mono">` / class `mono`.

## Navigation
- At 1280 px viewport width in admin mode with all links: one row, no overlap; below: wraps without overlap.
