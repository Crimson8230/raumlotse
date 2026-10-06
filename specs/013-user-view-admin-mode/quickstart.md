# Quickstart: validate user view, administration mode and access control

Prerequisites: `docker compose up` with the `local-auth-fixture` profile (fixture account = ADMIN, see fix commit `2260187`), a second regular account (role VIEWER) and a third regular account; one building/floor/room; `cd frontend && npm run dev`.

## Automated

```bash
cd backend && ./mvnw test            # authorization matrix, ownership, admin mode, my-upcoming
cd frontend && npm run lint && npm test && npm run build
```

## Manual scenarios

1. **User view (US1)** – sign in as regular user: navigation shows Home, Räume, Karten only; room list has no "New room/Deactivate/Delete"; room detail has no "Edit Room"; `/maps` has no upload/placement/connection controls. Booking works and appears in "my upcoming" (SC-005, SC-008).
2. **Direct admin addresses (US1.2, US5)** – as regular user open `/admin/rooms/new`, `/admin/users`, old `/rooms/new`, `/locations`: "not available" notice, no form (SC-007).
3. **Admin mode (US2)** – sign in as admin: mode is off, admin items hidden (SC-006). Switch on (≤2 clicks, SC-004): admin items + indicator visible. Open a second tab: mode is on there after focus. Switch off: admin pages are left. Sign out/in: off again.
4. **Admin address with mode off** – admin opens `/admin/locations` → notice with "Enable administration mode" button.
5. **Revocation (US2.6)** – while admin mode is on, remove ADMIN from that user in another session; next action in the first session → mode off, admin call refused (403).
6. **Master-data authorization (US3)** – as regular user, direct requests (use browser devtools or curl with the session cookie + CSRF header):
   `POST /api/rooms`, `PUT /api/buildings/{id}`, `DELETE /api/floors/{id}`, `POST /api/equipment-types/{id}/deactivate`, `POST /api/reservations/expire-unattended` → all `403 ADMIN_REQUIRED`, data unchanged (SC-002). As admin with mode off → applied.
7. **Ownership (US4)** – user A books; user B (regular) requests `GET/PATCH /api/reservations/{id}`, `activate`, `complete`, `expire`, `cancel` → `404`, reservation unchanged (SC-003). Admin cancels A's booking → 200. B's view of the room schedule shows the slot without person/note.
8. **Name independence (US4.6)** – give two users the same display name; each only sees/manages own bookings.
9. **Logs** – `docker compose logs backend | grep access_denied`: lines contain category, method, route pattern, user id; no names/notes.

## Verification record

- Baseline before changes (2026-10-06, `./mvnw test`): 398 tests, 0 failures, 29 errors. All 29 are Testcontainers classes that cannot start in the development sandbox (no Docker socket access); Testcontainers-based integration tests therefore were **not executed** during implementation and must be run in CI/with Docker before merge.
- Frontend baseline: 281 tests green, lint and typecheck clean.

### After implementation (2026-10-06)

- Backend `./mvnw test`: 526 tests, **0 failures**, 31 errors. All 31 are Testcontainers classes that cannot start without Docker access (29 from the baseline plus the two new integration tests `MasterDataAuthorizationIntegrationTest` and `ReservationOwnershipIntegrationTest`). **These integration tests are written but have never been executed** – run them with Docker before merging. Everything that runs without Docker is green, including `AuthorizationMatrixTest` (98 cases), `ReservationAccessPolicyTest`, `AdminModeServiceTest`, `AdminModeControllerTest`, the ownership cases in `ReservationServiceTest` and `ReservationOwnershipTest`.
- Frontend: 302 tests green (was 281), `eslint`, `tsc -b` and `npm run build` clean.
- Manual scenarios 1–9 of this guide: **not performed** (no running stack in the development sandbox).

### Deviations from plan and tasks

- Administration mode state is provided by the hook `auth/useAdminMode` on top of `useCurrentRoles` (all consumers re-read on the `raumlotse:roles-changed` event) instead of a separate `AdminModeProvider`; simpler, same behavior.
- No `AdminModeIntegrationTest` (T028 integration part): the mode is covered by `AdminModeServiceTest` and `AdminModeControllerTest`; clearing on login is a one-line change in `LoginService` without its own test.
- T044/T048 (warn before discarding an open administration form when the mode is switched off) are **not implemented**: the app uses `BrowserRouter`, which does not support `useBlocker`; needs a data-router migration or a custom confirm in the mode switch.
- T052 (optional index on `reservation.created_by_user_id`): **not measured**, no migration added. Revisit when the table grows.
- T017 (controller-level redaction test) is covered at service level (`ReservationServiceTest`) and in `ReservationOwnershipIntegrationTest` instead.

Contract: [contracts/access-control-api.yaml](./contracts/access-control-api.yaml) · Model: [data-model.md](./data-model.md) · Decisions: [research.md](./research.md)
