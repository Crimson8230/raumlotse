# Quickstart & Validation Guide: User Reservation Integration

**Feature**: `009-user-reservations` | **Date**: 2026-09-28

Provides step-by-step instructions to validate the end-to-end integration of user accounts with room reservations, `reservedFor` metadata, and personal upcoming reservation views.

---

## 1. Prerequisites & Environment Setup

1. Start PostgreSQL and backend with local fixture credentials:
   ```bash
   make dev-db
   ```
2. Verify existing test fixtures (e.g., test user `admin@mci.edu` / `organizer@mci.edu` per `test-fixtures/login-emails.json`).
3. Ensure migrations are applied up to `V10`:
   ```bash
   cd backend && ./mvnw test-compile
   ```

---

## 2. Automated Test Verification

Execute backend and frontend test suites covering all acceptance criteria:

### Backend Test Suite
```bash
cd backend
./mvnw test -Dtest=*Reservation*Test*,*AuthControllerTest*
```
**Expected Outcome**:
- All tests pass.
- Tests prove that `POST /api/rooms/{id}/reservations` automatically records `createdBy = user.userId` and stores `reservedFor`.
- Tests prove `GET /api/reservations/my-upcoming` returns only the calling user's upcoming reservations, up to 10 entries ordered chronologically ascending.
- Tests prove `PATCH /api/reservations/{id}` accepts and validates updates to `reservedFor`.

### Frontend Test Suite
```bash
cd frontend
npm test
```
**Expected Outcome**:
- `ReservationForm.test.tsx` passes: proves `reservedFor` defaults to `displayName`, allows editing, rejects empty values, and omits legacy `createdBy` input.
- `HomePage.test.tsx` passes: proves unauthenticated visitors see no "My Upcoming Reservations" section; authenticated users see their upcoming bookings table with time, room link, and duration.
- `ReservationList.test.tsx` passes: proves `reservedFor` is rendered and editable on upcoming reservations.

---

## 3. Manual End-to-End Validation Scenarios

### Scenario A: Unauthenticated Visitor Experience
1. Open the application at `http://localhost:5173/` in an incognito browser window (logged out).
2. **Verify**:
   - The "Meine nächsten Reservierungen" / "My Upcoming Reservations" section is **not visible**.
   - No 401 errors are logged in console (no personalized API request was dispatched).

### Scenario B: Create Reservation as Authenticated User
1. Log in as an authenticated user (e.g. "Max Mustermann").
2. Navigate to a room detail page (e.g. `/rooms/<roomId>`).
3. Open the booking form:
   - **Verify**: The "Reserviert für" field is automatically filled with `"Max Mustermann"`.
4. Overwrite "Reserviert für" with `"Projektgruppe Web"` and submit valid times in the future.
5. **Verify**:
   - The reservation is created successfully.
   - On the room's reservation list, the entry displays:
     - `Reserviert für: Projektgruppe Web`
     - `Erstellt von: <User UUID>`

### Scenario C: Home Page "Meine nächsten Reservierungen" Dashboard
1. Navigate back to the home page (`/`).
2. **Verify**:
   - The section "Meine nächsten Reservierungen" is visible.
   - The table displays the newly created reservation:
     - **Zeitpunkt**: Correct date and time range.
     - **Raum**: Room name rendered as a link.
     - **Dauer**: Correctly formatted duration (e.g., `1 Std. 30 Min.` or `45 Min.`).
3. Click the room name link:
   - **Verify**: Browser navigates to `/rooms/<roomId>`.

### Scenario D: Editing `reservedFor` on Existing Booking
1. On the room detail page, locate the upcoming reservation in `RESERVED` status.
2. Click **Edit** (Bearbeiten):
   - **Verify**: The "Reserviert für" input contains `"Projektgruppe Web"`.
3. Clear the field and attempt to save:
   - **Verify**: Blocked with validation error (non-blank required).
4. Update to `"Projektgruppe Mobile"` and save:
   - **Verify**: Card updates to display `Reserviert für: Projektgruppe Mobile`.
