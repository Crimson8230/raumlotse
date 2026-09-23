# Implementation Plan: Email and Password Login

**Branch**: `005-email-password-login` | **Date**: 2026-09-20 | **Spec**: [spec.md](spec.md)

**Input**: `specs/004-email-password-login/spec.md`

## Summary

Add email/password login, persistent account credentials, server-side sessions, and authentication checks for every existing business page and API. Extend Spring MVC with Spring Security and the existing React Router/client structure. Enforce five credential failures in a rolling 15-minute window followed by a fixed 15-minute per-email cooldown, equally for registered and unknown emails. Successful login opens `/`; unauthenticated browser navigation opens `/login` without mounting protected content.

No account or authentication implementation exists in this checkout. This feature supplies that identity foundation. Production account provisioning and initial Admin setup remain separate prerequisites; the role-management handoff is explicitly bounded in [contracts/integration.md](contracts/integration.md).

## Technical Context

**Language/Version**: Java 21; TypeScript ~6.0.2 with strict checking.

**Primary Dependencies**: Repository manifests declare Spring Boot 4.1.1, MVC, Bean Validation, JPA, Flyway; React ^19.3.0, React Router ^7.18.3, Vite ^8.3.0. Add Boot-managed Spring Security and security test support, plus Zod for login schema validation. Retain current manifest versions; implementation verifies dependency resolution and compilation.

**Storage**: PostgreSQL 17 accounts with prefixed password hashes and persistent HMAC-keyed login-attempt state; single-process servlet sessions. Restart signs users out. Flyway migrations currently V1-V3; allocate the next free version during implementation.

**Testing**: Existing JUnit Jupiter, Mockito, MockMvc, PostgreSQL Testcontainers 1.20.4, Vitest, React Testing Library and user-event; add security test support. Manual browser acceptance for actual cookies, autofill, reload and timed usability.

**Target Platform**: Browser and Java service with PostgreSQL; Windows development and Docker. HTTPS and same-origin frontend/API outside local development.

**Project Type**: Existing two-project web application.

**Performance Goals**: Known-credential login journey within one minute when no cooldown is active under normal availability (SC-001). Benchmark password verification on deployment hardware; no speculative throughput SLA.

**Constraints**: Exact password matching, no credential/PII diagnostics, login CSRF protection, session rotation, 30-minute inactivity timeout, no browser token persistence, no implicit Admin. Five failures/15-minute rolling window; 15-minute cooldown without extension. A stable AUTH_ATTEMPT_HMAC_KEY secret is required across restarts; active history is never discarded to bypass a storage limit. Coordinate frontend/backend rollout because existing business APIs gain authentication and CSRF requirements.

**Scale/Scope**: One login page, three auth endpoints, one account table and one bounded-history limiter table, one backend instance. Login attempts for each canonical email serialize independently; expired limiter rows are cleaned in batches. Excludes registration, logout flow, password recovery, remember-me, role assignment and initial Admin provisioning.

## Constitution Check

Pre-research and post-design review:

| Principle | Evidence / implementation gate | Result |
|---|---|---|
| I. Test first | Failing account, session, CSRF, privacy, cooldown boundary/concurrency/rollback, contract and UI tests precede code; retain red/green evidence. | PASS |
| II. Typed consistent code | Existing Java layers and strict TypeScript; build/lint required. | PASS |
| III. Contract first | Auth, UI and migration/handoff contracts include cooldown 429/Retry-After and triggering-failure metadata before code. | PASS |
| IV. Secure data handling | Bean Validation, frontend schema, parameterized persistence, adaptive hashes, HMAC-keyed expiring limiter state and safe diagnostics. Externalize existing development configuration credentials before secure rollout. | PASS |
| V. Simplicity and observability | Servlet sessions and per-email database rows without Redis, existing health endpoint, safe event codes; reasons for Security/Zod additions recorded in research and required in PR. | PASS |
| Workflow | Feature branch targets dev; backend tests and frontend tests/lint/build before merge; only dev targets main. | PASS |

These are design results, not executed implementation checks. Existing database and pgAdmin credential defaults are rollout remediation, not approved production secrets. No constitution exception is required.

