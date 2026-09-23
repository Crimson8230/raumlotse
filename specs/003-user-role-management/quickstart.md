# Quickstart Validation: User Role Management

Validation guide and implementation record for feature 003.

## Post-rebase integration validation — 2026-09-21

Removed the reintroduced `V4__create_user_account.sql` after confirming its SHA-256
matches `V6__create_user_account.sql`. The complete migration directory now matches
`dev`: V1-V3 catalog tables, V4 reservations, V5 login-attempt state, V6 user accounts,
V7 role tables and V8 role backfill. Existing filenames and SQL are unchanged.
V5 has no account-table dependency; V7 references the UUID account key created by V6.
Removed leftover merge-conflict markers from this document. T044-T045 are complete;
the existing external lifecycle/provisioning and manual acceptance gates remain open.

| Check | Result |
|---|---|
| Before fix: `.\mvnw.cmd -Dtest=MigrationVersionTest test` | RED: duplicate Flyway version 4 |
| Backend migration/authentication/role checks | GREEN: 19 tests, zero failures/errors/skips; fresh PostgreSQL 17 migration through V8, V7-to-V8 backfill preservation, real login/session/CSRF, current roles and concurrent mutation safeguards |
| Frontend `npm test` | PASS: 19 files, 75 tests |
| Frontend `npm run lint` and `npm run build` | PASS |

Backend validation command, from `backend/`:

```powershell
.\mvnw.cmd clean '-Dtest=MigrationVersionTest,AuthenticationIntegrationTest,UserRoleIntegrationTest,UserRoleConcurrentUpdateIntegrationTest,UserRoleMigrationIntegrationTest,UserRoleErrorHandlingTest' test
```

The clean build removed the deleted migration's stale copy from `target/classes`.
The sandbox denied Docker named-pipe access during that run; rerunning the same
test selection without `clean` outside the sandbox passed using disposable
Testcontainers databases. Runtime: Java 25, compiling for Java 21. This run covered
the selected backend suites; the full-suite result below is historical evidence.

## Implementation status — 2026-09-20

T001 and T002 are complete: authentication from feature 005 is integrated, and its
concrete capability mapping is recorded in [contracts/authentication.md](contracts/authentication.md).
The merged duplicate V4 was corrected by renaming the user-account migration to V6;
reservation V4 and login-attempt V5 remain unchanged. Reserve
`backend/src/main/resources/db/migration/V7__create_user_role_tables.sql` and
`backend/src/main/resources/db/migration/V8__backfill_user_roles.sql` for this feature.
These role migrations are implemented. Transactional account creation, initial-Admin
provisioning/recovery and lifecycle coordination remain external blocked dependencies.
No account creation or provisioning work is included in feature 003. Use verified
role fixtures only inside tests; an external identity operator must assign the first
Admin before production role routes can become ready.

For an existing database that already applied the old authentication-only V4,
reconcile migration history through a reviewed upgrade procedure before startup.
No existing database was changed, repaired or cleared during this implementation.
Fresh PostgreSQL 17 migration plus authenticated access passed the integration test.

The implementation retains the existing Java 21/Spring MVC/JPA/Flyway and
React/TypeScript/Vitest dependencies.

| Test-first evidence | Status |
|---|---|
| Migration regression: `MigrationVersionTest` | RED: duplicate V4 detected before rename; GREEN after rename with `mvnw.cmd clean -Dtest=MigrationVersionTest test` |
| Authentication baseline: `AuthenticationIntegrationTest` | PASS against real PostgreSQL 17 via Testcontainers; session and CSRF protections retained |
| Foundation and role integration tests | PASS: 17 tests across user-role integration, concurrency, migration and sanitized error handling |
| Frontend role and route tests | PASS: 19 test files, 75 tests, including role editor, list, conflict recovery and access-control tests |
| Account creation, initial Admin and deletion lifecycle | Blocked external dependencies; excluded from feature 003 |
| Manual browser/accessibility and timed acceptance | Not performed; requires a safely provisioned external Admin |
| Full backend suite | PASS: 218 tests, 0 failures/errors via `.\mvnw.cmd test` (run from `backend/`); includes PostgreSQL-backed concurrency and migration coverage. The first run exposed unauthenticated/CSRF-less reservation and room MVC fixtures, a per-class Testcontainers shutdown while Spring retained cached contexts, and a duplicate hard-coded building name. Fixtures now use authenticated CSRF-aware requests, the shared test database stays alive for the JVM, and the building fixture is unique. |

