# Research: Email and Password Login

Inspected manifests, current controllers, Problem/error handling, Flyway, frontend routing/client, Compose, constitution and feature 003's handoff. This checkout has no account model, security configuration, login UI or role-management implementation. Research decisions below resolve all technical unknowns before Phase 1.

## Authentication lifecycle

**Decision**: Boot-managed Spring Security, database account lookup and DaoAuthenticationProvider. Keep JSON login through a small controller/service using Bean Validation. After authentication invoke the shared CompositeSessionAuthenticationStrategy (ChangeSessionIdAuthenticationStrategy, then CsrfAuthenticationStrategy), create a fresh context and explicitly save it through the filter chain's shared SecurityContextRepository. Clear request context on failure. Disable default form login, HTTP Basic, remember-me, logout and request-cache redirects.

**Rationale**: Fits the JSON client while delegating security primitives to Spring. Setting SecurityContextHolder alone does not persist login.

**Alternatives considered**: Form login simplifies lifecycle but changes the request convention. JWT/browser storage adds unneeded storage/revocation concerns. Custom password verification is unnecessary.

Sources: [Session lifecycle](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html), [form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html), [managed dependencies](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html).

## Accounts and passwords

**Decision**: UUID account id, display name, canonical unique email and prefixed hash. Strip surrounding email whitespace and lowercase with Locale.ROOT consistently at provisioning and login. There is no implemented policy to inherit. Passwords remain exact; accept 1-1024 UTF-16 code units, including whitespace-only values if provisioned, without normalization or truncation.

Use DelegatingPasswordEncoder with encoding id `pbkdf2-sha256-600000-v1`, backed by Spring Pbkdf2PasswordEncoder: empty additional secret, random 16-byte salt, 600,000 iterations, HMAC-SHA256, 256-bit key, hexadecimal output. Store the full prefix. Invalid/unknown stored encoding fails closed with a sanitized unavailable response. Unknown email uses dummy-hash verification to avoid a trivial fast path, without claiming identical network timing.

**Rationale**: PBKDF2 uses the Java runtime and avoids BCrypt's byte-length limit. Spring's v5_8 default uses 310,000 iterations; a custom 600,000 configuration must use its own identifier. Benchmark the chosen cost during implementation without silently weakening it.

**Alternatives considered**: BCrypt requires a restrictive input boundary; Spring Argon2 needs an extra cryptography dependency. Plaintext, reversible encryption and fast unsalted hashes are rejected.

Sources: [Password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html), [PBKDF2 API](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/crypto/password/Pbkdf2PasswordEncoder.html).

## Session lifetime and deployment

**Decision**: Single-instance servlet sessions; 30-minute inactivity timeout. Session-only JSESSIONID cookie, Path=/, HttpOnly, SameSite=Lax, Secure except explicit localhost HTTP configuration; cookie-only tracking. Restart invalidates sessions. Frontend and API share an HTTPS origin; retain Vite's existing /api proxy in development. No broad credentialed CORS.

**Rationale**: Meets reload/navigation continuity with minimal infrastructure. Browser close is not a guaranteed logout because browsers can restore session cookies. Avoid background polling that keeps idle sessions alive.

**Alternatives considered**: Distributed sessions, remember-me and fixed absolute expiry are unnecessary for this scope. Thirty minutes is an engineering default, not a measured business requirement.

Source: [Boot session properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html).

## CSRF and client integration

**Decision**: Session-backed CSRF repository with default XOR handling. Public GET /api/auth/csrf returns resolved masked token/headerName. Keep token in memory; send it unchanged on every unsafe request including login. Fetch a fresh token after successful authentication. No-store responses; no automatic replay of failed business mutations.

**Rationale**: Spring documents this endpoint pattern; it needs no readable token cookie. A pre-login CSRF session remains anonymous. Return the resolved request token, not the raw repository token.

