# Quickstart Validation: Email and Password Login

This guide describes the implemented email/password login and the additional evidence required before a production rollout.

## Verification status

Latest verification: `npm test` passes (20 files, 89 tests), and `npm run lint` and `npm run build` pass. The complete backend Maven suite passes (230 tests in 39 suites, zero failures, errors, or skips) with Docker/Testcontainers, including the authentication logging request-path test. Real-browser acceptance remains manual release evidence and was not run in this environment because no browser automation runner is configured.

The migration allocation is V5 `login_attempt_state`, V6 `user_account`, V7 role-management tables, and V8 role backfill. Testcontainers applied that order successfully to a fresh PostgreSQL 17 database and validated all eight migrations. The role-migration tests also passed after login migrations, confirming compatibility with feature 003's installed schema.

The complete backend suite was run from `backend` with:

```powershell
./mvnw.cmd test -q
```

The PostgreSQL tests validate the V5 limiter / V6 account / V7 role tables / V8 role backfill ordering, fresh-service cooldown persistence, serialized login behavior, cleanup races and compatibility with role management. Frontend automated checks cover cooldown focus/visibility updates and explicit retry; browser checks below still need a configured browser runner and disposable provisioned account.

The production role integration is still a feature 003 release prerequisite, as detailed in `contracts/integration.md`.

## Prerequisites

- Java 21, Node/npm matching the existing frontend toolchain, Docker with Linux containers, and PowerShell (commands below).
- Implemented feature and resolved backend/frontend dependencies. Use an isolated local database for the fixture; never a production database.
- Provision a persistent local AUTH_ATTEMPT_HMAC_KEY (base64-encoded, at least 32 random decoded bytes) in untracked configuration; reuse it across runs, never regenerate it on backend startup.
- Complete credential externalization described in contracts/integration.md. Supply database and pgAdmin values in untracked local environment configuration; no committed credential defaults.
- Refer to contracts/api.md for request/status definitions, contracts/ui.md for navigation, and data-model.md for account constraints.

Run from the repository root unless a command specifies another directory. The fixture profile is opt-in and must only be used with a disposable local database.

## Start local services

Configure POSTGRES_PASSWORD, PGADMIN_DEFAULT_EMAIL and PGADMIN_DEFAULT_PASSWORD in an untracked .env file as required by the updated Compose configuration. Database name/user remain raumlotse. Run:

```powershell
docker compose up -d db
```

In the backend terminal, set the matching database password and a local acceptance password through hidden prompts. Input is converted only into process-local environment variables for the child Java process; no value is printed or put into a command literal.

```powershell
Set-Location backend
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/raumlotse'
$env:SPRING_DATASOURCE_USERNAME = 'raumlotse'
$dbCredential = [System.Management.Automation.PSCredential]::new('local', (Read-Host 'Local database password' -AsSecureString))
$env:SPRING_DATASOURCE_PASSWORD = $dbCredential.GetNetworkCredential().Password
$attemptKeyCredential = [System.Management.Automation.PSCredential]::new('local', (Read-Host 'Existing local attempt HMAC key (base64)' -AsSecureString))
$env:AUTH_ATTEMPT_HMAC_KEY = $attemptKeyCredential.GetNetworkCredential().Password
$env:SPRING_PROFILES_ACTIVE = 'local,local-auth-fixture'
$env:AUTH_FIXTURE_EMAIL = 'login-check@example.test'
$env:AUTH_FIXTURE_DISPLAY_NAME = 'Login acceptance user'
$loginCredential = [System.Management.Automation.PSCredential]::new('local', (Read-Host 'Local acceptance password' -AsSecureString))
$env:AUTH_FIXTURE_PASSWORD = $loginCredential.GetNetworkCredential().Password
try {
  .\mvnw.cmd spring-boot:run
} finally {
  Remove-Item Env:AUTH_FIXTURE_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:AUTH_ATTEMPT_HMAC_KEY -ErrorAction SilentlyContinue
  $attemptKeyCredential = $null
  $loginCredential = $null
  $dbCredential = $null
}
```

The fixture creates one account only in local configuration. An existing account is never silently reset; reuse the original password or a fresh isolated database/account when repeating validation. Production profile plus fixture must fail closed. When role management is installed, fixture creation must use its transactional Viewer initialization.