## Project Structure

### Documentation (this feature)

```text
specs/004-email-password-login/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/api.md
  contracts/ui.md
  contracts/integration.md
  checklists/requirements.md
```

`tasks.md` is generated later by speckit-tasks.

### Source Code (repository root)

```text
backend/src/main/java/at/mci/igp/raumlotse/
  config/       SecurityConfig, local-only account fixture
  controller/   AuthController
  dto/          LoginRequest, AuthenticatedUserResponse, CsrfResponse
  domain/       UserAccount, LoginAttemptState
  repository/   UserAccountRepository, LoginAttemptStateRepository
  service/      account lookup, login lifecycle, verified principal, attempt limiter and cleanup
  exception/    sanitized auth errors and security response handlers
backend/src/main/resources/
  application.yaml
  db/migration/ account and login-attempt-state migration
backend/src/test/java/at/mci/igp/raumlotse/
  controller/   security contract tests
  service/      validation and credentials tests
  repository/   persistence/uniqueness tests
  AuthenticationIntegrationTest.java
  AuthenticationLoggingTest.java
  LoginCooldownIntegrationTest.java
  LoginCooldownConcurrencyTest.java
frontend/src/
  API/          auth.ts, shared client.ts integration
  auth/         AuthProvider, RequireAuth, login schema and tests
  types/        auth.ts
  pages/        LoginPage.tsx, LoginPage.css and tests
  App.tsx       public login and protected shell
  components/Navigation/  protected-shell placement
README.md
docker-compose.yml
```

**Structure Decision**: Extend existing layers and styling; no additional service, account directory, token store or authorization engine. The listed classes/files are planned additions, not existing implementation.

## Delivery Sequence and Validation

1. Write failing account/contract tests, then add schema, canonical lookup, versioned encoder and opt-in local fixture. Verify real PostgreSQL uniqueness and exact password matching.
2. Write failing security lifecycle tests, then add JSON login, explicit session strategy/context persistence, CSRF bootstrap, identity lookup and safe errors. Verify session rotation, expiry and token renewal. Add failing cooldown tests, then persistent per-email history with atomic row acquisition, transaction outcome handling, session-failure compensation, expiry cleanup and stable HMAC configuration. Test lock waits and database failure without counter increments.
3. Add access tests for every current controller/mutation family. Protect all business APIs; update legitimate existing tests with authenticated fixtures and CSRF rather than disabling filters.
4. Write failing UI tests, then implement login, identity bootstrap, guarded shell and shared client integration. Existing mutations send CSRF; protected 401 clears rendered private state; no automatic mutation replay. Add per-email cooldown feedback, wait countdown and explicit retry after expiry; do not disable other email identities.
5. Complete redacted diagnostics, externalized configuration credentials, same-origin deployment guidance and handoff. Execute quickstart and regression checks.

Coverage: FR-001/002/003/006/007 -> UI/validation; FR-004/005 -> provider/account contracts; FR-008/010 -> session lifecycle; FR-009 -> all protected routes/APIs; FR-011 -> verified identity and preservation of installed permissions; FR-012/013 -> outages/log capture; FR-014 -> rolling-window boundaries, concurrency, same-email variants, success/expiry reset, no deadline extension, isolation and unchanged authenticated sessions. SC-001/005 are observed usability checks; SC-002/003/004/006/007 combine automated checks and browser validation.

Setup reports the directory identifier as `BRANCH=004-email-password-login`; Git reports `005-email-password-login`. Use `.specify/feature.json` for artifact discovery and the actual Git branch for source control.

## Complexity Tracking

No constitution exceptions. JSON login explicitly executes Spring's session strategy and saves its context; focused integration tests protect this lifecycle. In-memory sessions constrain deployment to one backend instance; distributed sessions are outside this feature.

The per-email transaction holds one database connection while password verification runs, intentionally bounding same-identity concurrency without a global lock. Five-second lock waits and ten-second statement timeouts fail closed. Persistent limiter state survives restart even though servlet sessions do not. Cleanup never removes live histories; no additional datastore is introduced. The updated design passes the post-clarification constitution review with no exceptions.
