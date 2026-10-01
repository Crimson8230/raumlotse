# Quickstart: Room Status Visualization

## Prerequisites

- Node.js and npm installed for the frontend.
- Backend and PostgreSQL available through the project's normal Docker Compose setup when
  validating the full room display route.
- An existing room id with reservation fixtures or seeded data.

## Focused frontend validation

From the repository root:

```bash
cd frontend
npm ci
npm run test -- --run src/components/RoomDisplay/roomDisplayLogic.test.ts src/components/RoomDisplay/RoomDisplay.test.tsx src/pages/RoomDisplayPage.test.tsx
npm run lint
```

Expected result: all room-display tests pass and lint reports no errors.

## Manual scenarios

Open `/rooms/{roomId}/display` and verify:

1. With no current or future `RESERVED` reservation, the room shows green plus `Available`.
2. With a future `RESERVED` reservation, the room shows yellow plus `Reserved`.
3. With a current `ACTIVE` reservation, the room shows red plus `Reserved and Occupied`.
4. With a future `RESERVED` reservation, the display shows one readable line containing
   `Next Reservation`, `Reserved for:`, `Start Time`, and `End Time` together.
5. With multiple future reservations, the earliest `RESERVED` start time is shown; an
   `ACTIVE` record is never selected as the next reservation.
6. After advancing the clock or changing reservation state, the display updates on its normal
   refresh cadence.
7. If room/reservation loading fails, an unavailable message appears and the display does not
   show green or stale reservation details.
8. Confirm the colored status appears above the current date and time and that the complete next
   reservation line does not wrap, overlap, or hide any required value at the intended display size.

## Regression validation

From the repository root:

```bash
cd frontend
npm run test -- --run
npm run build
```

The existing backend contract and reservation test suites should also remain green when the
standard project validation workflow is run. No migration or backend endpoint change is
expected for this feature.
