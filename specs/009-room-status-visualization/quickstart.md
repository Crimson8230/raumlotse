# Quickstart: Room Status Visualization

## Prerequisites

- Node.js and npm installed.
- Existing frontend dependencies installed with `npm ci` from `frontend/`.
- A room display route and reservation fixtures or mocked API responses.

## Automated validation

From the repository root:

```bash
cd frontend
npm run test -- --run src/components/RoomDisplay/roomDisplayLogic.test.ts src/components/RoomDisplay/RoomDisplay.test.tsx src/pages/RoomDisplayPage.test.tsx
npm run lint
npm run build
```

Expected result: all targeted status, presentation, refresh, lint, and build checks pass.

## Required scenarios

1. No current or future reservation: display `Verfügbar` in green.
2. Future `RESERVED` reservation before start: display still `Verfügbar` in green and show it only as the next reservation.
3. Current `RESERVED` reservation without check-in: display `Reserviert` in yellow.
4. Current `ACTIVE` reservation after check-in: display `Belegt` in red.
5. At the reservation end boundary: the ended reservation is not current because the interval is `[startTime, endTime)`.
6. Cancelled, expired, completed, and wrong-room records do not determine status or next reservation.
7. After a 30-second refresh, a future reservation transitioning to current `ACTIVE` changes from `Verfügbar` to `Belegt`.
8. Failed room/reservation loading shows unavailable data and never green.
9. Status appears above the current date/time and next-reservation content remains readable in the existing one-line layout.
