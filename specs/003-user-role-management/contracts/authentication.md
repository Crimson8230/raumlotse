# Authentication Prerequisite Handoff

Authentication, account creation and initial Admin provisioning are separate work, per clarification. These capabilities are required without choosing a sign-in protocol here.

## Capabilities required

- Resolve verified stable account identity per request; absence -> 401. Browser-supplied id/roles never establish identity.
- Account directory: opaque id, displayName, unique accountLabel, existence lookup, paginated substring search. API serializes ids as strings; storage uses native identity keys.
- Frontend authenticated identity and sign-in/expired-session flow. Navigation derives from current roles.
- Secure credential transport. For cookie sessions, use prerequisite CSRF tokens and same-origin policy; for other transport, use its documented protections. Never disable protection for role tests.
- Transactional account creation hook writes Viewer and version 0 before account visibility. Registration cannot request elevated roles.
- Secure initial Admin provisioning/recovery with no public promotion endpoint or committed secret. Coordinate through the shared mutation guard.
- Coordinate supported deletion/deactivation with last-Admin preservation and common lock order. No lifecycle endpoint is added here.
- Permit per-request persisted role checks; sign-in-time role claims are not authoritative after updates.

## Ownership and release gate

Identity owns account storage, sign-in/out, credentials/session verification, registration and provisioning. Role management owns role vocabulary, assignments, versioning, authorization and editor. A small adapter maps identity to this contract; do not add a second authentication framework.

Before implementing the adapter, verify the delivered prerequisite and record its concrete module/key mapping in the implementation PR. Before release, prove these capabilities with real authenticated accounts, expired credentials and request-forgery rejection where applicable.

Missing prerequisite capability blocks integration/release, not planning. Isolated tests may supply verified principal fixtures; production must never use mock identity or implicit Admin.

## Implementation prerequisite audit — 2026-09-20

Audited implementation branch: `003-user-role-management`, HEAD `5e13058`.
Authentication is integrated through `ef8d841` (PR #19). The earlier feature 004
reference meant the authentication prerequisite; feature 004 in the repository is
room reservations, while authentication was delivered as feature 005.

| Required capability | Evidence on the implementation branch | Status |
|---|---|---|
| Account storage and native key | `backend/src/main/java/at/mci/igp/raumlotse/domain/UserAccount.java`: `user_account.id` UUID, displayName, unique canonical email used as accountLabel | Available |
| Verified request identity and credential protections | `dto/AuthenticatedUser.java` carries UUID userId; `config/SecurityConfig.java` uses server sessions and CSRF; `service/CurrentAccountFilter.java` rejects deleted accounts on the next request (all under `backend/src/main/java/at/mci/igp/raumlotse/`) | Available |
| Account directory and existence lookup | `backend/src/main/java/at/mci/igp/raumlotse/repository/UserAccountRepository.java` provides UUID lookup; role adapter must add parameterized paginated literal substring search over displayName/email | Existing model available; search to implement |
| Frontend session and expired-session flow | `frontend/src/auth/AuthProvider.tsx`, `frontend/src/auth/RequireAuth.tsx`, `frontend/src/API/auth.ts`, `frontend/src/API/client.ts`: same-origin cookie credentials, CSRF token fetch, 401 expiry event; `/api/auth/me` currently returns identity without roles | Available; current membership integration to implement |
| Transactional account creation | `backend/src/main/java/at/mci/igp/raumlotse/config/LocalAuthFixtureConfiguration.java` calls repository saveAndFlush directly; no transactional role hook or registration endpoint exists | Missing hook; only local fixture creation is currently supported |
| Initial Admin provisioning/recovery | Local fixture creates an ordinary account only; no role assignments or secure initial-Admin operation exist | Missing prerequisite capability |
| Deletion/deactivation coordination | No supported identity deletion/deactivation endpoint or service exists | No path to integrate currently; future paths must use the shared guard |

The authentication integration test now passes against disposable PostgreSQL 17.
The merge introduced a duplicate Flyway V4. Preserve reservation V4 and independent
login-attempt V5; the user-account migration is renamed to V6 with unchanged SQL.
V7__create_user_role_tables.sql and V8__backfill_user_roles.sql are reserved for roles.
No existing database history has been edited. A database previously migrated with
the old authentication-only V4 requires an explicit history reconciliation before
upgrade; do not run automatic repair, clean, or rename applied history entries.

Per explicit scope direction, transactional account creation, secure initial-Admin
provisioning/recovery and account deletion/deactivation remain external prerequisites.
Feature 003 does not add or modify those workflows. The roles API fails closed until
an external operator provisions at least one Admin and every account has a role.
Feature 003 can be built and tested with isolated verified identities; production
release and account-lifecycle acceptance remain blocked until the prerequisite
provides its documented hooks. No mock identity or implicit Admin is permitted.
