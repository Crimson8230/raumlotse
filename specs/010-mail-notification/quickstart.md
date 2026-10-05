# Quickstart and Validation Guide: Optional Booking Confirmation Email

**Feature**: `010-mail-notification` | **Date**: 2026-10-05

This guide validates the default-off booking-form choice, request compatibility, conditional transactional enqueueing, mail content, duplicate prevention, failure isolation, privacy-safe diagnostics, and local SMTP delivery. It assumes the implementation tasks generated after this plan have been completed.

## 1. Prerequisites

- Java 21 and the included Maven wrapper
- Node.js/npm for frontend checks and browser validation
- Docker with Docker Compose
- A local authenticated fixture account configured as described in the project README

Do not place production SMTP credentials in tracked files. Local Compose uses Mailpit without external delivery.

## 2. Automated Frontend Verification

Run the focused reservation-form and schema tests:

```bash
cd frontend
npm test -- ReservationForm.test.tsx reservationFormSchema.test.ts
```

Expected results:

- `Email Notification` appears before `Confirm Reservation` and is accessible by its label.
- The checkbox is unchecked whenever a new form is mounted.
- Submitting without interaction sends `emailNotification: false` and does not block an otherwise valid booking.
- Selecting and deselecting the checkbox sends the final visible boolean value.
- Opening a later form does not inherit the previous selection.
- The Zod schema accepts complete valid payloads with `emailNotification` set to either `true` or `false`.
- Invalid or manipulated reservation values, including non-UUID seating/equipment identifiers and non-boolean notification values, are rejected before any API call and use the existing user-facing form error path.

## 3. Automated Backend Verification

Run focused tests first:

```bash
cd backend
./mvnw -Dtest='BookingConfirmation*Test,ReservationCreationIntegrationTest,ReservationControllerTest' test
```

Expected results:

- A successful authenticated create with `emailNotification: true` commits one `PENDING` confirmation.
- A successful create with `emailNotification: false` or with the property omitted commits the reservation and no confirmation.
- Rejected and rolled-back reservations commit no confirmation regardless of the request value.
- The unique reservation constraint and concurrent claims prevent duplicate logical confirmations and SMTP attempts.
- The message contains the room plus complete start/end values in `Europe/Berlin`, including cross-day and daylight-saving cases.
- A missing account/email or gateway failure produces `FAILED` while the opted-in reservation remains `RESERVED`.
- Tests prove logs and persisted failure metadata contain no email address, message body, booking time, credentials, or raw exception text.
- Page reloads, reservation reads, updates, cancellations, activation, completion, and expiration do not enqueue mail.

Then run the complete backend suite:

```bash
cd backend
./mvnw test
```

Expected result: all unit and PostgreSQL Testcontainers integration tests pass with no failures or skipped confirmation tests.

## 4. Configuration Validation

Verify application configuration supports environment-backed values for:

```text
MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
MAIL_SMTP_AUTH
MAIL_SMTP_STARTTLS
```

Connection, read, and write timeouts must be finite. `MAIL_PASSWORD` and real credentials must not appear in `application.yaml`, `docker-compose.yml`, `.env.example`, logs, or committed test resources.

## 5. Local End-to-End Delivery with Mailpit

Start PostgreSQL, Mailpit, and the backend:

```bash
docker compose up --build db mailpit backend
```

