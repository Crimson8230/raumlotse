# Quickstart Validation Guide: Display Room Information

**Branch**: `005-display-room-information` | **Date**: 2026-09-20 | [spec.md](./spec.md) | [Contract](./contracts/room-display-view.md)

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

- Display component tests pass for current, same-day upcoming, next-day exclusion, ended, cancelled, missing-note, long-note-with-upcoming-line, no-reservation, loading, and unavailable states.
- The TypeScript build completes without errors.
- ESLint reports no violations.

## Manual Scenarios

All visual and usability checks use an 800 × 480 CSS-pixel viewport. Body text must remain at least 14 px.

### Scenario 1: Current reservation

1. Open the room display for an active room with a reservation whose interval contains the current time.
2. Verify the room name, current date/time, reservation note, `Booked by: <createdBy>`, start time, and end time are visible.
3. Verify no reservation-title or lecturer-name fields appear.

### Scenario 2: No current reservation

1. Use a room with no reservation covering the current time, or wait until a reservation ends.
2. Verify the room name and clock remain visible.
3. Verify an explicit no-current-reservation message appears and ended/future reservations are not shown.

### Scenario 3: Note fallback and truncation

1. Use a current reservation with an empty note and verify `No note provided` is shown.
2. Use a current reservation with a long note containing line breaks and special characters.
3. Verify the preview remains readable, does not overlap other fields, and includes an explicit truncation indicator when necessary.

### Scenario 4: Same-day upcoming reservation

1. Use a room with a current reservation and a later eligible reservation on the same local calendar day.
2. Verify the `Upcoming reservation` section is visible with the later reservation's start and end time on one line.
3. Repeat with no current reservation and verify the same upcoming section remains visible.
4. Use a long current note and verify the note area is reduced first and the incomplete note receives an explicit `...` marker.

### Scenario 5: Later-day reservation exclusion

1. Use a room with no later reservation today and a reservation tomorrow.
2. Verify no `Upcoming reservation` section is shown before the next local calendar day begins.

### Scenario 6: Booked-by display

1. Use a current reservation with `createdBy` set to `Albert Einstein`.
2. Verify the current reservation shows `Booked by: Albert Einstein`.
3. Verify the one-line upcoming section, when present, remains limited to its start and end times.

### Scenario 7: Clock freshness

1. Leave the display open for at least two minutes.
2. Verify the displayed time advances and remains within 60 seconds of the current time.

### Scenario 8: Unavailable data

1. Simulate a failed room or reservation request.
2. Verify the display shows a clear unavailable state and does not retain stale reservation data.

### Scenario 9: Five-second usability check

1. Conduct at least 10 manual viewer trials at the target viewport size.
2. For each trial, show the display and record whether the viewer identifies the room and determines whether a current reservation exists within 5 seconds.
3. Verify that at least 9 of 10 trials succeed, or at least 90% when more than 10 trials are conducted.
