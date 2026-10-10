# Quickstart: On-Site Presence Check-In

Validation guide for `014-presence-checkin`. API shapes: [contracts/presence-checkin-api.yaml](./contracts/presence-checkin-api.yaml). States and rules: [data-model.md](./data-model.md).

## Prerequisites

- Local stack running as described in the root `README.md` (`docker compose up`, backend, frontend).
- Two accounts: a regular user **U** and an administrator **A** (the `local-auth-fixture` account is an admin).
- One active room **R**.
- A phone on the same network as the frontend, for the real QR scan (optional; opening the link in the browser works too).

## Automated checks

```bash
cd backend && ./mvnw test        # includes new check-in, automation, status and presence tests
cd frontend && npm test && npm run lint
```

## Scenario 1: QR check-in prepares the room (US1, US2)

1. As U, book R starting at the current minute (end in 30 min).
2. Open `/rooms/R/display` in a second browser window. Expected: the badge shows "Reserviert", the device icons show light off, ventilation off and door locked, and a QR code is shown.
3. Scan the QR code with the phone (or open `/rooms/R/check-in?method=qr`) as U, and press "Anwesenheit bestätigen".
4. Expected on the check-in page: confirmation with room name and time, plus the device states light on, ventilation on, door unlocked.
5. Expected on the display within 30 s: "Belegt" with all three icons switched.
6. `GET /api/rooms/R/status` returns `OCCUPIED`, `lighting: true`, `ventilation: true`, `door: UNLOCKED`, `lastPresenceAt: null`.

## Scenario 2: NFC stand-in and login return (US1 scenario 2, US4)

1. Sign out. Open `/rooms/R/check-in?method=nfc` for a new booking of U whose window is open.
2. Expected: redirect to `/login`. After signing in as U, you land on the check-in page again, with the query string kept.
3. Confirm. Expected: the reservation shows `checkInMethod: NFC`.

## Scenario 3: Rejections (FR-003, FR-004, FR-011)

| Setup | Action | Expected |
|---|---|---|
| U's booking starts in 10 min | U opens the check-in link and confirms | Booking `ACTIVE`, display shows "Belegt" from now on |
| U's booking starts in 15 min | U opens the check-in link | "Check-in ab HH:MM möglich" (start − 10 min), no state change |
| U's booking starts in 5 min, another booking of the room still runs | U opens the link and confirms | "Der Raum ist noch belegt. Der Check-in ist ab HH:MM Uhr möglich.", no state change |
| U's booking started 6 min ago without check-in | U opens the link | Booking already `EXPIRED`, explanatory message |
| U's booking, window open | Another non-admin user V opens the link | "Keine passende Buchung", U's booking stays `RESERVED` |
| U's booking, window open | A opens the link and confirms | Booking `ACTIVE`, `checkedInByUserId = A` |
| Booking already `ACTIVE` | U confirms again | "Bereits in Nutzung", `alreadyActive: true`, no new device commands in logs |

## Scenario 4: Release at the end (US3)

1. Check in a booking that ends in 2 minutes. Wait until the end time plus at most 60 s.
2. Expected: the reservation is `COMPLETED`, the display shows "Verfügbar" with light and ventilation off, and the door stays unlocked.
3. Repeat with "Abschließen" (manual completion) and with cancellation of an `ACTIVE` booking. Expected: immediate release.
4. Back-to-back booking: U books 10:00–10:30 and 10:30–11:00 and checks in the second at 10:30 before the sweep completes the first. Expected: lights stay on.

## Scenario 5: Simulated motion sensor (US5)

1. As A with administration mode on, open the display of R during U's `ACTIVE` booking. Expected: a "Bewegung simulieren" button is visible.
2. Press it. Expected: "Zuletzt Bewegung erkannt: HH:MM:SS" is shown, and the reservation status and devices are unchanged.
3. Press it while R has only a `RESERVED` booking. Expected: `recorded: false`, and the booking stays `RESERVED`.
4. As U, check that the button is absent. `POST /api/admin/rooms/R/presence-events` returns `403`.

## Scenario 6: Door in the device controls (FR-012)

During U's `ACTIVE` booking, open `/rooms/R/control`. Expected: Beleuchtung, Lüftung and Tür (Verriegelt/Entriegelt) are listed and can be switched.

## Scenario 7: Device failure (FR-010)

Covered by automated tests with a failing fake `RoomDeviceGateway`. The check-in succeeds, `failedDevices` lists the failing kind, and the log contains `room_automation_device_failed`.

## Scenario 8: Admin printout

As A with administration mode on, open `/rooms/R`. Expected: a "Check-in-Codes" section with a printable QR code and the NFC link, ready to copy for writing to a tag.
