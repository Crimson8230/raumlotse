# UI Contract: Ranked Room Search Results

**Feature**: `017-room-search-ranking` | **Date**: 2026-10-12

Extends the UI contract of feature 008 (`specs/008-room-search-filter/contracts/ui.md`). Styling uses the existing design tokens (feature 002); no new colors.

## 1. Room overview "Räume" (`/rooms`) — result list

- The list shows rooms in the order returned by `GET /api/rooms/search`; the frontend MUST NOT re-sort.
- Above the list, when results are shown: note **"Sortiert nach Eignung"** (FR-012). Not shown in the empty state.
- On page load the frontend fetches `GET /api/me/favorite-rooms` once and keeps the entries in state.
- Each result row gets a favorite toggle (star button):
  - `aria-pressed` reflects the state; accessible name "Als Favorit markieren" / "Favorit entfernen".
  - Filled star = favorite (FR-008); outline star = not a favorite.
  - Click → optimistic toggle → `PUT` or `DELETE /api/me/favorite-rooms/{roomId}` → on success re-run the current search with the unchanged query string (R6); on error revert the star and show the standard error message.
- Search panel gains a checkbox **"Nur Favoriten"** → `favoritesOnly=true`, mirrored in the `/rooms` query string like the other filters and cleared by "Filter zurücksetzen" (FR-013).
- With "Nur Favoriten" active:
  - below the results, a section **"Nicht verfügbar"** lists favorites with `available = false` (building · floor · room name), no "Buchen" action, filled star that can be removed (FR-014); hidden if there are none.
  - if the user has no favorites at all: message **"Du hast noch keine Favoriten. Markiere Räume mit dem Stern, um sie hier zu sehen."** instead of the generic no-results message (FR-015).
- Marking an 11th favorite: the server answers 409 `FAVORITE_LIMIT_REACHED`; the star reverts and an inline message shows **"Höchstens 10 Favoriten – entferne zuerst einen anderen."** (FR-006a).
- The administrators' room list without an active search (incl. deactivated rooms) keeps its previous order and shows no favorite toggle for deactivated rooms.

## 2. Room detail view (`/rooms/:id`)

- Shows the same favorite toggle next to the room name for active rooms (FR-006), using the same favorite id state source (`GET /api/me/favorite-rooms`).

## 3. Equipment catalog (Stammdaten → Ausstattung, permission `EQUIPMENT_TYPE_MANAGE`)

- Create and edit forms gain a checkbox **"Spezialausstattung (wirkt sich auf die Sortierung aus)"** mapped to `specialised`; unchecked by default on create.
- The catalog list marks specialised types with a "Spezial" badge.
