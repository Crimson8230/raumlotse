# Quickstart: Room Map Placement validation

Prerequisites: `docker compose up` (backend, frontend, PostgreSQL); an admin and a non-admin account; a building with two floors, each with ≥1 room; two small PNG floor plans (e.g. 1200×800).

## Automated
```bash
cd backend && ./mvnw test          # includes V13 migration, service and API tests
cd frontend && npm run lint && npm test && npm run build
```

## Manual scenarios (as admin unless noted)
1. **Upload** — `/maps` → floor 0 → upload plan. Map is shown; non-PNG/JPEG or >10 MB is rejected with a message and the map is unchanged. (US3, FR-009)
2. **Place** — pick a room from the unplaced list, click on the plan → marker with room name; reload → same position. Drag to move; remove → room returns to unplaced list. (US1/US2, SC-001/002/003)
3. **Responsive** — resize window / zoom: markers stay on the same spot of the plan. (FR-010)
4. **Wrong floor** — `PUT /api/maps/{map0}/placements/{roomOfFloor1}` → 422. Coordinates 1.2 → 400. (FR-012)
5. **Lifecycle** — rename a placed room → label updates; delete a room → marker gone; delete a map → rooms unplaced, connection points removed. (US4, SC-005/007)
6. **Connections** — create elevator "A", add point on map 0 and map 1; each map shows the other reachable map; add a third map; remove one point; connection with 1 point shows "incomplete". (US5, SC-006)
7. **Authorization** — as non-admin: map visible, no edit controls, `PUT` returns 403; logged out: 401. (Display-Einmalcode-Login ist nicht Teil dieses Features.) (FR-008)
8. **Image caching** — second `GET /api/maps/{id}/image` with `If-None-Match` returns 304.

## Usability-Abnahme (manuell, zeitgemessen)
- SC-001: Raum platzieren (Karte öffnen → gespeichert) in < 30 s.
- SC-003: Alle unplatzierten Räume in < 5 s erkennen.
- SC-006: Karte anlegen und bestehende Verbindung auf zwei Stockwerke ausdehnen in < 3 min.

Contract: [contracts/room-map-api.yaml](./contracts/room-map-api.yaml) · Model: [data-model.md](./data-model.md)

## Verifikation (Stand der Implementierung)

- Backend `./mvnw test`: 476 Tests, 0 Fehler (inkl. Migration V13, Rollen-Gate, Platzierung, Verbindungen, Lebenszyklus, Performance, Logging-Datenschutz).
- Frontend `npm run lint`, `npx tsc -b`, `npm test` (281 Tests) und `npm run build`: ohne Fehler.
- Die manuellen Browser-Szenarien 1–8 und die Usability-Abnahme oben wurden **noch nicht** im laufenden Compose-Stack durchgespielt; sie entsprechen den automatisierten API-/Komponententests, ersetzen sie aber nicht.
- Fixture-Hinweis: Alle Integrationstests teilen einen Postgres-Container. `MapIntegrationSupport` räumt deshalb zuerst `booking_confirmation` und `reservation` ab, bevor Räume gelöscht werden.
