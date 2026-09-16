# Quickstart Validation: User Role Management

Guide for the future implementation; planning does not implement endpoints or run product acceptance tests.

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