**Alternatives considered**: The SPA cookie helper is valid but unnecessary here. Disabling CSRF for login or JSON and relying only on SameSite are rejected.

Source: [CSRF token endpoint and refresh](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## UI and validation

**Decision**: Public /login, authenticated landing /. AuthProvider states: loading, finishing-login, authenticated, anonymous, unavailable; bootstrap /api/auth/me and CSRF before publishing readiness. After login, finishing-login holds the server identity until token renewal succeeds without mounting the protected shell. Guard the entire existing shell. Use native labeled email/password inputs and autofill hints with no reveal toggle. Add Zod safeParse for login schema validation and repeat constraints with Bean Validation.

**Rationale**: Uses current React Router/styles and satisfies the constitution's schema requirement. Loading or unavailable states display no protected content. A network failure is not an invalid-credential error.

**Alternatives considered**: Remembered return URLs and a new UI framework add scope; rendering the shell before identity resolves leaks content; ad hoc validation alone does not meet the schema requirement.

Source: [Zod safeParse](https://zod.dev/basics).

## Provisioning and roles

**Decision**: This feature owns account storage/lookup. Supply an opt-in local-only fixture profile with environment-provided credentials, no defaults and no public creation endpoint. Production account provisioning remains an external prerequisite using the same canonicalization/encoder. Role management later consumes UUID identities and enforces current persisted roles; initial Admin setup remains separate.

**Rationale**: Makes login testable without silently adding registration or satisfying all feature 003 prerequisites. If an account implementation lands first, adapt/reuse it rather than create a duplicate table.

**Alternatives considered**: Hardcoded users, implicit Admin, public provisioning and a second role store are rejected.

Repository sources: `specs/003-user-role-management/contracts/authentication.md` and `data-model.md`.

## Errors, privacy and migration

**Decision**: Preserve Problem shape and add optional machine-readable code. Fixed authentication messages and field names only; no rejected values. Controller and filter handlers share the format. Allowlist event/outcome and random correlation ids; exclude credentials, email, account ids, cookies, hashes, tokens, request bodies and exception text. Update existing mutation clients and tests in the same release.

**Rationale**: Existing general exception logging can expose database exception messages; auth errors need a sanitized path. Existing API bodies remain compatible, while access restrictions require a documented migration.

**Alternatives considered**: Account-specific errors disclose existence; redirects from APIs confuse clients; raw exception logging risks personal-data disclosure.

## Clarified attempt limit: persistence, concurrency and privacy

**Decision**: Enforce FR-014 with one PostgreSQL login_attempt_state row per HMAC-SHA256 of the canonical email, no user foreign key. The same stable secret and canonicalizer apply to registered and unknown emails. Keep at most five failure timestamps, a cooldown deadline and cleanup eligibility. Persist state across backend restarts; use the existing database rather than another cache service.

Each syntactically valid, CSRF-valid anonymous login obtains the row through atomic INSERT ON CONFLICT DO UPDATE (no-op) RETURNING in a READ COMMITTED transaction. This obtains the current row and its lock even when cleanup or another first attempt races with it. Serialize all credential verification and outcomes for that email under this lock; unrelated emails use different rows. Login never uses SKIP LOCKED. Sample clock_timestamp() after acquiring the lock and again after verification, so waiting and hashing do not use transaction-start time. Discard failures at or before now minus 15 minutes: the rolling interval is (now-15m, now]. A fifth failure starts a fixed 15-minute deadline; attempts during it do not verify passwords or modify the deadline/history.

**Rationale**: An in-memory counter resets on restart; a user-id key cannot cover unknown emails; check-then-update outside the lock allows simultaneous attempts to bypass the threshold. PostgreSQL already exists. HMAC avoids retaining submitted unknown addresses in cleartext; the key and timestamps remain sensitive pseudonymous data, not anonymous analytics.

**Alternatives considered**: IP-only limits do not implement the chosen per-email policy. Fixed time buckets differ from the rolling window. Redis adds infrastructure. Account locked flags cannot represent unknown emails and conflate login cooldown with account lifecycle. A global lock would delay unrelated accounts.

Normal failed credentials are returned as an outcome, not thrown out of the transaction: their counter changes must commit. Outages, invalid stored hashes and lock/statement timeouts roll back and return sanitized 503 without incrementing history. Use a five-second lock wait bound and a ten-second statement timeout as initial engineering limits; measure hashing/connection usage during validation. Prepare an unpublished principal and rotate only the anonymous session while the row is held; never save an authenticated context into shared servlet-session storage before commit confirmation. After confirmed commit, publish the context and send success. If preparation, commit confirmation or publication fails, invalidate the candidate session, clear context, expire its cookie and return 503. Concurrent protected requests must never observe precommit authentication. Servlet session storage and PostgreSQL are not one atomic resource; compensation and failure-injection tests are mandatory. The successful admission transaction clears history; if later context publication fails, that committed reset remains and must not be overwritten by compensation racing with newer attempts. A login committed before a later cooldown is already admitted and is not revoked by it.

HTTP policy: failures 1-5 remain generic 401 INVALID_CREDENTIALS. The fifth additionally reports a 900-second cooldown through retryAfterSeconds and Retry-After; further attempts during it return 429 LOGIN_COOLDOWN with the positive ceiling of remaining seconds. Both responses reveal no account existence. The browser shows the wait, never retries automatically and never treats the countdown as authoritative permission. 429 with Retry-After follows the HTTP rate-limit convention; returning metadata on the triggering 401 preserves the spec's generic credential rejection.

Cleanup uses bounded batches with row locks, SKIP LOCKED and eligibility recheck; delete only expired cooldown rows or rows with no live failures. Request-time pruning makes eligibility independent of the cleanup schedule. Run at startup and every five minutes; use batches of at most 500, one batch per scheduled run. Never evict active entries to impose a memory cap. Resource/storage failures fail closed with 503, not bypass. Tests cover cleanup racing with a first attempt and retained history across restart.

AUTH_ATTEMPT_HMAC_KEY is a required stable base64-encoded random secret with at least 32 decoded bytes, injected from untracked environment/deployment secrets, never generated at ordinary startup or logged. All processes and restarts use the same value. Rotation is a separate maintenance procedure: pause new logins, drain in-flight attempts, then wait at least 30 minutes, allow every preexisting window/cooldown to expire, purge expired limiter rows and switch the key consistently before resuming. Existing authenticated sessions need not be revoked. No online rotation scheme or extra public endpoint is added.

Tradeoff: the selected per-email rule lets someone who knows an email cause a temporary denial of new logins for that identity. A cooldown never revokes existing sessions and blocked attempts do not extend it. This implements the user's selected policy without adding administrator unlocking, CAPTCHA or IP-based policy.

Sources: [PostgreSQL atomic upsert](https://www.postgresql.org/docs/17/sql-insert.html), [row locking](https://www.postgresql.org/docs/17/explicit-locking.html), [database time functions](https://www.postgresql.org/docs/17/functions-datetime.html), [cleanup locking](https://www.postgresql.org/docs/17/sql-select.html), [HTTP 429 and Retry-After](https://www.rfc-editor.org/rfc/rfc6585.html#section-4).

Commit uncertainty: a lost database commit acknowledgement can occur after history changes are durable. Return 503 without publishing authentication and never automatically replay the credential attempt. Do not claim that already committed history was rolled back or add a failure because of the outage. Ordinary provider/lookup/lock failures before a commit leave history unchanged; uncertain commit follows actual durable state. Test both commit-then-disconnect and rollback-then-disconnect outcomes.

## Resolution

All technical decisions are resolved. Production provisioning and initial Admin operations are explicit delivery dependencies, not unresolved design questions. Official documentation informs design; exact managed APIs must still compile and pass focused integration tests during implementation.