Start the frontend separately:

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
Frontend:   http://localhost:5173
Mailpit UI: http://localhost:8025
Backend:    http://localhost:8080
```

### Scenario A — Default Opt-Out

1. Sign in with the local fixture account and open a room's booking form.
2. Verify `Email Notification` is visible before `Confirm Reservation` and is unchecked.
3. Create a valid reservation without selecting it.
4. Verify the reservation succeeds using the existing `201` response.
5. Verify Mailpit receives no message and the reservation has no confirmation row.
6. Open a new booking form and verify the checkbox is unchecked again.

### Scenario B — Explicit Opt-In

1. Select `Email Notification` and create a valid future reservation.
2. Confirm the API returns the existing `201` reservation response without a mail-status field.
3. In Mailpit, verify exactly one message is accepted within two minutes.
4. Verify recipient equals the fixture account email.
5. Verify subject is `Raumlotse: Buchung bestätigt – <room name>`.
6. Verify the plain-text body contains only the room and complete start/end date-times marked `Europe/Berlin`.
7. Reload the room and home pages repeatedly; verify no second message appears.

### Scenario C — Final Checkbox State

1. Select and then deselect `Email Notification` before submitting a valid booking; verify no confirmation row or message is created.
2. Open a fresh form, leave the checkbox initially off, then select it immediately before submitting; verify exactly one confirmation is created and accepted.

### Scenario D — Rejected Booking

1. Select `Email Notification` and submit an overlapping booking or invalid time range.
2. Verify the request is rejected using the existing error contract.
3. Verify Mailpit receives no new message and no confirmation row exists for the rejected attempt.

### Scenario E — SMTP Failure Isolation

1. Stop Mailpit while leaving PostgreSQL and the backend running.
2. Select `Email Notification` and create another valid reservation.
3. Verify the reservation remains present in `RESERVED` state.
4. After the bounded SMTP timeout, verify its confirmation reaches `FAILED` with `SMTP_REJECTED`.
5. Verify backend logs contain a fixed failure event and identifiers only—not recipient email, room name, times, message body, or raw exception text.
6. Restart Mailpit and verify the failed confirmation is not automatically submitted, preserving the at-most-once policy.

### Scenario F — Mixed Distinct Reservations

1. Create three non-conflicting reservations as the same user: first opted in, second opted out, third opted in.
2. Verify all three reservations succeed.
3. Verify Mailpit receives exactly two messages, one for each opted-in reservation.
4. Verify each message contains its own room and time window.

### Scenario G — SMTP Acceptance Performance

1. Create 100 valid reservations with `Email Notification` selected and no more than 20 requests running concurrently.
2. Record each successful booking-completion timestamp and the corresponding confirmation acceptance timestamp.
3. Verify at least 95 confirmations are accepted within 120 seconds of their corresponding booking completion.
4. Record the total count, concurrent-request limit, per-confirmation elapsed times, successes, and failures.

### Scenario H — User Comprehension

1. Recruit at least 10 representative users and show each the default booking form without explaining the checkbox.
2. Verify at least 9 can explain within 15 seconds that email is sent only when `Email Notification` is selected.
3. For opted-in confirmations, verify at least 9 can identify room, start, and end within 30 seconds without additional explanation.
4. Record only anonymized result and duration data.

## 6. Database Inspection

Use pgAdmin or a parameterized/read-only SQL client to inspect queue metadata:

```sql
SELECT reservation_id, status, attempt_count,
       created_at, processing_started_at, sent_at, failed_at, failure_code
FROM booking_confirmation
ORDER BY created_at DESC;
```

Expected invariants:

- No row exists for an opted-out reservation.
- Exactly one row exists for each successfully committed opted-in reservation.
- `attempt_count` is never greater than `1`.
- `SENT` rows have `sent_at`; `FAILED` rows have `failed_at` and a fixed `failure_code`.
- The table contains no opt-in boolean, recipient email, or rendered subject/body columns.

## 7. Quality Gates

Before merge, verify:

```bash
cd backend
./mvnw test
```

```bash
cd frontend
npm test
npm run lint
npm run build
```

Confirm `/api/health` still reports healthy operation and `docker compose logs backend` provides greppable confirmation outcomes without personal data. Record commands, test counts, manual scenario evidence, and results in this guide during implementation.

## 8. Implementation Validation Record

Automated validation executed on 2026-10-05:

- Focused frontend: `npm test -- ReservationForm.test.tsx reservationFormSchema.test.ts` — 2 files, 23 tests passed.
- Focused backend: `./mvnw "-Dtest=BookingConfirmation*Test,ReservationCreationIntegrationTest,ReservationControllerTest" test` — 45 tests passed, 0 failures, 0 errors, 0 skipped.
- Full backend: `./mvnw test` with PostgreSQL Testcontainers — 382 tests passed, 0 failures, 0 errors, 0 skipped.
- Full frontend: `npm test` — 32 files, 226 tests passed; `npm run lint` passed; `npm run build` passed.
- Contract: `contracts/reservation-confirmation.yaml` parsed successfully as YAML.
- Health: the complete backend suite passed `SecurityPolicyTest` and `ProtectedBusinessRouteTest`, including public `/api/health` returning HTTP 200.
- Privacy: tracked mail configuration contains only environment-backed `MAIL_USERNAME`/`MAIL_PASSWORD` placeholders; worker logs contain fixed event names, UUIDs, counts, and allowlisted failure codes only. No recipient, room, booking times, message body, credentials, or raw SMTP exception text is logged.
- Constitution I–V: PASS; test-first coverage, typed request/form validation, unchanged response contract, environment-backed credentials/privacy-safe logs, and the minimal gateway plus durable queue design are covered by the automated checks above.

Manual acceptance remains to be executed against a running browser/Mailpit environment: scenarios A–H (tasks T033–T035). The architectural rationale still needs to be copied into the introducing pull request description once that pull request exists (T037).
