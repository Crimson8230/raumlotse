# Quickstart Validation Guide: Room Reservations

**Branch**: `004-room-reservations` | **Date**: 2026-09-19 | **Spec**: [spec.md](./spec.md) | **Contracts**: [reservations-api.yaml](./contracts/reservations-api.yaml)

This guide documents the step-by-step validation scenarios to verify room reservation capabilities end-to-end across backend and frontend services.

---

## Prerequisites

1. **Docker environment running** (PostgreSQL database):
   ```bash
   docker compose up -d db
   ```
2. **Backend running**:
   ```bash
   cd backend && ./mvnw spring-boot:run
   ```
3. **Frontend running** (in a separate terminal):
   ```bash
   cd frontend && npm run dev
   ```

---

## Automated Test Execution

Run the complete test suites to verify contracts, unit logic, and integration flows:

### Backend Unit & Integration Tests
```bash
cd backend
./mvnw test -Dtest="*Reservation*"
```
Expected outcome:
- Flyway migration `V4__create_reservation_tables.sql` applies successfully.
- WebMvc controller tests for `ReservationController` pass (201, 200, 400, 404, 409 conflict, including `/activate`, `/complete`, `/expire`, `/cancel`).
- `ReservationService` tests confirm conflict detection, capacity validation, and 5-state lifecycle persistence.
- `RoomDependentHistoryChecker` confirms deletion blocks when reservations exist.

### Frontend Component & Integration Tests
```bash
cd frontend
npm test
```
Expected outcome:
- Reservation form unit tests pass (input validation, capacity warnings, equipment filtering).
- Room view schedule display tests pass (correct status badges: `RESERVED`, `ACTIVE`, `COMPLETED`, `EXPIRED`, `CANCELLED`).
- Operational action buttons ("Activate", "Complete", "Expire", "Cancel") render and trigger correctly according to lifecycle state.

---

## Manual End-to-End Validation Scenarios

### Scenario 1: Book Room with Seating Arrangement & Additional Equipment
1. Open the web browser at `http://localhost:5173/rooms`.
2. Select an active room (e.g., "Room 101").
3. In the **Reservations** section, click **Book Room**.
4. Fill in the form:
   - **Start Time**: Tomorrow at 10:00 AM
   - **End Time**: Tomorrow at 11:30 AM
   - **Seating Arrangement**: Select "Theater" (Capacity: 40)
   - **Expected Attendees**: Enter `30`
   - **Additional Equipment**: Check "Microphone" (verify that built-in room equipment like "Projector" is not selectable here)
   - **Notes**: Enter `Department quarterly meeting`
   - **Booked By**: Enter `Jane Doe`
5. Click **Confirm Reservation**.
6. **Expected Outcome**: Reservation is created with status badge `RESERVED`. Creator and timestamp are visible.

### Scenario 2: Conflict Detection on Overlapping Booking Attempt
1. On the same room, click **Book Room** again.
2. Select an overlapping interval: Tomorrow from 10:30 AM to 12:00 PM.
3. Fill remaining required fields and click **Confirm Reservation**.
4. **Expected Outcome**: Submission is blocked. An error notification displays: `"Scheduling conflict: The room is already reserved during this time."` (HTTP 409).

### Scenario 3: Back-to-Back Booking Permitted
1. Click **Book Room** for Tomorrow from 11:30 AM to 12:30 PM (starts exactly when Scenario 1 ends).
2. Click **Confirm Reservation**.
3. **Expected Outcome**: Reservation succeeds immediately without conflict.

### Scenario 4: Capacity Limit Enforcement
1. Click **Book Room**. Select a seating arrangement with `maxCapacity = 40`.
2. Enter `45` expected attendees.
3. Click **Confirm Reservation**.
4. **Expected Outcome**: Form validation halts submission with `"Expected attendees (45) cannot exceed arrangement capacity (40)"` (HTTP 400).

### Scenario 5: Update Reservation Metadata
1. Locate the reservation from Scenario 1 (`RESERVED` status).
2. Click **Edit Details**. Change expected attendees to `35` and append `Bring spare batteries` to notes.
3. Click **Save Changes**.
4. **Expected Outcome**: Changes persist immediately with HTTP 200 without changing the scheduled time window.

### Scenario 6: Operational Lifecycle Transitions (Activate, Complete, Expire)
1. On a `RESERVED` booking, click **Check-In / Activate**:
   - **Expected Outcome**: Status updates to `ACTIVE` (HTTP 200 via `POST /api/reservations/{id}/activate`). Action buttons change to allow Check-Out or Cancel.
2. Click **Check-Out / Complete**:
   - **Expected Outcome**: Status updates to `COMPLETED` (HTTP 200 via `POST /api/reservations/{id}/complete`). Action buttons are disabled as this is a terminal state.
3. On another unattended `RESERVED` booking whose scheduled window elapsed, click **Expire / No-Show**:
   - **Expected Outcome**: Status updates to `EXPIRED` (HTTP 200 via `POST /api/reservations/{id}/expire`).

### Scenario 7: Cancel Reservation & Reclaim Slot
1. Locate an upcoming `RESERVED` or ongoing `ACTIVE` reservation.
2. Click **Cancel Reservation** and confirm prompt.
3. **Expected Outcome**: Status updates to `CANCELLED` (HTTP 200 via `POST /api/reservations/{id}/cancel`).
4. Now book the same room during that time window:
5. **Expected Outcome**: Booking succeeds immediately because cancelled reservations are ignored by conflict detection.

### Scenario 8: Protection Against Deactivating/Deleting Reserved Rooms
1. Create an upcoming reservation for a room.
2. Attempt to deactivate the room in the room settings.
3. **Expected Outcome**: Deactivation fails with an error: `"Room has active or upcoming reservations; cancel them first."` (HTTP 409).
4. Attempt to delete the room.
5. **Expected Outcome**: Deletion fails with `"Room has dependent history; deactivate it instead."` (HTTP 409).
