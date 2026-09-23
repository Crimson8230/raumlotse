# Quickstart Validation Guide: Display Room Information

**Branch**: `005-display-room-information` | **Date**: 2026-09-21 | [spec.md](./spec.md) | [Contract](./contracts/room-display-view.md)

## Prerequisites

1. Start the PostgreSQL service and application environment:

   ```bash
   docker compose up -d db
   ```

2. Start the backend in one terminal:

   ```bash
   cd backend && ./mvnw spring-boot:run
   ```

3. Start the frontend in another terminal:

   ```bash
   cd frontend && npm run dev
   ```

## Automated Validation

Run the frontend checks:

```bash
cd frontend
npm test
npm run build
npm run lint
```

Expected outcomes:

- Display component and page tests pass for current, ended, future, terminal, wrong-room, missing-note, no-reservation, loading, unavailable, and room-detail navigation states.
- The TypeScript build completes without errors.
- ESLint reports no violations.

## Manual Scenarios

All visual and usability checks use an 800 × 480 CSS-pixel viewport. Body text must remain at least 14 px.

### Scenario 1: Navigate from room management

1. Open a specific room's detail view.
2. Verify `Display Room Information` appears directly beside `Edit Room`.
3. Activate it and verify the display opens for the same room.

### Scenario 2: Current reservation

1. Open the room display for an active room with a reservation whose interval contains the current time.
2. Verify the room name, current date/time, `Booked by`, `Note`, `Start time`, and `End time` are visible with distinct labels.

### Scenario 3: No current reservation

1. Use a room with no reservation covering the current time, or wait until a reservation ends.
2. Verify the room name and clock remain visible.
3. Verify an explicit no-current-reservation message appears and ended/future reservations are not shown.

### Scenario 4: Missing note and special characters

1. Use a current reservation with an empty note and verify `No note provided` is shown.
2. Use a current reservation with a note and creator name containing spaces and special characters.
3. Verify both values remain complete and readable.

### Scenario 5: Long values

1. Use long room, note, and creator values.
2. Verify values wrap or use another visible treatment without clipping or overlap.

### Scenario 6: Clock freshness

1. Leave the display open for at least two minutes.
2. Verify the displayed time advances and remains within 60 seconds of the current time.

### Scenario 7: Unavailable data

1. Simulate a failed room or reservation request.
2. Verify the display shows a clear unavailable state and does not retain stale reservation data.

### Scenario 8: Five-second usability check

1. Conduct at least 10 manual viewer trials at the target viewport size.
2. For each trial, show the display and record whether the viewer identifies the room and determines whether a current reservation exists within 5 seconds.
3. Verify that at least 9 of 10 trials succeed, or at least 90% when more than 10 trials are conducted.