## Prerequisites and startup

Java 21, compatible Node/npm, running Docker and delivered authentication satisfying [authentication.md](contracts/authentication.md). Use disposable accounts: two Admins, University Staff, Student, Lecturer and Viewer. Provision initial Admin through the identity feature's secure process. Never put credentials in source or command history.

PowerShell from repository root:
```powershell
docker compose up -d db
Set-Location backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Second terminal from repository root:
```powershell
Set-Location frontend
npm ci
npm test
npm run lint
npm run build
npm run dev
```

Backend port 8080; use the Vite URL printed at startup (normally http://localhost:5173). Existing Vite proxy forwards /api. Provide prerequisite authentication configuration and client integration; no guessed login command is supplied.

## Test-first validation

Write failing tests before production code, capture failures, then retain passing evidence from the same tests. Use existing AbstractIntegrationTest with PostgreSQL 17 for concurrency.

| Scenario | Expected evidence |
|---|---|
| All 31 nonempty sets | Save/reload each while another Admin remains |
| All non-Admin combinations, unauthenticated | 401/403; no changes, including direct requests |
| Empty/unknown/duplicate roles or invalid version | 400; unchanged persisted state |
| Creation and migration | Viewer at creation commit; preserve valid existing roles; zero roleless accounts |
| Last Admin / self-edit | 409 LAST_ADMIN_REQUIRED; unchanged state |
| Same target, two editors | One save succeeds; stale save gets 409; review/retry succeeds |
| Different targets, two Admin self-demotions | At most one succeeds; at least one Admin remains |
| Actor revoked while waiting | After lock acquisition, waiting save returns 403 |
| Change-away/change-back; stale no-op | Old versions still conflict |
| Current no-op | 200, unchanged version |
| Rollback/lock timeout | No partial selection; bounded error, no automatic replay |
| Deleted target | 404 for authorized caller; other accounts unchanged |

Use separate database connections/transactions and barriers, not timing-only sleeps. Assert final persisted state after all operations finish. Migration tests cover recognized roles, missing-role backfill and unknown-role rejection.

## Browser acceptance

1. Sign in as Admin, find a known user, open roles, select Student and Lecturer, remove Viewer, save/reload; verify exact roles in under two minutes.
2. Clear all roles; verify visible validation. Cancel a different edit and confirm saved state is unchanged.
3. Open the same user in two Admin sessions. Save in one, then submit the outdated other editor. Verify conflict, explicitly Review latest roles, then make a new valid save.
4. Attempt last-Admin demotion with another role retained; confirm rejection. With two Admins, self-demote one and verify their next management action is denied without sign-in.
5. Check direct role URLs and requests as University Staff, other non-Admins and unauthenticated users.
6. Create users through every prerequisite creation flow; verify sole Viewer assignment.
7. Check keyboard operation, small viewport, focus and status announcements. Confirm existing room/catalog behavior still works.

Payloads/statuses: [api.md](contracts/api.md). Invariants: [data-model.md](data-model.md). Use authenticated browser tooling or prerequisite test clients for direct calls; never add a production bypass.

## Release evidence

Backend tests plus frontend tests/lint/build pass, with red/green evidence and manual outcomes. Verify real authentication, credential/CSRF integration, sanitized logs, zero roleless accounts and at least one usable Admin. If authentication is absent, the release gate stays closed even when isolated role tests pass.
