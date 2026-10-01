# Quickstart Validation Guide: Expire Unclaimed Room Reservations

**Feature**: `007-expire-unclaimed-reservations`
**Date**: 2026-09-23

This guide provides end-to-end steps to validate the automated expiration of unclaimed room reservations.

---

## 1. Prerequisites

1. **Java 21** installed and configured (`java -version`).
2. **Docker & Docker Compose** running (for PostgreSQL container):
   ```bash
   docker compose up -d postgres
   ```
3. Application running locally:
   ```bash
   cd backend && ./mvnw spring-boot:run
   ```

---

## 2. Automated Test Execution

Validate all unit and integration tests implementing the feature:

```bash
cd backend
./mvnw test -Dtest=ReservationServiceTest,ReservationExpirationSchedulerTest,ReservationExpirationIntegrationTest
```

Expected output:
- `ReservationServiceTest`: verifies boundary timing (4m59s vs 5m00s), active exemption, and terminal state invariants.
- `ReservationExpirationSchedulerTest`: verifies periodic scheduling invocation.
- `ReservationExpirationIntegrationTest`: verifies end-to-end database persistence, log emission, and room release in conflict queries.

---

## 3. Manual / API Verification Workflow

### Scenario A: Unattended Reservation Auto-Expires

1. **Create an active room and a reservation** starting 6 minutes in the past (or create a booking and let 5 minutes elapse):
   ```bash
   curl -X POST http://localhost:8080/api/rooms/{roomId}/reservations \
     -H "Content-Type: application/json" \
     -d '{
       "seatingArrangementId": "{arrangementId}",
       "startTime": "2026-09-23T10:00:00Z",
       "endTime": "2026-09-23T11:00:00Z",
       "expectedAttendees": 5,
       "createdBy": "organizer@mci.edu"
     }'
   ```

2. **Trigger the operational expiration sweep** (or wait for the 30-second scheduler run):
   ```bash
   curl -X POST http://localhost:8080/api/reservations/expire-unattended
   ```
   **Expected Response (`200 OK`)**:
   ```json
   {
     "expiredCount": 1
   }
   ```

3. **Verify the reservation status is `EXPIRED`**:
   ```bash
   curl http://localhost:8080/api/rooms/{roomId}/reservations
   ```
   **Expected Response**: The reservation record contains `"status": "EXPIRED"`.

4. **Verify room is released for booking**:
   Attempt to create a new reservation for the same room during `10:00:00Z` to `11:00:00Z`.
   **Expected Outcome**: Succeeds (`201 Created`) with no 409 conflict error.

5. **Verify activation is rejected**:
   ```bash
   curl -X POST http://localhost:8080/api/reservations/{reservationId}/activate
   ```
   **Expected Outcome**: Rejection with `409 Conflict` ("Reservation cannot be activated from status: EXPIRED").

---

### Scenario B: Checked-In Reservation Is NOT Expired

1. Create a reservation starting at 10:00:00Z.
2. Check in / activate before the 5-minute threshold:
   ```bash
   curl -X POST http://localhost:8080/api/reservations/{reservationId}/activate
   ```
   **Expected Outcome**: Reservation status is `ACTIVE`.
3. Wait or trigger the expiration sweep:
   ```bash
   curl -X POST http://localhost:8080/api/reservations/expire-unattended
   ```
   **Expected Outcome**: `expiredCount` is `0`, and the reservation remains in `ACTIVE` status.

---

## 4. Observability Check

Inspect backend logs to verify structured log entries:

```bash
docker compose logs backend | grep "Auto-expired unattended reservation"
```

Expected log output:
```text
INFO  a.m.i.r.s.ReservationService - Auto-expired unattended reservation id=... roomId=... startTime=...
```
