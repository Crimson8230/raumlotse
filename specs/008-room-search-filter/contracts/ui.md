# UI Contract: Room Search with Filters

**Feature**: `008-room-search-filter` | **Date**: 2026-09-26

Defines the observable UI behavior and URL contracts the frontend must honor. Visual styling uses the existing design tokens (feature 002); no new colors.

## 1. Room overview "Räume" (`/rooms`) — search panel

Route unchanged. `RoomListPage` gains a `RoomSearchPanel` above the list.

### Controls

| Label (German UI) | Control | Maps to API parameter |
|---|---|---|
| Personen min. | number input, min 1 | `minPersons` |
| Personen max. | number input, min 1 | `maxPersons` |
| Gebäude | select: "Alle Gebäude" + active buildings | `buildingId` |
| Bestuhlung | select: "Alle" + names from `GET /api/rooms/search/seating-arrangements` | `seatingArrangement` |
| Ausstattung | one checkbox per **active** equipment type | `equipmentTypeId` (repeated) |
| Barrierefrei erreichbar | checkbox | `barrierFree=true` |
| Datum, Von, Bis | date input + two time inputs | `from`, `to` (converted to ISO instants in the browser time zone) |
| Suchen | submit button | runs the search |
| Filter zurücksetzen | button | clears all filters and URL parameters (FR-013) |

### URL state

Filters are mirrored in the `/rooms` query string so reload, back/forward, and shared links restore the search:

```text
/rooms?minPersons=20&maxPersons=50&buildingId=<uuid>&seatingArrangement=U-Shape
      &equipmentTypeId=<uuid>&equipmentTypeId=<uuid>&barrierFree=true
      &date=2026-10-05&startTime=11:00&endTime=12:00
```

`date`/`startTime`/`endTime` are kept in local form in the page URL; the API call converts them to `from`/`to` instants.

### Client-side validation (mirrors API, SC-004)

Shown inline in the design system's error style; no request is sent while invalid:
- person count not a whole number ≥ 1 → "Bitte eine ganze Zahl ab 1 eingeben."
- min > max → "Min. Personen darf nicht größer als Max. Personen sein."
- date/time partially filled → "Bitte Datum, Von und Bis vollständig angeben."
- end not after start → "Bis muss nach Von liegen."

Server-side `400` responses are shown with `formatApiError` as today.

### Status selector interplay

- Status "Active" (default): list shows `GET /api/rooms/search` results.
- Status "Deactivated" / "All": list shows the existing `GET /api/rooms?status=…` result; the search panel is disabled with the hint "Die Suche umfasst nur aktive Räume." Admin row actions (deactivate/reactivate/delete) behave as before in all modes.

### Result list (FR-011)

Each result row shows: room name (link), building, floor, seating arrangements with capacities, equipment names, and a "Barrierefrei erreichbar" marker when `barrierFreeReachable` is true. Equipment names are resolved from the equipment catalog (including deactivated types).

- **Link target**: `/rooms/{id}`; if the search has a complete date/time window: `/rooms/{id}?start=<ISO from>&end=<ISO to>` (§3).
- **"Buchen" action** (FR-011b, added 2026-09-28): a button-styled link with visible text "Buchen" and accessible name "`<room name>` buchen", only for `ACTIVE` rooms. Target: `/rooms/{id}?book=true`, plus `&start=<ISO from>&end=<ISO to>` when the search has a complete window.
- **Empty result** (FR-012): "Keine passenden Räume gefunden." plus a "Filter zurücksetzen" button.
- **Loading**: existing `status-loading` style.

## 2. Building catalog (`/locations`) — elevator and ground floor

`BuildingCatalog` component:
- Create building: checkbox "Aufzug vorhanden" next to the name (default off).
- Each building row: shows "Aufzug" / "kein Aufzug" and offers a toggle that saves via `PUT /api/buildings/{id}` with the current name and new `hasElevator`.
- Create floor: checkbox "Erdgeschoss (stufenloser Zugang)" (default off).
- Each floor row: shows the mark and offers a toggle that saves via `PUT /api/floors/{id}` with the current name and new `groundFloor`.

## 3. Room detail (`/rooms/:roomId`) — booking pre-fill (FR-011a)

URL contract:

```text
/rooms/{roomId}?start=2026-10-05T09:00:00Z&end=2026-10-05T10:00:00Z
```

- If both `start` and `end` are valid ISO instants, `end > start`, and the room is `ACTIVE`: the booking form opens automatically with Start/End pre-filled (converted to the browser's local `datetime-local` value) and editable.
- If `book=true` and the room is `ACTIVE`: the booking form opens automatically; Start/End are pre-filled only under the rule above, otherwise empty.
- Otherwise the parameters are ignored and the page behaves as before.
- Saving uses the unchanged reservation validation; conflicts or past starts produce the existing messages.
- The metadata panel additionally shows "Barrierefrei erreichbar: Ja/Nein".

## 4. Room form (`/rooms/new`, `/rooms/:roomId/edit`)

- New checkbox "Nicht barrierefrei (Ausnahme)" with helper text "Nur setzen, wenn der Raum trotz Erdgeschoss oder Aufzug nicht barrierefrei ist." Default off; sent as `notBarrierFree`.
