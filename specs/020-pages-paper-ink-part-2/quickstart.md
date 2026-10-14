# Quickstart: validating Part 2

Prerequisites: `cd frontend && npm ci`; backend and database running for browser checks (`docker compose up`).

1. **Tests**: `npm test` and `npm run lint` pass (includes rule tests and the axe suite).
2. **Room detail**: sign in as lecturer, open a room with own and foreign bookings today. Check: left column order mine → day → overview, form on the right at 1280 px, stacked order at 360 px, "keine Angabe" for a room without equipment.
3. **Manager**: administration mode with `OTHER_RESERVATION_MANAGE`: "Weitere Reservierungen" shows details and actions.
4. **Typography**: occupancy page lines: room name serif, times mono (DevTools computed font).
5. **Lists**: each catalog and the role page with and without records.
6. **Footer/help**: "Hilfe" visible on login and in the app at the same place; `/hilfe` reachable by keyboard.
7. **Navigation 1280 px / reduced motion**: record the result in `checklists/visual-review.md` and `docs/DESIGN-UMSTELLUNG.md`.
