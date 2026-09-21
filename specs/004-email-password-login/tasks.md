# Tasks: Email and Password Login

**Input**: Design documents in `specs/004-email-password-login/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Required by the project constitution's test-first principle. Each story's tests must fail before its implementation tasks begin.

**Organization**: Tasks are grouped by the specification's three independently testable P1 user stories. Story phases are sequenced where the authentication foundation, account verification, and session state create real dependencies.

## Phase 1: Setup

**Purpose**: Add only the dependencies required by the plan.

- [X] T001 Add Boot-managed Spring Security runtime and Spring Security test dependencies in `backend/pom.xml`; do not override Boot dependency versions.
- [X] T002 Add Zod to frontend runtime dependencies in `frontend/package.json` and `frontend/package-lock.json`.

## Phase 2: Foundational Security

**Purpose**: Establish a default-deny API policy and common response/CSRF behavior before any story exposes authenticated functionality. No business route becomes public by default.

- [X] T003 [P] Write failing MockMvc tests for anonymous business GET/write denial, JSON 401 responses, authenticated unsafe-request CSRF rejection, public `/api/health`, and public auth route matching in `backend/src/test/java/at/mci/igp/raumlotse/security/SecurityPolicyTest.java`.
- [X] T004 Implement the default-deny Spring filter chain, allowlist only health and planned auth endpoints, require authentication on all business `/api/**`, disable Basic/form/logout, and preserve CSRF in `backend/src/main/java/at/mci/igp/raumlotse/config/SecurityConfig.java`.
- [X] T005 Write failing response-handler tests for 401 anonymous business writes, 403 public login CSRF failures, 403 authenticated CSRF failures, stable codes, and no redirects in `backend/src/test/java/at/mci/igp/raumlotse/security/SecurityResponseHandlerTest.java`.
- [X] T006 Implement shared sanitized `Problem` serialization and API authentication/access-denial/CSRF handlers in `backend/src/main/java/at/mci/igp/raumlotse/dto/Problem.java` and `backend/src/main/java/at/mci/igp/raumlotse/config/SecurityConfig.java`; never include submitted values or exception text.
- [X] T007 Write failing CSRF bootstrap tests for anonymous session creation, resolved masked token/header name, no-store response, and invalid/missing login tokens in `backend/src/test/java/at/mci/igp/raumlotse/controller/CsrfControllerTest.java`.
- [X] T008 Add the public CSRF bootstrap endpoint and session-backed token serialization in `backend/src/main/java/at/mci/igp/raumlotse/controller/CsrfController.java`; keep Spring's CSRF protection enabled.

**Checkpoint**: Tests prove default deny and secure CSRF handling. Subsequent stories can build authenticated behavior without opening business APIs.

## Phase 3: User Story 1 - Log in with valid credentials (Priority: P1)

**Goal**: A registered user can submit exact matching credentials, receive a persistent server-side session, and reach the authenticated application.

**Independent Test**: With the local fixture account, valid CSRF and a clean limiter history, log in, verify the response identity and rotated cookie, call `/api/auth/me`, navigate, and reload while the session is valid.

### Tests for User Story 1 (write first)

- [X] T009 [P] [US1] Write failing account persistence tests for UUID identity, canonical unique email, required display name and password hash, and no hash in serialized responses in `backend/src/test/java/at/mci/igp/raumlotse/repository/UserAccountRepositoryTest.java`.
- [X] T010 [P] [US1] Write failing credential tests for identical canonicalization, exact case/whitespace preservation, 1-1024 UTF-16 code-unit inputs, dummy-hash verification for unknown email, and passwords differing after byte 72 in `backend/src/test/java/at/mci/igp/raumlotse/service/AccountAuthenticationServiceTest.java`.
- [X] T011 [P] [US1] Write failing endpoint integration tests for CSRF -> valid login -> fresh CSRF -> `/api/auth/me`, identity-safe response, session-id rotation, pre-login id/token rejection, and 30-minute inactivity expiry in `backend/src/test/java/at/mci/igp/raumlotse/AuthenticationIntegrationTest.java`.
- [X] T012 [P] [US1] Write failing browser/component tests for masked password, autofill labels, pending/loading/unavailable states, successful redirect to `/`, reload identity, and accessible feedback in `frontend/src/pages/LoginPage.test.tsx` and `frontend/src/auth/AuthProvider.test.tsx`.

### Implementation for User Story 1

- [X] T013 [P] [US1] Create the `user_account` Flyway migration and JPA mapping: `id UUID primary key`; `email varchar(254) required and unique after canonicalization`; `display_name varchar(120) required, 1-120 characters after trimming`; `password_hash varchar(512) required`; `created_at timestamp with time zone required`; add no seeded users in `backend/src/main/resources/db/migration/V4__create_user_account.sql` and `backend/src/main/java/at/mci/igp/raumlotse/domain/UserAccount.java`.
- [X] T014 [P] [US1] Implement parameterized canonical-email lookup and uniqueness handling for `strip surrounding whitespace`, `lowercase with Locale.ROOT`, and valid nonempty email up to 254 characters in `backend/src/main/java/at/mci/igp/raumlotse/repository/UserAccountRepository.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/EmailCanonicalizer.java`.
- [X] T015 [US1] Implement the versioned `pbkdf2-sha256-600000-v1` encoder with `16-byte random salt`, `600,000 HMAC-SHA256 iterations`, and `256-bit key`, and verify without trimming or truncating passwords in `backend/src/main/java/at/mci/igp/raumlotse/config/PasswordEncoderConfig.java`.
- [X] T016 [US1] Implement the account authentication provider/service with canonical email lookup, exact credential matching, unknown-user dummy-hash verification, and fail-closed stored-hash errors in `backend/src/main/java/at/mci/igp/raumlotse/service/AccountAuthenticationService.java`.
- [X] T017 [US1] Implement validated JSON login and `/api/auth/me` contracts: request email/password only, password 1-1024 UTF-16 units unchanged, safe Problem codes/messages, and AuthenticatedUser `{userId, displayName}` in `backend/src/main/java/at/mci/igp/raumlotse/controller/AuthController.java` and `backend/src/main/java/at/mci/igp/raumlotse/dto/LoginRequest.java`.
- [X] T018 [US1] Implement login session lifecycle: require session-bound CSRF, rotate anonymous session, invalidate prior CSRF, and publish the authenticated context only after the credential/limiter transaction commits in `backend/src/main/java/at/mci/igp/raumlotse/service/LoginService.java` and `backend/src/main/java/at/mci/igp/raumlotse/config/SecurityConfig.java`.
- [X] T019 [US1] Configure session inactivity `30 minutes`, session-only cookie `HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure` outside explicit local HTTP, cookie-only tracking, and no remember-me in `backend/src/main/resources/application.yaml`.
- [X] T020 [US1] Add the opt-in `local-auth-fixture` profile requiring `AUTH_FIXTURE_EMAIL`, `AUTH_FIXTURE_DISPLAY_NAME`, and `AUTH_FIXTURE_PASSWORD`, with no defaults, no credential reset, and no production activation in `backend/src/main/java/at/mci/igp/raumlotse/config/LocalAuthFixtureConfiguration.java`.
- [X] T021 [US1] Implement safe authenticated-user and CSRF client calls with same-origin credentials, in-memory token and post-login token refresh in `frontend/src/API/auth.ts` and `frontend/src/API/client.ts`.
- [X] T022 [US1] Implement the auth provider states `loading`, `finishing-login`, `authenticated`, `anonymous`, `unavailable`; publish identity only after `/api/auth/me` plus CSRF succeeds in `frontend/src/auth/AuthProvider.tsx`.
- [X] T023 [US1] Implement the public `/login` route, authenticated home-shell gate, and labeled login form with Zod constraints, type=password/current-password autofill, accessible errors, exact password preservation, and explicit success navigation to `/` in `frontend/src/App.tsx`, `frontend/src/pages/LoginPage.tsx`, and `frontend/src/pages/LoginPage.css`.

**Checkpoint**: Valid login, session persistence, reload and masked input work independently; cooldown tests are not yet complete.

## Phase 4: User Story 2 - Understand failed login and cooldown feedback (Priority: P1)

**Goal**: Invalid credentials fail generically; the fifth failure in a rolling 15-minute window starts a fixed 15-minute per-email cooldown for registered and unknown emails.

**Independent Test**: With isolated histories, compare registered/unknown failures, drive five failures, verify fifth 401 metadata then 429 denials, wait/advance through expiry, and successfully retry without administrator intervention.

**Dependencies**: Requires User Story 1's canonical identity, credential provider, login transaction, and session lifecycle. Cooldown implementation must be complete before unrestricted login is released.

### Tests for User Story 2 (write first)

- [X] T024 [P] [US2] Write failing cooldown contract tests for generic first-four 401s, fifth 401 with `retryAfterSeconds=900` and `Retry-After=900`, subsequent 429, matching registered/unknown messages, no deadline extension, and no authentication during cooldown in `backend/src/test/java/at/mci/igp/raumlotse/LoginCooldownIntegrationTest.java`.
- [X] T025 [P] [US2] Write failing controllable-clock tests for the rolling window `(now-15m, now]`, exclusion at exactly 15 minutes, cooldown interval `[fifth failure, blocked_until)`, success reset, expired-history reset, and validation/outage exclusions in `backend/src/test/java/at/mci/igp/raumlotse/LoginCooldownBoundaryTest.java`.
- [X] T026 [P] [US2] Write failing concurrency tests for five simultaneous failures, a correct-password request queued behind the fifth failure, independent email keys, equivalent email variants, and cleanup racing with a first attempt in `backend/src/test/java/at/mci/igp/raumlotse/LoginCooldownConcurrencyTest.java`.
- [X] T027 [P] [US2] Write failing privacy/rollback tests proving limiter/store/lock/provider failures return safe 503, no outage-added failures, unknown-user parity, no HMAC/email/password diagnostics, and no precommit authentication visible to concurrent protected requests in `backend/src/test/java/at/mci/igp/raumlotse/LoginCooldownFailureTest.java`.
- [X] T028 [P] [US2] Write failing UI tests for fifth-failure metadata, 429 countdown, email switching, focus/visibility timer recomputation, no polling/automatic retry, and explicit retry at expiry in `frontend/src/pages/LoginPage.cooldown.test.tsx`.

### Implementation for User Story 2

- [X] T029 [P] [US2] Create the `login_attempt_state` migration with `identity_key bytea primary key`, `failure_times timestamptz[] not null default empty` limited to five non-null ordered entries, nullable `blocked_until timestamptz`, and indexed `expires_at timestamptz` in `backend/src/main/resources/db/migration/V5__create_login_attempt_state.sql`.
- [X] T030 [US2] Require stable `AUTH_ATTEMPT_HMAC_KEY` configuration with base64 decoding and at least 32 random bytes; fail startup when absent/malformed and never generate/log it in `backend/src/main/java/at/mci/igp/raumlotse/config/LoginAttemptKeyConfig.java` and `backend/src/main/resources/application.yaml`.
- [X] T031 [US2] Implement canonical-email HMAC-SHA256 identity derivation without storing cleartext email, logging keys, accepting key defaults, or creating account foreign keys in `backend/src/main/java/at/mci/igp/raumlotse/service/LoginAttemptIdentity.java`.
- [X] T032 [US2] Implement parameterized atomic `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` row acquisition and same-email locking in a `READ COMMITTED` transaction; sample database `clock_timestamp()` after lock and after verification in `backend/src/main/java/at/mci/igp/raumlotse/repository/LoginAttemptStateRepository.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/LoginAttemptService.java`.
- [X] T033 [US2] Enforce the rolling failure window and fixed cooldown under the row lock: keep at most five timestamps, discard failures at or before `now - 15 minutes`, block from the fifth failure for exactly 15 minutes, skip credential verification during cooldown, and do not extend blocked attempts in `backend/src/main/java/at/mci/igp/raumlotse/service/LoginAttemptService.java`.
- [X] T034 [US2] Commit wrong-credential outcomes as data rather than exceptions; keep context unpublished until commit confirmation, deny with 503 on dependency/known rollback/commit uncertainty, and never replay or claim rollback when commit acknowledgement is lost in `backend/src/main/java/at/mci/igp/raumlotse/service/LoginService.java`.
- [X] T035 [US2] Return 401 INVALID_CREDENTIALS on attempts one through five; fifth adds `retryAfterSeconds` and `Retry-After`; return generic 429 LOGIN_COOLDOWN with positive ceiling remaining seconds on blocked attempts in `backend/src/main/java/at/mci/igp/raumlotse/controller/AuthController.java` and `backend/src/main/java/at/mci/igp/raumlotse/dto/Problem.java`.
- [X] T036 [US2] Add fail-closed five-second row-lock and ten-second statement bounds, and clean at most 500 eligible rows per transaction at startup/every five minutes using `FOR UPDATE SKIP LOCKED` and eligibility recheck in `backend/src/main/java/at/mci/igp/raumlotse/service/LoginAttemptCleanupService.java` and `backend/src/main/resources/application.yaml`.
- [X] T037 [US2] Extend sanitized structured outcome logging for credential failures, cooldowns, limiter failures, cleanup and commit uncertainty while excluding emails, identity keys, timestamps, passwords, hashes, sessions and tokens in `backend/src/main/java/at/mci/igp/raumlotse/service/LoginAttemptService.java` and `backend/src/test/java/at/mci/igp/raumlotse/AuthenticationLoggingTest.java`.
- [X] T038 [US2] Extend shared frontend errors to expose `retryAfterSeconds` and `Retry-After` only to login handling, without global logout or request replay in `frontend/src/API/client.ts` and `frontend/src/API/auth.ts`.
- [X] T039 [US2] Implement the per-canonical-email accessible cooldown message/countdown, clear the password, avoid storage/polling/automatic retries, and enable explicit retry at expiry in `frontend/src/pages/LoginPage.tsx` and `frontend/src/pages/LoginPage.css`.

**Checkpoint**: Fifth failure, registered/unknown parity, concurrency, outage behavior, expiry and UI wait handling meet FR-014 and SC-007.

## Phase 5: User Story 3 - Keep protected areas inaccessible without login (Priority: P1)

**Goal**: Anonymous/expired users cannot read protected content or mutate application data by route, direct URL or API bypass; authenticated users retain installed permission checks.

**Independent Test**: Test every current controller route and React route while anonymous and expired; verify zero data/change, login redirection, valid session continuity, CSRF protection, and existing role-denial behavior.

**Dependencies**: Foundational default-deny and User Story 1 verified sessions; can proceed after those complete, in parallel with cooldown implementation for route-guard portions. End-to-end suite and production release include User Story 2.

### Tests for User Story 3 (write first)

- [X] T040 [P] [US3] Write failing parameterized access tests for every current rooms/buildings/floors/equipment-types GET and mutation route, anonymous tokenless writes returning 401, authenticated missing-token writes returning 403, no state change, and health remaining public in `backend/src/test/java/at/mci/igp/raumlotse/security/ProtectedBusinessRouteTest.java`.
- [X] T041 [P] [US3] Write failing route-guard tests for `/`, `/locations`, `/rooms`, `/rooms/new`, `/rooms/:roomId`, login redirection, loading/error states, expiry, browser Back, and clearing mounted private data in `frontend/src/App.protected-routes.test.tsx`.
- [ ] T042 [P] [US3] Write failing tests proving login does not grant/change roles and installed role restrictions still deny unauthorized authenticated users in `backend/src/test/java/at/mci/igp/raumlotse/security/ExistingPermissionIntegrationTest.java`.

### Implementation for User Story 3

- [X] T043 [US3] Protect every existing business controller and future `/api/**` handler by default, exempting only documented health/auth endpoints; preserve exact Problem 401/403 precedence in `backend/src/main/java/at/mci/igp/raumlotse/config/SecurityConfig.java`.
- [X] T044 [US3] Add verified-account existence validation after context loading and before CSRF classification; clear missing accounts and fail closed with 503 on lookup outage in `backend/src/main/java/at/mci/igp/raumlotse/config/SecurityConfig.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/CurrentAccountFilter.java`.
- [X] T045 [US3] Update legitimate existing room/catalog controller and integration tests to authenticate and supply CSRF while retaining the real filter chain in `backend/src/test/java/at/mci/igp/raumlotse/controller/RoomControllerTest.java`, `backend/src/test/java/at/mci/igp/raumlotse/controller/BuildingControllerTest.java`, `backend/src/test/java/at/mci/igp/raumlotse/controller/FloorControllerTest.java`, and `backend/src/test/java/at/mci/igp/raumlotse/controller/EquipmentTypeControllerTest.java`.
- [X] T046 [US3] Extend the US1 shell guard to every existing and future business route, verify direct navigation, expiry, browser Back and clear mounted/cached private state on protected 401 in `frontend/src/App.tsx` and `frontend/src/auth/RequireAuth.tsx`.
- [X] T047 [US3] Update the shared API client to include same-origin session cookies and fresh CSRF headers on every unsafe business request, distinguish login 401/429 from protected 401, and never automatically replay mutations in `frontend/src/API/client.ts`.
- [ ] T048 [US3] Exercise a test-only persisted permission restriction and verify no role claims from login or frontend can authorize an endpoint in `backend/src/test/java/at/mci/igp/raumlotse/security/ExistingPermissionIntegrationTest.java`; record feature 003's production role integration as a release prerequisite.

## Phase 6: Polish and Cross-Cutting Concerns

**Purpose**: Finish deployment hygiene, end-to-end verification and documented rollout gates.

- [X] T049 [P] Write failing configuration tests rejecting production fixture activation and committed database/pgAdmin credential defaults in `backend/src/test/java/at/mci/igp/raumlotse/config/AuthenticationConfigurationTest.java` and `backend/src/test/java/at/mci/igp/raumlotse/ConfigurationPrivacyTest.java`.
- [X] T050 Externalize required database/pgAdmin credentials, keep local values in untracked configuration, and document the stable HMAC key, local fixture and session-cookie deployment settings in `docker-compose.yml`, `backend/src/main/resources/application.yaml`, `.env.example`, `.gitignore`, and `README.md`.
- [ ] T051 Run the complete backend JUnit/Testcontainers suite and verify red/green evidence, login cooldown boundary/concurrency/failure-injection tests, sanitized log capture and all protected routes in `specs/004-email-password-login/quickstart.md`.
- [ ] T052 Run `npm test`, `npm run lint`, and `npm run build`; record browser timing, masked typing/paste/autofill, cooldown countdown, reload, expiry and protected-content results in `specs/004-email-password-login/quickstart.md`.
- [X] T053 Confirm feature 003 integration gates for real provisioned accounts, stable user-id mapping, per-request persisted role enforcement, initial Admin operation and account lifecycle coordination before enabling role-managed production routes in `specs/004-email-password-login/contracts/integration.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Can start immediately; dependencies must resolve before the security tests compile.
- **Foundational Security (Phase 2)**: Depends on Phase 1; default-deny and common error/CSRF responses block all story implementation.
- **User Stories (Phase 3+)**: US1 depends on the security foundation. US2 depends on US1's identity and login transaction. US3's access-guard tests/implementation can proceed after the foundation and US1, in parallel with US2; release validation depends on all three.
- **Polish (Phase 6)**: Depends on all three stories; cannot claim production auth release until required environment configuration and account-provisioning prerequisites are met.

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2. Provides account verification and session identity.
- **US2 (P1)**: Starts after US1 because the limiter serializes credential verification and controls session creation.
- **US3 (P1)**: Guard work starts after Phase 2 and integration requires US1. It can run alongside US2 once verified identity exists. US3's permission checks preserve any installed policy; feature 003 remains a production integration dependency.

### Parallel Opportunities

- T003, T005 and T007 are different foundational test files and can be drafted together, but each corresponding implementation waits for its own test to fail.
- T009-T012 are independent US1 tests and can be written in parallel.
- After Phase 2 and US1, US2 limiter work and US3 protected-route work can proceed in parallel when distinct files are assigned. T024-T028 and T040-T042 are story-local test groups with separate files.
- T049 and frontend regression validation T052 can proceed in parallel after feature integration. Avoid parallel edits to shared `SecurityConfig.java`, `LoginService.java`, `Problem.java`, or `client.ts`.

## Parallel Example: User Story 1

```text
Task: T009 repository tests for account constraints in backend/src/test/java/at/mci/igp/raumlotse/repository/UserAccountRepositoryTest.java
Task: T010 credential policy tests in backend/src/test/java/at/mci/igp/raumlotse/service/AccountAuthenticationServiceTest.java
Task: T011 session endpoint tests in backend/src/test/java/at/mci/igp/raumlotse/AuthenticationIntegrationTest.java
Task: T012 login UI tests in frontend/src/pages/LoginPage.test.tsx and frontend/src/auth/AuthProvider.test.tsx
```

## Implementation Strategy

### MVP First

Complete setup and default-deny foundation, then deliver US1 as the first user-visible increment: the account fixture, valid credential login, session identity, protected home navigation, and reload. Keep all business APIs denied until US3 route coverage is ready; do not deploy an incomplete allowlist. US2 cooldown implementation is mandatory before production login is enabled.

### Incremental Delivery

1. Complete setup/foundation and confirm unauthenticated business APIs deny by default.
2. Add US1; verify valid login, session rotation and reload independently.
3. Add US2; verify five-failure threshold, fixed cooldown, persistence and UI retry.
4. Complete US3 route/API coverage and authenticated CSRF mutations.
5. Run polish gates and satisfy account provisioning/feature 003 operational prerequisites before production release.

Each checkpoint retains its failing-test evidence before implementation and passing result afterward, as required by the constitution.

## Phase 7: Convergence

- [X] T054 Add deterministic rolling-window and cooldown boundary tests proving successful-login reset, expired-history reset, and validation/service-outage exclusions alongside the exact 15-minute cutoffs per FR-014, SC-007, and T025 (partial).
- [X] T055 Expand parameterized protected-route tests to cover every current GET, POST, PUT, DELETE, deactivate, and reactivate endpoint; assert anonymous reads/writes are denied, authenticated unsafe requests require CSRF, and denied requests make no changes per FR-009, SC-003, and T040 (partial).
