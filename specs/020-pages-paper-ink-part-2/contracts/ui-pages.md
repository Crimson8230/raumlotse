# UI contract: pages in Part 2

## Room detail (`/rooms/:roomId`)
- `main#inhalt.room-detail-page` > header (name serif, favorite, links) > grid:
  - area `mine`: `<section aria-labelledby>` "Meine Buchungen" (cards, own actions, empty text)
  - area `form`: `<section>` "Raum buchen" with `ReservationForm` (only active room + `RESERVE`)
  - area `day`: "Belegungen des Tages": `Strip` + `DataList` (time mono, state word)
  - area `overview`: "Raum auf einen Blick": `<dl>` Sitzordnungen / Ausstattung / Zugang
  - admin only: "Weitere Reservierungen" (management cards), "Check-in-Codes"
- Wide (≥ 64 rem): columns `[mine, day, overview] | [form]`. Narrow: order mine, form, day, overview, rest.
- Query params `start`, `end`, `book` prefill the form; no toggle button.

## Footer (all frames, login)
- `<footer>` after `main`: link "Hilfe" → `/hilfe`, identical text and position on all pages.

## Help (`/hilfe`, public)
- `SkipLink`, `main#inhalt`, `h1` "Hilfe", fixed sections, contact line from `HELP_CONTACT`.

## Composed lines
- `RoomName` → `<span class="room-name">`, `TimeText` → `<span class="mono">`; textContent unchanged.

## Lists
- `DataList` with `aria-label` and `emptyText` in catalogs, roles, occupancy.
