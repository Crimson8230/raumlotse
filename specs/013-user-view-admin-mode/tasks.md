---

description: "Task list for feature 013 – user view, administration mode and access control"
---

# Tasks: User View, Administration Mode and Access Control

**Input**: Design documents from `/specs/013-user-view-admin-mode/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/access-control-api.yaml, quickstart.md

**Tests**: REQUIRED (constitution I, test-first). Within every story, the test tasks come first and MUST fail before the implementation tasks are started.

**Organization**: Grouped by user story. All five stories are independently testable; the phases are ordered security first (US3, US4), then the visible mode and views (US2, US1, US5).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on open tasks)
- **[Story]**: US1–US5 as in spec.md
- Backend root: `backend/src/main/java/at/mci/igp/raumlotse/` (abbreviated `B/`), tests `backend/src/test/java/at/mci/igp/raumlotse/` (`BT/`); frontend `frontend/src/` (`F/`)

---

## Phase 1: Setup

- [X] T001 Verify the baseline is green before changing anything: `cd backend && ./mvnw test` and `cd frontend && npm run lint && npm test`; note counts in `specs/013-user-view-admin-mode/quickstart.md` under "Verification record"
- [X] T002 [P] Inventory every mapped endpoint (all `@*Mapping` in `B/controller/`) into a table in `specs/013-user-view-admin-mode/research.md` (new section "R9 – Endpoint classification") with the class `PUBLIC | USER_READ | USER_ACTION | ADMIN`, matching `x-authorization` in `contracts/access-control-api.yaml`

---

## Phase 2: Foundational (blocks all stories)

- [X] T003 [P] Add `isAdmin(UUID userId)` to `B/service/UserRoleSafety.java` (true iff `assignments.roles(userId)` contains `Role.ADMIN`; no exception); unit test in `BT/service/UserRoleSafetyTest.java` (admin → true, VIEWER → false, no assignment → false)
- [X] T004 [P] Create `B/dto/Actor.java` record `(UUID userId, boolean admin)` and a static `Actor.from(Authentication, UserRoleSafety)` that throws the existing `UserRoleException(401,"AUTHENTICATION_REQUIRED",…)` for anonymous/non-`AuthenticatedUser` principals; unit test `BT/service/ActorTest.java`
- [X] T005 [P] Add a structured refusal logger `B/service/AccessDeniedLog.java` writing `access_denied category=<admin|ownership> method=<M> route=<pattern> userId=<uuid>` (no names, notes, bodies, raw URLs; FR-026); test in `BT/config/AccessDeniedLogPrivacyTest.java` modeled on `BT/config/MapLoggingPrivacyTest.java`
- [X] T006 [P] Frontend: extend `getCurrentRoles` in `F/API/userRoles.ts` to type `{ roles; ready; adminMode: boolean }` and add `setAdminMode(enabled: boolean)` calling `PUT /api/auth/admin-mode`; types in `F/types/userRole.ts`; tests in `F/API/userRoles.test.ts`

**Checkpoint**: building blocks exist; stories can start.

---

## Phase 3: User Story 3 – Only authorized people change master data (P1) 🎯 MVP part 1

**Goal**: Every write outside a short allow-list requires ADMIN; deny by default (FR-013–FR-018, FR-026).

**Independent Test**: As regular user, every administrative write returns 403 `ADMIN_REQUIRED` with no data change; as admin it succeeds (quickstart scenario 6).

### Tests (write first, must fail)

- [X] T007 [P] [US3] `BT/security/AuthorizationMatrixTest.java`: reflectively collect all `@GetMapping/@PostMapping/@PutMapping/@PatchMapping/@DeleteMapping` handlers under `at.mci.igp.raumlotse.controller`, assert each is in an explicit classification map (fails for unclassified endpoints, FR-016), and for each non-GET `ADMIN` entry assert VIEWER → 403 `ADMIN_REQUIRED`, ADMIN → not 403, anonymous → 401
- [X] T008 [P] [US3] `BT/security/MasterDataAuthorizationIntegrationTest.java` (Testcontainers, base `BT/AbstractIntegrationTest.java`): as VIEWER attempt create/update/deactivate/reactivate/delete on building, floor, equipment type and room plus `POST /api/reservations/expire-unattended`; assert 403 and unchanged DB rows; as ADMIN assert success (mode flag not required, A6); GET reads succeed for VIEWER (FR-017)
- [X] T009 [P] [US3] (covered by `AuthorizationMatrixTest` and `AccessDeniedLogPrivacyTest` instead of extending `SecurityPolicyTest`) `BT/security/SecurityPolicyTest.java`: `GET /api/admin/users` as VIEWER → 403; unauthenticated writes → 401; refusal writes one `access_denied category=admin` log line

### Implementation

- [X] T010 [US3] Rewrite `B/service/RoleAccessFilter.java` in the project's normal formatting (one statement per line): apply to every `/api/**` request that is not GET/HEAD/OPTIONS, plus every `/api/admin/**`; skip only for the allow-list in research R1 (`POST /api/rooms/*/reservations`, `PATCH /api/reservations/*`, `POST /api/reservations/*/{activate|complete|expire|cancel}`, `POST /api/rooms/*/device-controls/*`, `PUT /api/auth/admin-mode`, `POST /api/auth/login`); keep error codes/`Cache-Control: no-store`; call `AccessDeniedLog` on 403; remove `isMapWrite`
- [X] T011 [US3] Make `POST /api/reservations/expire-unattended` explicitly admin-only (it is not in the allow-list, so T010 covers it; confirm via T007/T008) and update its entry in `specs/013-user-view-admin-mode/contracts/access-control-api.yaml` if the matrix reveals differences
- [X] T012 [US3] Update existing tests that assumed open master-data writes (grep `BT/controller/*ControllerTest.java`, `BT/security/ProtectedBusinessRouteTest.java`, `BT/*IntegrationTest.java`) to authenticate as ADMIN; run `./mvnw test`
- [X] T013 [US3] Update README "Current Status"/security paragraph and remove the stale "Planned: Authentication/authorization" bullet in `README.md`

**Checkpoint**: backend master data is protected; US3 verifiable without any UI change.

---

## Phase 4: User Story 4 – Reservations only changeable by their owner (P1) 🎯 MVP part 2

**Goal**: Owner (by user id) or ADMIN only; others get 404; redacted room schedule; correct "my upcoming" (FR-019–FR-025).

**Independent Test**: Quickstart scenarios 7 and 8.

### Tests (write first, must fail)

- [X] T014 [P] [US4] Rewrite/extend `BT/controller/ReservationOwnershipTest.java`: user B (non-admin) gets 404 `RESERVATION_NOT_FOUND` with a body identical to a non-existing id for GET, PATCH, activate, complete, expire, cancel; reservation unchanged; owner A and ADMIN succeed; reservation with `created_by_user_id = NULL` → only ADMIN
- [X] T015 [P] [US4] `BT/ReservationOwnershipIntegrationTest.java` (Testcontainers): two users with identical display name each manage only their own; `my-upcoming` after real creation returns exactly the caller's own RESERVED/ACTIVE future reservations, max 10, ordered by start (FR-022, regression for the name/id mismatch); admin sees only own there
- [X] T016 [P] [US4] `BT/service/ReservationAccessPolicyTest.java`: owner/admin/other/legacy-null matrix for `canManage(Reservation, Actor)` and `redact(...)`
- [X] T017 [P] [US4] Room schedule redaction test in `BT/controller/ReservationControllerTest.java`: non-owner entries have null `note`/`reservedFor`/`createdBy`, empty `additionalEquipment`, `ownedByMe=false`; owner/admin entries complete (`ownedByMe` true only for owner); `GET /api/reservations/{id}` per contract
- [X] T018 [P] [US4] Repository test in `BT/repository/ReservationOccupancyIntegrationTest.java` for new `findTop10ByCreatedByUserIdAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc` (replace the name-based test at line ~109)

### Implementation

- [X] T019 [US4] Create `B/service/ReservationAccessPolicy.java`: `canManage(Reservation, Actor)` = `admin || (createdByUserId != null && createdByUserId.equals(actor.userId()))`; `requireManage(...)` throws `NotFoundException("Reservation <id> not found.")` and logs `access_denied category=ownership`
- [X] T020 [US4] `B/repository/ReservationRepository.java`: replace `findTop10ByCreatedBy…` with `findTop10ByCreatedByUserIdAndStatusInAndEndTimeGreaterThanOrderByStartTimeAsc(UUID, Collection<ReservationStatus>, Instant)`
- [X] T021 [US4] `B/service/ReservationService.java`: change `getReservation`, `updateReservationMetadata`, `activate/complete/expire/cancelReservation` to take an `Actor` and call `ReservationAccessPolicy.requireManage` before any state check; `getMyUpcomingReservations(UUID userId)` uses T020; `getReservationsForRoom(roomId, from, to, Actor)` applies redaction
- [X] T022 [US4] `B/dto/ReservationResponse.java`: add `boolean ownedByMe`; add factory `redacted(Reservation)` (null `note`/`reservedFor`/`createdBy`/`createdAt`? keep `createdAt`, empty equipment list, null `seatingArrangement`/`expectedAttendees` per contract); update the two constructors and all callers
- [X] T023 [US4] `B/controller/ReservationController.java`: build the `Actor` via `Actor.from`, pass to the service methods; delete the `UserDetails` name→UUID fallback in `resolveUser`; `my-upcoming` passes `user.userId()` (UUID, not string)
- [X] T024 [US4] Remove the legacy `createReservation(UUID, ReservationCreateRequest)` overload and the `honorNotificationChoice` flag in `B/service/ReservationService.java`; always call `setCreatedByUserId(user.userId())`; migrate tests that used the legacy overload (grep `BT/service/ReservationServiceTest.java`, `BT/ReservationCreationIntegrationTest.java`)
- [X] T025 [US4] Use the injected `Clock` instead of `Instant.now()` for the future-start check in `ReservationService.createReservation` (touched code, enables deterministic tests); adjust affected tests
- [X] T026 [P] [US4] Frontend: add `ownedByMe` and nullable personal fields in `F/types/reservation.ts`; in `F/components/ReservationList/ReservationList.tsx` show action buttons (edit/check-in/cancel/…) only for `ownedByMe` or admin and render redacted entries as "Belegt" with time window; tests in `ReservationList.test.tsx`
- [X] T027 [P] [US4] Frontend: ensure `F/components/RoomDisplay/roomDisplayLogic.ts` and `RoomDisplay.tsx` work with redacted entries (no use of `reservedFor`/`note`); update `roomDisplayLogic.test.ts`

**Checkpoint**: US3 + US4 complete = security MVP, deployable without UI changes (except T026/T027 for compatibility).

---

## Phase 5: User Story 2 – Administration mode (P1)

**Goal**: Admins switch a per-session mode; effective only for ADMIN; revocation ends it (FR-004–FR-009).

**Independent Test**: Quickstart scenario 3 and 5 (API level without UI: `PUT /api/auth/admin-mode`, `GET /api/auth/roles`).

### Tests (write first, must fail)

- [X] T028 [P] [US2] `BT/controller/AdminModeControllerTest.java` + `BT/AdminModeIntegrationTest.java`: new session → `adminMode=false`; admin `PUT {enabled:true}` → 200 `true` and visible in `GET /api/auth/roles`; VIEWER `PUT` → 403 `ADMIN_REQUIRED` and stays false; missing/invalid body → 400; anonymous → 401; revoke ADMIN while flag set → next `GET /api/auth/roles` returns `adminMode=false` and flag cleared; new login → false; CSRF required
- [X] T029 [P] [US2] Frontend tests: `F/auth/AdminModeProvider.test.tsx` (initial false, toggle calls API and updates, error → stays off, refresh on `focus` and `raumlotse:roles-changed`), `F/components/Navigation/Navigation.test.tsx` (switch only for admins, indicator when on, admin items only when on)

### Implementation

- [X] T030 [US2] Create `B/service/AdminModeService.java`: `boolean effective(HttpSession, Actor)` (clears the attribute when not admin) and `void set(HttpSession, Actor, boolean)` (throws `UserRoleException(403,"ADMIN_REQUIRED",…)` for non-admin); attribute name constant `ADMIN_MODE`
- [X] T031 [US2] Create `B/controller/AdminModeController.java` (`PUT /api/auth/admin-mode`, request record `{ @NotNull Boolean enabled }`, response `{ adminMode }`, `Cache-Control: no-store`); extend `B/controller/CurrentRolesController.java` response `Membership(roles, ready, adminMode)`
- [X] T032 [US2] Clear the attribute on login in `B/service/LoginService.java#prepareSession`
- [X] T033 [US2] Create `F/auth/AdminModeProvider.tsx` + `F/auth/useAdminMode.ts` (state `{ loading, isAdmin, adminMode, setMode(enabled) }` fed by `getCurrentRoles`, refreshed on focus/`raumlotse:roles-changed`; replaces `useCurrentRoles` usage); wrap in `F/App.tsx` inside `AuthProvider`; keep `useCurrentRoles` only if still used elsewhere, otherwise delete with its test
- [X] T034 [US2] `F/components/Navigation/Navigation.tsx` + `Navigation.css`: mode switch (admins only, 1 click to toggle) and a persistent "Administrationsmodus aktiv" indicator; admin items (Standorte, Karten bearbeiten, Benutzerrollen) only when `adminMode`; keep existing German labels

**Checkpoint**: mode works end to end at API and shell level.

---

## Phase 6: User Story 1 – Clean user view (P1)

**Goal**: Regular users see only overview/booking; admin controls appear only in admin mode (FR-001–FR-003).

**Independent Test**: Quickstart scenario 1.

### Tests (write first, must fail)

- [X] T035 [P] [US1] `F/pages/RoomListPage.test.tsx`: no "New room", "Deactivate/Reactivate", "Delete" and no status filter "Deactivated" for non-admin or admin with mode off; present with mode on
- [X] T036 [P] [US1] `F/pages/RoomDetailPage.test.tsx`: no "Edit Room" without admin mode; booking, display, control links remain
- [X] T037 [P] [US1] `F/pages/MapPage.test.tsx`: with `editable=false` no upload, delete, placement, drag, unplaced-room list or connection editing; map and reachable connections are shown read-only

### Implementation

- [X] T038 [US1] `F/pages/RoomListPage.tsx`: gate create/deactivate/reactivate/delete and the "Deactivated" filter on `useAdminMode().adminMode`; user view lists active rooms only
- [X] T039 [US1] `F/pages/RoomDetailPage.tsx`: gate "Edit Room" link (target `/admin/rooms/:id/edit`) on admin mode
- [X] T040 [US1] `F/pages/MapPage.tsx`: add prop `editable` (default false); hide all write controls when false (`MapUploadControl`, `UnplacedRoomList`, delete, `ConnectionCatalog`, drag/keyboard moving in `MapCanvas`); in read-only mode keep selection of map and display of placed rooms/connections
- [X] T041 [P] [US1] `F/pages/HomePage.tsx` / `F/components/MyUpcomingReservations/`: verify no admin links remain; add test assertions

**Checkpoint**: user view clean.

---

## Phase 7: User Story 5 – Route split and redirects (P2)

**Goal**: Unambiguous addresses; `/admin/*` guarded; old bookmarks redirect (FR-009–FR-012).

**Independent Test**: Quickstart scenarios 2 and 4.

### Tests (write first, must fail)

- [X] T042 [P] [US5] `F/auth/RequireAdminMode.test.tsx`: non-admin → "not available" notice (no outlet rendered); admin + mode off → notice with "Enable administration mode" button that calls `setMode(true)` and then renders the outlet; admin + mode on → outlet; loading and failed-lookup states
- [X] T043 [P] [US5] `F/App.protected-routes.test.tsx` + `F/App.user-roles.test.tsx`: route table per plan (user routes for any signed-in user; `/admin/locations`, `/admin/rooms/new`, `/admin/rooms/:id/edit`, `/admin/maps`, `/admin/maps/:mapId`, `/admin/users`, `/admin/users/:userId/roles` guarded); redirects `/locations`, `/rooms/new`, `/rooms/:id/edit` → admin equivalents; unauthenticated → `/login` and back to the requested permitted address (FR-012)
- [ ] T044 (DEFERRED, see quickstart "Deviations") [P] [US5] Unsaved-changes guard test in `F/components/RoomForm/RoomForm.test.tsx`: switching mode off or navigating away with a dirty form prompts before discarding (edge case)

### Implementation

- [X] T045 [US5] Create `F/auth/RequireAdminMode.tsx` (replaces `RequireAdmin.tsx`; delete the old file and its imports); German texts consistent with existing role pages
- [X] T046 [US5] `F/App.tsx`: new route table under `<Route path="/admin" element={<RequireAdminMode/>}>`; `/maps*` render `<MapPage editable={false}/>`, `/admin/maps*` render `<MapPage editable />`; add `<Navigate replace>` redirects (use parameter-preserving element for `:roomId`)
- [X] T047 [US5] Update all in-app links to the new addresses: `RoomListPage` ("New room" → `/admin/rooms/new`), `RoomDetailPage`, `RoomFormPage` (cancel/back targets), `MapPage` navigations (`navigate('/maps/…')` → base path from `editable`), `Navigation`
- [ ] T048 (DEFERRED) [US5] Implement the dirty-form guard from T044 (e.g. React Router `useBlocker`) in `F/pages/RoomFormPage.tsx` / `F/components/RoomForm/RoomForm.tsx` and when the mode is switched off
- [X] T049 [US5] After a 403 `ADMIN_REQUIRED` response on an admin call, dispatch `raumlotse:roles-changed` from `F/API/client.ts` so the UI re-reads roles/mode (revocation case, spec US2.6); test in `F/API/client.test.ts`

---

## Phase 8: Polish & Cross-cutting

- [X] T050 [P] Update `specs/013-user-view-admin-mode/contracts/access-control-api.yaml` to any deviations found during implementation (contract-first, constitution III); update `README.md` endpoint list (`PUT /api/auth/admin-mode`) and route overview
- [X] T051 [P] Update `docs/SYSTEMDOKUMENTATION.md`: mark S1, S2, S3, F1, F2 (partly), F3 as resolved with this feature's commit references, and update the authorization matrix in §5
- [ ] T052 (NOT MEASURED, no index added) Decide on the optional index: run the quickstart "my upcoming" query with `EXPLAIN` on a seeded table (≥100k reservations); only if a seq scan on `created_by_user_id` appears, add `backend/src/main/resources/db/migration/V14__add_reservation_owner_index.sql` (`CREATE INDEX ix_reservation_owner_upcoming ON reservation (created_by_user_id, status, end_time, start_time);`) plus update `BT/MigrationVersionTest.java`; otherwise record "not needed" in `data-model.md`
- [X] T053 (automated part only; Testcontainers ITs and manual scenarios not run, see quickstart) Full verification: `cd backend && ./mvnw test`, `cd frontend && npm run lint && npm test && npm run build`; walk through all nine scenarios in `quickstart.md` and record results under "Verification record"
- [X] T054 Final grep checks: no usage of `findTop10ByCreatedBy`, `RequireAdmin`, `isMapWrite`, `honorNotificationChoice`; no remaining links to `/locations`, `/rooms/new`, `/rooms/:id/edit` outside redirects

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 → stories. T003/T004/T005 block all backend stories; T006 blocks US2/US1/US5 frontend work.
- **US3** (T007–T013) and **US4** (T014–T027) are independent of each other and of the UI; both depend only on Phase 2. Do US3 first (T012 adjusts tests that US4 also touches).
- **US2** backend (T028, T030–T032) depends on T003/T004. Its frontend part (T029, T033, T034) depends on T006.
- **US1** (T035–T041) depends on T033 (`useAdminMode`).
- **US5** (T042–T049) depends on T033 and T040 (`editable` prop); T047 follows T038–T040.
- Polish after all stories; T052 can run any time after T020.

### Within a story

Tests first (must fail) → policy/service → controller/DTO → frontend.

## Parallel Opportunities

- Phase 2: T003, T004, T005, T006 all [P].
- After Phase 2: one developer on US3, one on US4 (backend), one on US2 frontend (T029/T033/T034).
- Inside US4: T014–T018 test tasks in parallel; T026 and T027 (frontend) parallel to backend work after T022.
- Inside US1: T035–T037 in parallel.

## Implementation Strategy

1. **MVP = Phase 1 + 2 + US3 + US4** (security fixes, ~27 tasks): closes the open master-data and ownership holes and the "my upcoming" defect; shippable alone because the UI keeps working (T012, T026, T027 keep it compatible).
2. Add **US2** (mode) → **US1** (clean views) → **US5** (routes/redirects) as one increment so the visible behavior changes together; US1 alone without US5 is not meaningful.
3. Polish and full verification last (T050–T054).

## Summary

| Phase | Tasks | Count |
|---|---|---|
| Setup | T001–T002 | 2 |
| Foundational | T003–T006 | 4 |
| US3 | T007–T013 | 7 |
| US4 | T014–T027 | 14 |
| US2 | T028–T034 | 7 |
| US1 | T035–T041 | 7 |
| US5 | T042–T049 | 8 |
| Polish | T050–T054 | 5 |
| **Total** | | **54** |