In another terminal from repository root:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open the URL printed by Vite (normally http://localhost:5173). Its existing /api proxy forwards session cookies and CSRF requests. Local profile must explicitly allow non-Secure cookies on localhost HTTP; production must require Secure cookies and HTTPS.

## Automated gates

Write failing tests before each implementation slice, record the failure, implement and rerun. After completing the feature:

```powershell
Set-Location backend
.\mvnw.cmd test
```

Docker must be running for existing PostgreSQL Testcontainers tests. Use real containers, not an in-memory substitute for uniqueness/persistence behavior. Existing controller tests must retain security filters and use valid authenticated fixtures plus CSRF for legitimate mutations.

From a separate terminal at repository root:

```powershell
Set-Location frontend
npm ci
npm test
npm run lint
npm run build
```

Expected before release: all existing and new tests pass, no lint/build errors, and each scenario below has recorded evidence. Automated backend and frontend gates pass. Manual browser evidence remains outstanding for the journeys below.

| Automated scenario | Expected proof |
|---|---|
| Valid and incorrect credentials; unknown email | Correct UUID on success; matching generic 401 bodies for wrong password/unknown email |
| Validation and canonicalization | Shared email test vectors, duplicates rejected in PostgreSQL; malformed/type/length failures return safe 400 |
| Exact password matching | Case/space differences rejected; whitespace-only provisioned password accepted; suffix differences after 72 bytes remain distinct; no truncation |
| Hash persistence | No plaintext stored/serialized; versioned hash verifies correctly; unknown encoding fails closed |
| Session persistence/fixation | Next request/reload has same user; login rotates id; old id cannot access protected resources |
| CSRF lifecycle | Bootstrap token permits login; missing/invalid login token denies; pre-login token fails after authentication; fresh token permits authorized writes |
| Access coverage | Anonymous access denied for every current controller route and mutation family, including tokenless writes; deleted-account writes with stale tokens return 401; zero business changes |
| Expiry/invalid session/missing account | Next request is 401; UI clears protected state and redirects |
| Permission integration | Installed role checks remain enforced; if feature 003 is absent, verify a test-only persisted restriction and record actual role-release checks as pending integration |
| Dependency outage | 503 safe retry message; never false invalid-credential response or protected fallback |
| Privacy | Captured success/rejection/validation/outage logs contain no credential, email, id, hash, session or CSRF sentinels |
| UI navigation | Guard loading/finishing-login/unavailable states show no private content; failed token refresh retries without resubmitting credentials; fully ready login -> /; protected 401 -> /login |
| Cooldown threshold and parity | Five mismatches within rolling 15m trigger fixed 15m wait for registered and unknown emails; fifth 401 has retry metadata, subsequent requests are 429 even with correct password |
| Cooldown boundaries and resets | Failure exactly 15m old excluded; expiry at exact deadline permits fresh login; success clears history; invalid input, CSRF rejection and outages do not count |
| Cooldown concurrency | Five concurrent failures cannot lose increments; a correct-password attempt queued behind the fifth fails; different emails remain independent; casing/whitespace variants share history |
| Cooldown lifecycle | Browser/session changes and backend restart with same key retain wait; blocked attempts never extend deadline; existing authenticated sessions remain usable |
| Limiter faults and cleanup | Lock timeout/store outage is 503 with no increment; precommit concurrent requests cannot see authentication; preparation/commit/publication failure leaves no authenticated session; cleanup racing with login cannot delete live history |
| Cooldown UI | Fake-timer countdown, accessible wait message, email-change behavior, explicit retry only; reload cannot bypass backend policy |
| Existing mutations | Authenticated room/catalog create/update/delete still work with CSRF; failed operations are not automatically replayed |

Use deterministic session invalidation or a shortened test-only inactivity timeout rather than waiting 30 minutes in automated tests. For browser expiry acceptance, run with SERVER_SERVLET_SESSION_TIMEOUT=60s and avoid requests/polling for over one minute; restore production default afterward.

## Browser acceptance

1. In a fresh private window, open /rooms directly. Expect /login, no navigation shell or room data. Open /locations and /rooms/new similarly.
2. Confirm labeled email/password fields and keyboard submission. Type, paste and autofill a password: it always remains masked; no reveal control exists.
3. Submit empty/invalid inputs and inspect accessible correction messages. Submit valid-format unknown email and a known email with wrong password: both get the same credential message and remain unauthenticated.
4. Correct the credentials. Start timing from the visible login form: reach / within one minute without assistance (SC-001/005). Confirm the identity using /api/auth/me, then navigate and reload without another login.
5. In browser network/cookie tools, inspect HttpOnly, SameSite, path and session-id rotation without copying values to logs or reports. Verify Secure in the HTTPS deployment and absence of localStorage/sessionStorage authentication tokens.
6. Perform an existing room/catalog mutation using an isolated test record and verify it succeeds. The request includes the fresh CSRF header. A missing-token copy must fail with no state change; do not publish request credentials.
7. Let the short test-only session timeout expire. Next protected navigation/action sends the browser to /login and removes prior private content. Browser Back must not remount it without identity validation.
8. Simulate auth dependency failure in integration tests; verify the UI equivalent with a mocked 503/network failure. It shows temporary unavailability and a retry path, not a wrong-password assertion. Restoring availability permits retry.
9. Capture sanitized test output for privacy assertions. Do not save browser network exports containing credentials/cookies. Confirm /api/health remains public.

## Cooldown acceptance (FR-014 / SC-007)

Use `LoginCooldownIntegrationTest` and `LoginCooldownConcurrencyTest` with disposable PostgreSQL and a stable test HMAC key. Run from backend when Docker is available:

```powershell
.\mvnw.cmd "-Dtest=LoginCooldownIntegrationTest,LoginCooldownConcurrencyTest" test
```

The integration and concurrency classes pass in the complete backend suite. They cover simultaneous failures and shared canonical identities, registered/unknown parity, fixed deadline behavior, repeated blocked requests without deadline extension, continued access for an already-authenticated session, correct-password serialization behind the fifth failure, persistence through a fresh service using the same database/key, and cleanup races. Use barriers/latches and transaction locks for concurrency tests, not timing-dependent sleeps. All protocol cases execute real CSRF/bootstrap requests. Seed fresh histories for independent scenarios; do not expose a production reset endpoint.

1. Against a fresh registered-email history, submit five wrong passwords. Responses 1-4 are generic 401; response 5 is the same generic 401 plus retryAfterSeconds=900 and Retry-After=900. Repeat with a fresh unknown email and compare the same fields/messages.
2. During cooldown, submit the correct password from another browser/session with a valid CSRF token. Expect 429 LOGIN_COOLDOWN with a positive remaining wait. Record the database deadline in the isolated test and prove it never moves after repeated blocked requests.
3. Advance test time to just before and exactly at expiry. Before: blocked. At expiry: valid credentials succeed and old failures no longer count. Test a failure exactly on the rolling-window cutoff and one just inside it.
4. Queue a correct-password request behind the fifth-failure transaction. It must see the committed cooldown and fail. If success commits first, it clears prior failures and remains valid; verify outcomes in actual serialized order. Five parallel failures must not lose increments.
5. Verify another email succeeds independently; equivalent case/whitespace variants share history. Existing authenticated sessions can continue permitted business operations while new logins for that identity are blocked.
6. Restart the backend with the same database/key before expiry: new login remains blocked. Race cleanup with first attempts and active histories; only expired histories are removed. State may outlive its eligibility until cleanup, but must not keep login blocked after expiry.
7. Inject provider/store/lock failures before commit: expect safe 503, unchanged history and no usable authentication. Pause commit and issue a concurrent protected request using the same anonymous session: it must not see prepared authentication. Fail session preparation/publication and verify candidate-session invalidation. Simulate commit-then-disconnect: history may be durable but no authenticated context is published, and the request must not replay. A postcommit publication failure retains the committed reset without overwriting newer attempts. Ordinary credential rejection must commit its increment despite returning 401.
8. Browser check: observe wait/countdown after the fifth failure, unchanged deadline on a second browser, different-email retry, no automatic login at zero, and no private content while blocked. Automated clock tests cover the full 15 minutes; do not shorten production cooldown settings for convenience.

No limiter identity key, raw email, cookie or HMAC secret may appear in captured runtime logs. Extend the existing privacy assertions to 429 and limiter-outage responses. Authentication and limiter storage are separate from session storage: restarting signs out prior sessions but does not clear cooldowns.

## Direct API smoke check

Before login, this must return 401 and no room records; health must remain 200:

```powershell
curl.exe -i http://localhost:8080/api/rooms
curl.exe -i http://localhost:8080/api/health
```

For authenticated API verification, prefer the real-cookie integration tests and browser requests above so passwords/session cookies are not pasted into shell history. Tests must execute the complete csrf -> login -> new csrf -> protected mutation sequence, not just inject a mock principal.

## Release evidence

Record test/build/lint results, red/green evidence, timed browser result, actual cookie flags, and credential/PII absence checks. Document production account provisioning separately before accepting production login. The full feature 003 release gate remains unmet until its directory, role lifecycle and initial Admin prerequisites are delivered. No account administration is implied by this login plan.


