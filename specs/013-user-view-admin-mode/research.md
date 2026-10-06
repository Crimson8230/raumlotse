# Research: User View, Administration Mode and Access Control

## R1 – How to enforce deny-by-default authorization

**Decision**: Invert the existing `RoleAccessFilter`: it applies to every `/api/**` request that is not a safe read (GET/HEAD/OPTIONS) **and** to every `/api/admin/**` request; requests matching a small allow-list of user actions pass without the admin check. Everything else needs ADMIN and is answered with the existing codes (`401 AUTHENTICATION_REQUIRED`, `403 ADMIN_REQUIRED`, `503 ROLE_MANAGEMENT_UNAVAILABLE`).

Allow-list (user actions; ownership enforced in the service): `POST /api/rooms/{id}/reservations`, `PATCH /api/reservations/{id}`, `POST /api/reservations/{id}/{activate|complete|expire|cancel}`, `POST /api/rooms/{id}/device-controls/{kind}`, `PUT /api/auth/admin-mode` (own check), `POST /api/auth/login` (public, handled earlier).

**Rationale**: Reuses proven code and error contract, minimal change, and flips the default from allow to deny (FR-016). A matrix test enumerating all mapped handler methods fails when a new endpoint is added without a classification.

**Alternatives considered**:
- *Role authorities + `hasRole` in `authorizeHttpRequests`*: idiomatic, but requires loading roles into the principal on every request or a custom `AuthenticationProvider`, and rewriting the error responses and their tests. Larger change for the same outcome; reconsider in the redesign (docs/SYSTEMDOKUMENTATION.md S4).
- *`@PreAuthorize` per method*: easy to forget on new methods (default allow) — rejected, violates FR-016.

## R2 – Where administration mode lives

**Decision**: A boolean attribute in the HTTP session, set by `PUT /api/auth/admin-mode` (ADMIN only), exposed as `adminMode` in `GET /api/auth/roles`. The effective value is `flag && user is ADMIN` evaluated on each read; if the user is no longer ADMIN the flag is cleared.

**Rationale**: Satisfies "off after sign-in / ends with session" (FR-005, session is replaced at login), "not shared between devices" (A7), multi-tab consistency (all tabs share the session; the frontend already re-reads roles on focus), and revocation (FR-008). No schema change.

**Alternatives considered**: *Client-only state (React/sessionStorage)* — simple but not per-session across tabs and lets the UI claim a mode the server never approved (FR-007 wants server refusal); *persisted per user* — violates "off after every sign-in".

**Note**: Per A6 the mode does not gate API permissions; the ADMIN role does. The server therefore does not require the flag for admin calls.

## R3 – Ownership rule and response on violation

**Decision**: Owner = `reservation.createdByUserId == actor.userId`. Admin may act on all. Non-owner → `404 RESERVATION_NOT_FOUND` (same body as a missing reservation) for details and every action (FR-021). Reservations with `createdByUserId == null` are owned by nobody (admin only, A4).

**Rationale**: Avoids existence leaks; identity-based, so name changes or duplicate names are irrelevant (FR-019, US4-6).

**Alternatives considered**: `403` for non-owners — reveals existence; rejected per FR-021.

## R4 – Room schedule visibility

**Decision**: `GET /api/rooms/{id}/reservations` stays available to all signed-in users and returns every entry, but for entries the caller does not own (and non-admin) `note`, `reservedFor`, `createdBy` and `additionalEquipment` details are omitted/null; a new `ownedByMe` flag tells the UI which entries offer actions. `roomName`, time window, status stay (needed for availability and the room display).

**Rationale**: Booking needs occupancy; personal data must not leak (FR-023). Additive, backward-compatible shape.

## R5 – "My upcoming reservations" defect

**Decision**: Query by `createdByUserId` (new repository method, replaces `findTop10ByCreatedBy…`); the controller already passes the user id. Add an end-to-end test (create → list) which was missing — existing tests mock the service.

**Rationale**: Today `createdBy` holds the display name while the lookup passes the user id string. Fix required by FR-022.

## R6 – Legacy reservation creation path

**Decision**: Remove `createReservation(roomId, request)` (derives a UUID from `createdBy` text) and the name-derived identity fallback in `ReservationController.resolveUser`, if no production caller remains (only tests). Always set `createdByUserId` from the authenticated principal, including when the notification choice is ignored.

**Rationale**: These paths create reservations whose owner cannot be matched to a user and contradict FR-019.

## R7 – Frontend route split

**Decision**: Admin pages under `/admin/*` guarded by a `RequireAdminMode` wrapper: non-admin → "not available" notice; admin with mode off → notice with a "Enable administration mode" button; admin with mode on → page. Old addresses become `<Navigate>` redirects. `/maps*` becomes read-only for all; the editing UI (upload, placement, connections) moves to `/admin/maps*` by reusing `MapPage` with an `editable` prop.

**Rationale**: One shared component, no duplicated map UI. Prefix makes classification trivial (FR-010).

**Alternatives considered**: Hiding controls only (no route change) — leaves admin forms reachable by address and contradicts "adapt the routes".

## R8 – Logging refusals

**Decision**: Log one structured line per refusal: `access_denied category=<admin|ownership> method=<M> route=<pattern> userId=<uuid>` — no names, notes, or bodies (FR-026, constitution IV). Route is the matched pattern, never the raw URL with ids of other users' bookings.

## R9 – Endpoint classification

Source of truth in code: `AuthorizationMatrixTest.MATRIX` (fails if a mapped endpoint is unclassified or stale).

| Class | Endpoints |
|---|---|
| PUBLIC | `GET /api/health`, `GET /api/auth/csrf`, `POST /api/auth/login` |
| USER_READ (signed in) | all other `GET` under `/api` except `/api/admin/**` (rooms, search, buildings/floors, equipment types, reservations incl. `my-upcoming`, device controls, maps, connections, `/api/auth/me`, `/api/auth/roles`) |
| USER_ACTION (signed in; ownership/eligibility in service) | `POST /api/rooms/{id}/reservations`, `PATCH /api/reservations/{id}`, `POST /api/reservations/{id}/{activate,complete,expire,cancel}`, `POST /api/rooms/{id}/device-controls/{kind}`, `PUT /api/auth/admin-mode` |
| ADMIN | all other non-GET (buildings, floors, equipment types, rooms, `expire-unattended`, maps, placements, connections, floor map upload) and everything under `/api/admin/**` |
