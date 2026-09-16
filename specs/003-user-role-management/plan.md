# Implementation Plan: User Role Management

**Branch**: `003-user-role-management` | **Date**: 2026-09-16 | **Spec**: [spec.md](spec.md)

**Input**: `specs/003-user-role-management/spec.md`

## Summary

Add an Admin-only user list and editor for five predefined roles. Persist nonempty role sets, default new accounts to Viewer, enforce current Admin authority, preserve the last Admin, and reject outdated saves. Extend the existing Spring MVC/JPA layers and React application. A dedicated role version detects stale edits; a shared database guard serializes role mutations to protect the global Admin invariant.

Authentication is a separate prerequisite, as clarified by the user. The handoff is defined in [contracts/authentication.md](contracts/authentication.md). This plan does not implement sign-in or registration. Planning may finish before authentication, but integration and release remain gated on it.

## Technical Context

**Language/Version**: Java 21; TypeScript ~6.0.2, strict mode.

**Primary Dependencies**: Repository manifests specify Spring Boot 4.1.1, Spring MVC, Bean Validation, JPA, Flyway; React ^19.3.0, React Router ^7.18.3, Vite ^8.3.0. Retain the existing dependency set; no upgrade proposed.

**Storage**: PostgreSQL 17; Flyway migrations currently V1-V3. Allocate new migration numbers after prerequisite migrations merge.

**Testing**: JUnit Jupiter, Mockito, MockMvc, PostgreSQL Testcontainers 1.20.4; Vitest and React Testing Library.

**Target Platform**: Docker-backed Java service and browser application; Windows development supported.

**Project Type**: Web application with backend and frontend.

**Performance Goals**: Complete the review/edit/verify journey within two minutes (SC-001). Bound user lists to 25 entries by default, maximum 100. No new throughput SLA is asserted.

**Constraints**: No roleless account, last-Admin removal, stale overwrite, stale sign-in authority, or personal data in logs. Authentication is required before release.

**Scale/Scope**: Five roles, 31 nonempty combinations, application-wide assignments, infrequent administrative writes. One list/editor flow; no custom roles, bulk changes, sign-in UI, or changes to room/catalog permissions.

## Constitution Check

Pre-research and post-design review:

| Principle | Evidence / planned gate | Result |
|---|---|---|
| I. Test first | Failing service, controller, UI and real PostgreSQL concurrency tests precede implementation; retain red/green evidence. | PASS |
| II. Typed consistent code | Existing Java layers and strict TypeScript; no new framework. | PASS |
| III. Contract first | API, UI and authentication contracts written before code; existing APIs unchanged. | PASS |
| IV. Secure data handling | Persisted authorization, validation, parameterized queries, prerequisite credential/CSRF integration, sanitized role errors. | PASS |
| V. Simplicity and observability | Existing health endpoint/client; one database guard, no policy engine; structured reason codes without personal data. | PASS |
| Workflow | Feature branch targets dev; tests/build/lint required; only dev targets main. | PASS |

Post-design: no introduced constitution violations or unresolved feature decisions. Authentication delivery is a documented dependency, never an authorization bypass. Existing development credentials in application/Compose files are not production configuration; add no secrets, and use prerequisite deployment configuration for authentication.

## Project Structure

### Documentation (this feature)

```text
specs/003-user-role-management/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/api.md
  contracts/ui.md
  contracts/authentication.md
  checklists/requirements.md
```

`tasks.md` is generated later by speckit-tasks.

### Source Code (repository root)

```text
backend/src/main/java/at/mci/igp/raumlotse/
  controller/     UserRoleController
  domain/         role enum, assignment aggregate, guard
  dto/            user/role requests, responses, compatible error code
  service/        UserRoleService and current-role authorization
  repository/     role state and guard persistence
  exception/      safe typed role errors
backend/src/main/resources/db/migration/   role schema and backfill
backend/src/test/java/at/mci/igp/raumlotse/
  controller/     authorization and contract tests
  service/        invariant tests
  UserRoleIntegrationTest.java
  UserRoleConcurrentUpdateIntegrationTest.java
frontend/src/
  API/userRoles.ts
  types/userRole.ts
  pages/UserRoleListPage.tsx
  pages/UserRolePage.tsx
  components/UserRoles/   editor and colocated tests
  components/Navigation/ Admin-only entry
  App.tsx                protected routes
```

**Structure Decision**: Extend the existing two-project layout. Map the identity prerequisite's account model into the handoff contract rather than creating a second account system.

## Delivery Sequence and Validation

1. Verify the prerequisite provides principal identity, account directory, transactional creation hook, initial Admin provisioning and deletion coordination. Production integration is blocked until these capabilities exist; test fixtures may supply verified identities.
2. Write failing rule/contract tests, then implement schema/backfill and atomic role persistence. Preserve recognized assignments; block rollout for unsupported legacy roles or no initial Admin.
3. Implement current-role authorization, guarded updates, version checking and typed errors. Exercise PostgreSQL concurrency tests before the UI.
4. Write failing component tests, then build list/editor using existing styling, navigation and client conventions.
5. Execute quickstart acceptance scenarios and complete backend tests, frontend tests/lint/build and real authentication integration checks.

Coverage: FR-001/004/005/007/010 -> aggregate validation; FR-002/008/011 -> authorization; FR-003/009 -> list/editor; FR-006/013 -> lifecycle integration; FR-012/014/015 -> guarded transactions. SC-001 is timed manually; SC-002 through SC-008 receive automated invariant, contract and integration coverage.

## Complexity Tracking

No constitution exceptions. Per-user versions alone cannot protect the global last-Admin invariant across different targets. A singleton guard serializes infrequent role writes without adding infrastructure; ordinary reads do not acquire that guard.
