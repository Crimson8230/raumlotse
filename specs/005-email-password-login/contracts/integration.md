# Identity, Role and Deployment Integration Contract

## Delivered by this feature

- Persistent user_account UUID identity, canonical email and password credentials.
- Verified principal UUID for each protected request; stable opaque string id for client-facing contracts.
- Account existence/identity lookup shared by authentication and future role integration.
- Login/session/expiry UI and API, same-origin transport, CSRF integration, and durable per-email cooldown history shared by all browsers.
- Local-only opt-in acceptance fixture; no production default account or implicit Admin.

## Feature 003 handoff

The role-management plan needs more than login. This feature does NOT claim to deliver registration, a paginated account directory/search endpoint, initial Admin provisioning/recovery, logout, account deletion or deactivation. Those remain separate delivery obligations before feature 003's full release gate can pass.

Map role management's userId to user_account.id, displayName to display_name, and unique accountLabel to canonical email. This is an internal adapter mapping, not permission to expose email through /api/auth/me. Role management owns UserRoleState, RoleAssignment, role vocabulary, versioning, guard and permission checks. Read persisted roles on each restricted operation; never trust a role header, submitted user id, or sign-in snapshot. Login never changes roles.

When role management is integrated, all account creation paths, including local fixtures if enabled, must transactionally create Viewer/version 0 before account visibility. Initial Admin provisioning is separate and follows its shared guard and last-Admin invariants. Until that integration exists, login itself creates no role claims and must not invent Admin authority. Tests may provide a real test-only persisted permission restriction to prove identity integration; release of actual role pages still requires real role tests from feature 003.

Production release gates remain **pending** in this checkout:

| Gate | Required evidence before role-managed production routes are enabled |
|---|---|
| Real provisioned accounts | Approved production provisioning and lifecycle path using the shared canonicalizer and password encoder; no fixture profile |
| Stable identity mapping | Feature 003 maps its user id to `user_account.id` and keeps its display/account labels aligned |
| Persisted authorization | Every restricted request reads current persisted role assignments; login identity or client-supplied roles never grant permission |
| Initial Admin operation | Guarded bootstrap/recovery with last-Admin protection and a documented operator procedure |
| Account lifecycle coordination | Registration, disable/delete and role state changes coordinate transactionally; existing sessions are invalidated or checked according to that policy |

Current checkout has no role permission enforcement. This change adds authentication to current business endpoints; it does not define new role-specific room/catalog permissions. Any role checks already installed when implementation merges must remain in force and receive regression tests.

## Provisioning and rollout

Production login cannot be accepted until accounts have been provisioned through an approved separate process using this feature's canonicalization and encoder. No migration seeds a password. A local fixture profile is for manual validation only and must be rejected when production configuration is active. Never reset an existing account from fixture environment variables.

Deployment uses one backend instance and an HTTPS origin serving the SPA and proxying /api. SPA deep links fall back to the frontend entry document; backend APIs return JSON errors. Session cookies are Secure outside explicit local development, HttpOnly, SameSite=Lax, path=/ and nonpersistent. Configure trusted proxy forwarding only for the actual deployment proxy. Do not enable wildcard credentialed CORS. Sessions are lost on restart; multi-instance deployment requires a separate shared-session design.

Replace committed database/pgAdmin credential defaults in application.yaml and Compose with required environment configuration. Keep real values in untracked local configuration or deployment secrets; example files contain names/placeholders only. Do not print fixture passwords in startup errors, shell output or logs. Review proxy, request and SQL logging so credentials and personal data cannot be captured.

## Backward compatibility and diagnostics

Coordinate this backend and frontend release; the existing shared client must send CSRF for all room/catalog writes. Existing callers migrate according to contracts/api.md. Never weaken authentication/CSRF to keep old unauthenticated scripts working.

Safe structured diagnostic fields: event, outcome, status, duration and random correlation id. Exclude email, display name, account id, password/hash, cookie/session id, CSRF token, limiter identity key/HMAC secret, failure timestamps, raw payload and raw exception text. Use fixed reasons for bad credentials, invalid request, expired authentication and unavailable dependencies. Tests capture successful, rejected, validation, unknown-user and database-failure logs, including lower-layer logging, and assert sensitive sentinels are absent.

## Cooldown operations and privacy

Provision AUTH_ATTEMPT_HMAC_KEY as a base64-encoded random secret with at least 32 decoded bytes. Fail startup if missing or malformed; there is no default or startup-generated substitute. Local development uses a separately provisioned persistent key in untracked configuration. Every restart/process uses the same value, otherwise an unchanged email would acquire a new history and bypass the cooldown. Never include the key, derived identity keys or limiter query parameters in diagnostics.

Backend restarts retain PostgreSQL limiter history; session loss does not clear it. Unknown-address rows have no account foreign key. Production account provisioning must not delete an existing limiter row, so registering an address does not evade a current cooldown. Role assignment, last-Admin rules and existing sessions are unaffected by the limiter.

Cleanup runs one batch of at most 500 eligible rows on startup and every five minutes; it locks/rechecks rows, skips those held by login and never removes live history or extends blocked_until. Monitor aggregate table size, cleanup success, lock waits and 429/503 counts without identity labels. Capacity exhaustion and cleanup/limiter failures must not lead to a fail-open path. Cleanup failure may leave expired rows for a later run; request-time expiry remains authoritative.

Ordinary secret rotation is not a blind environment-variable change. For this single-instance design, planned rotation pauses new logins, drains in-flight attempts, and then waits at least 30 minutes so all prior failure windows/cooldowns expire, then purges expired limiter rows and switches the key consistently before resuming. This procedure does not revoke established sessions. Emergency rotation or online overlap is separate operational work; document maintenance impact before performing it.

The chosen policy can temporarily block new logins for a known email when someone repeatedly guesses its password. Existing sessions remain valid, and blocked attempts cannot prolong the current deadline. Do not silently replace this user-approved per-email policy with an IP-only limit or permanent account lock.

Roll out the new limiter migration and HMAC configuration before enabling the revised login handler. The updated frontend understands triggering 401 metadata and 429; deploy it with the backend. An older client still cannot bypass server enforcement but would provide incomplete wait feedback. No new public limiter-status, clear-history or unlock endpoint is introduced.
