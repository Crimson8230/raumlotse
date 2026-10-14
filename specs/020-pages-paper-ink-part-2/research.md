# Research: Pages in "Papier & Tinte", Part 2

## R1 – What is "Zugang" in "Raum auf einen Blick"?
**Decision**: Show the access facts already in the room data: "Barrierefrei erreichbar" (`barrierFreeReachable`), "Aufzug im Gebäude" (`building.hasElevator`), "Ebene ohne Stufen" (`floor.groundFloor`), and "Nicht barrierefrei (Verwaltung)" is *not* shown separately because it is folded into the first value.
**Rationale**: No access-note field exists; adding one is a backend change and out of scope.
**Alternatives**: free-text note (needs backend + migration, layer 4); omit "Zugang" (deviates from the board).

## R2 – "Meine Buchungen" and "Belegungen des Tages" without a new query
**Decision**: Both use the existing `listRoomReservations(roomId)` result. "Meine Buchungen" = entries with `ownedByMe` (full cards with today's actions, reuse `ReservationList` with a `scope="mine"` prop). "Belegungen des Tages" = new read-only `RoomDayOccupancy`: a `Strip` for today plus a `DataList` of today's windows (time in mono, state word, "meine" chip for own). Users with `OTHER_RESERVATION_MANAGE` in administration mode keep today's management cards in an extra section "Weitere Reservierungen" so no behavior is lost.
**Rationale**: One request, no backend change; management actions stay reachable.
**Alternatives**: separate day query (extra request, no gain); put actions into day lines (clutters the compact list).

## R3 – Booking form position and toggle
**Decision**: Right column, always visible when the user may reserve and the room is active; the "Raum buchen"/"Buchungsformular schließen" toggle is removed. Query parameters `start`, `end`, `book` keep prefilling and now only prefill.
**Rationale**: Board shows the form permanently; a toggle hides the main action. Behavior (what can be booked) is unchanged.
**Alternatives**: keep toggle (diverges from board, extra click).

## R4 – Layout technique
**Decision**: CSS grid with `grid-template-columns: minmax(0,1fr) minmax(20rem,26rem)` at ≥ 64 rem; below that a single column ordered mine → form → day → overview via grid areas. No JS breakpoints.
**Rationale**: Tokens and plain CSS match the existing code; reading order stays logical.

## R5 – Mono/serif in composed lines
**Decision**: A tiny `TimeText` / `RoomName` pair of inline components (`<span class="mono">`, `<span class="room-name">`) used by `bookingLine`/`runningLine` and group headings, replacing string builders with fragments. Text content and order stay identical.
**Alternatives**: CSS-only (cannot style parts of a string).

## R6 – Lists on `DataList`
**Decision**: Convert `BuildingCatalog` (2 lists), `EquipmentCatalog`, `ConnectionCatalog`, `UserRoleListPage` (`role-users`), occupancy lists, and role lists. Leave `UnplacedRoomList`, `RoomDeviceIcons`, `Navigation` (own semantics). Panel layout of catalogs may change (spec 019 note); keep their buttons and confirmations.
**Rationale**: Spec FR-007; list semantics are already `ul`, so risk is layout only.

## R7 – Footer and help page
**Decision**: New `Footer` component (shared frame, also on login) with link "Hilfe" to `/hilfe`. `/hilfe` is a public route with `SkipLink`, one `main#inhalt` and fixed German text; contact address is a constant `HELP_CONTACT` in one file, placeholder to be set by the project.
**Rationale**: WCAG 3.2.6 needs the same position on every page; public so it is reachable from login.
**Alternatives**: link inside navigation (not "footer", different position per mode).

## R8 – C2: small capitals
**Decision**: Only `.status-badge` still uses `text-transform: uppercase` with `letter-spacing: 0.04em`. Drop both; keep 14 px and weight 700; the pattern marker already carries the distinction. A rule test forbids `uppercase` with `letter-spacing` below 14 px.
**Rationale**: Grep over all CSS shows no other case.

## R9 – Automated accessibility check
**Decision**: Add `axe-core` as a devDependency and a Vitest suite that renders each page in scope in jsdom and runs axe (label, list, table, landmark rules; colour-contrast is disabled in jsdom). Contrast, 1280 px navigation and `prefers-reduced-motion` are verified in the browser with the visual-review checklist and recorded.
**Rationale**: jsdom has no layout, so contrast and wrapping need a real browser; axe-core is the standard engine and keeps the suite self-contained.
**Alternatives**: Playwright (heavier new tool, constitution V); manual only (no regression guard).

## R10 – Navigation at 1280 px
**Decision**: Verify in the browser; if ten links plus indicator do not fit, keep the controlled wrap from 019 and document. No change planned unless overlap is found.
