# Data Model: Email and Password Login

## Persistent account

Planned table: `user_account`. Reuse an equivalent identity table if one has merged before implementation; never create parallel identities.

| Field | Type | Rules |
|---|---|---|
| id | UUID primary key | Stable identity; serialize as opaque string, never use email as foreign key |
| email | varchar(254) | Required, canonicalized before storage; unique database constraint |
| display_name | varchar(120) | Required, 1-120 characters after trimming; plain text rendered with escaping |
| password_hash | varchar(512) | Required; complete encoder prefix plus hash; never returned by an API |
| created_at | timestamp with time zone | Required, generated at account creation |

Email policy: strip surrounding whitespace, lowercase with Locale.ROOT, validate a nonempty syntactically valid address up to 254 characters. The database enforces unique canonical values; provisioning and lookup share a normalizer. Use common test vectors for frontend/backend validation. There is no existing account policy/data in the inspected checkout; if another implementation lands first, reconcile its canonicalization before migration instead of changing identities silently.

Password input is transient, not an entity attribute: 1-1024 UTF-16 code units; preserve case, spaces and all characters exactly. Use nonempty/size validation rather than NotBlank or trimming. The account provisioner must observe the same upper bound. Passwords with identical first 72 bytes but different suffixes must remain distinct.

Hash format: `{pbkdf2-sha256-600000-v1}` followed by hexadecimal Spring PBKDF2 output (16-byte random salt, 600,000 HMAC-SHA256 iterations, 256-bit key). No raw password column, pepper secret in source, fixed salt, credential serialization, generated toString containing secrets, or automatic fallback to plaintext. Invalid stored encodings are operational failures, not accepted credentials.

## Authentication state

| Field | Ownership / behavior |
|---|---|
| session id | Opaque servlet-generated identifier, transported only in HttpOnly cookie |
| verified account id | Server-established principal UUID; no browser-supplied identity trusted |
| last access / inactivity limit | Container-managed; expires after 30 minutes without requests |
| security context | Stored through shared repository only after successful authentication |
| CSRF secret | Session-backed; masked request token returned to frontend; reset on authentication |

One account may have several independent sessions. Session storage is in one backend process; restart invalidates all sessions. No session table, persistent login token or role snapshot is introduced. The principal carries account id and minimum safe display information, not password/hash. Load account existence for identity lookup and business access; an account missing from persistence invalidates its authentication. Lookup outages deny access with 503, never fall back to trusted client values.

RoleAssignment/UserRoleState remain owned by feature 003 and reference this UUID when integrated. Sign-in does not add, remove or cache authoritative roles. Role-restricted services must read current assignments per request.

## State transitions

1. Anonymous -> anonymous CSRF session: GET /api/auth/csrf. No protected access granted.
2. Anonymous -> authenticated: validated credentials, serialized cooldown eligibility/provider verification, staged session-id change, invalidate old CSRF token, commit cleared attempt history, then publish the authenticated context only after commit confirmation before returning user summary; frontend obtains fresh CSRF then navigates to /.
3. Anonymous -> anonymous on bad credentials, active cooldown, invalid input, CSRF failure or service outage. An anonymous CSRF session may remain, but no authenticated context is saved.
4. Authenticated -> authenticated: permitted navigation/reload uses existing cookie and refreshes inactivity time. POST login while already authenticated returns 409 without switching accounts.
5. Authenticated -> anonymous: inactivity expiry, invalid session id, missing account or backend restart. Next protected request is denied; frontend clears private state and opens /login.
6. Unavailable identity check -> unavailable frontend state: no private component renders; retry is explicit. Do not treat an outage as proof that credentials are incorrect.

## Migration and fixtures

Allocate next free Flyway version after V3; create account and limiter tables with canonical-email uniqueness and limiter constraints, without credential seed rows. Follow existing Flyway startup integration and JPA repository conventions. Migrations never insert test passwords, production accounts or Admin roles.

An opt-in `local-auth-fixture` profile creates one local acceptance account from AUTH_FIXTURE_EMAIL, AUTH_FIXTURE_DISPLAY_NAME and AUTH_FIXTURE_PASSWORD. All are required; no defaults. Use the common normalizer/encoder. Existing account: leave credentials untouched; fail safely if a fixture conflicts, never silently reset it. Enable only with the local profile, never production. Tests seed isolated accounts programmatically into disposable PostgreSQL. If role management is installed, creation must also execute its transactional Viewer initialization before visibility; no role mutation occurs at sign-in.

Production account creation and initial Admin provisioning remain separate operational delivery. No account deletion/deactivation API is added. If lifecycle operations are introduced later, they must coordinate session invalidation and feature 003's last-Admin safeguards.

## Persistent login attempt state

Planned table: login_attempt_state, independent of user_account so unknown emails receive identical enforcement. No user foreign key and no cleartext email column.

| Field | Type | Rules |
|---|---|---|
| identity_key | bytea primary key | 32-byte HMAC-SHA256 of canonical email under stable AUTH_ATTEMPT_HMAC_KEY; never logged or returned |
| failure_times | timestamptz[] | Non-null, default empty; at most five timestamps, no null entries; chronological order maintained by service |
| blocked_until | timestamptz nullable | Fixed deadline set only by fifth counted failure; null outside cooldown |
| expires_at | timestamptz | Indexed cleanup eligibility: blocked_until during cooldown, otherwise latest failure +15m; now for empty history |

Rows contain sensitive pseudonymous state with short retention; HMAC is not anonymization. The secret is not stored in PostgreSQL and is shared across deployments/restarts. No per-account existence signal or failure count is exposed. State is bounded per key; row count follows distinct attempted emails, so periodic cleanup and database-capacity monitoring are required. Storage errors deny login with 503 instead of discarding active rows.

## Serialized attempt algorithm

1. Reject invalid input, invalid CSRF or already-authenticated login before touching history. Normalize email and derive identity_key. Each eligible attempt uses one READ COMMITTED transaction.
2. Atomically insert-or-no-op-update the row with RETURNING and retain its lock through verification and outcome staging. Parameterize all SQL. This also handles a row deleted by concurrent cleanup; no separate existence-check race. Do not use SKIP LOCKED for login. Bound lock wait to five seconds and statements to ten seconds; timeouts return 503 without counting a failure.
3. Read actual database clock_timestamp() after lock acquisition. If blocked_until > now, return a blocked outcome with ceiling(remaining seconds), at least 1, without password verification or changing history/deadline. The no-op upsert may change physical row metadata, never logical history or expiry.
4. If blocked_until <= now, clear the previous cooldown and all triggering failures. Otherwise retain only timestamps strictly greater than now-15m. A failure exactly 15 minutes old is outside the rolling window.
5. Verify credentials under the row lock, including dummy-hash verification for unknown emails. Resample database time after verification and prune the window again before recording a failure. No other attempt for this identity can authenticate in parallel.
6. Credential mismatch: append current failure timestamp. At count five set blocked_until = failure timestamp +15m and expires_at to that deadline. Before five set expires_at to the latest failure +15m. Commit the failure outcome even though its HTTP result is 401: do not throw an exception that rolls back the counter. The fifth 401 carries cooldown metadata; subsequent blocked attempts receive 429.
7. Verified credentials: clear all failures/deadline, set expires_at to now, and prepare a principal/rotate the anonymous session while retaining the lock. Keep the authenticated context unpublished and invisible to all other requests. Only after confirmed database commit save it to shared session storage and emit success. A failure to prepare, confirm commit or publish invalidates the candidate session, clears context, expires its cookie and returns 503; no usable authentication may exist before confirmed commit. If publication fails after commit, the committed history reset remains; never restore an old history over newer attempts. Existing sessions in other browsers are untouched. Response-loss after a committed success does not turn it into a counted failure.
8. Dependency/provider failures, unusable hashes and known rolled-back transactions return 503 with no failure increment. A lost commit acknowledgement is different: history may already be durable. Deny authentication with 503, never replay the attempt or increment for the outage, and preserve actual committed state rather than promising rollback. A success committed before a later cooldown is already admitted and remains valid.

Window uses (now-15m, now]; cooldown uses [fifth failure, blocked_until). Exact expiry permits a new attempt and starts with cleared history. Failure timestamp is verification-completion time. Database time is the production authority; use an injectable time abstraction with controllable integration-test values without a public clock endpoint.

## Cleanup and lifecycle

On startup and every five minutes, clean at most 500 eligible rows per run using a transaction and FOR UPDATE SKIP LOCKED; recheck no live history/cooldown under the lock before deleting. Skip active login rows and retry on a later run. Empty reset rows may be deleted immediately when the job reaches them. Correctness and retry eligibility never wait for cleanup. Tests must race deletion with upsert and prove no live failures are lost.

No account-creation foreign key means creating an account for a previously unknown email does not bypass that email's active cooldown. No session or account role fields are changed by starting/expiring a cooldown. Backend restart retains limiter rows and requires the same HMAC secret; it may invalidate servlet sessions as already documented.