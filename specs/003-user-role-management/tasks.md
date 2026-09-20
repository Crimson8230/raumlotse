# Tasks: User Role Management

**Input**: Design documents in `specs/003-user-role-management/`.
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/api.md`, `contracts/ui.md`, `contracts/authentication.md`.
**Tests**: Test-first work is required by the constitution and the feature's acceptance criteria. Write each test group, run it and retain failing evidence before implementing its behavior; retain passing evidence afterward.
**Organization**: Three P1 user stories, in specification order. Shared safety primitives are foundational so US1 never exposes an unprotected write.
**Format**: `- [ ] Tnnn [P?] [USn?] Description with file path`. [P] means independent files within the indicated dependency wave, not permission to bypass prerequisites.
**Paths**: All paths are repository-relative. New Java classes use the existing `at.mci.igp.raumlotse` package.

**Execution status (2026-09-20)**: Checklist gate passed (16/16). Setup ignore-file verification is complete. T001's missing-capability audit is recorded in `contracts/authentication.md`, but the concrete prerequisite mapping remains blocked. T002 cannot allocate migration filenames until authentication is integrated. Local prerequisite candidate: `005-email-password-login` at `c61bbf8`; references to feature 004 below identify the earlier prerequisite name, not a verified implementation mapping. All task checkboxes remain open because no full task has yet met its completion criteria.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the authentication boundary and retain concrete implementation evidence.

- [ ] T001 Record the delivered authentication module, native account key, verified principal access, directory API, frontend session integration, creation hooks, provisioning and supported deletion/deactivation paths in `specs/003-user-role-management/contracts/authentication.md`; inspect feature 004 only once available on the implementation branch, identify missing capabilities explicitly, and gate production adapter/schema integration on their delivery without implementing login or registration here.
- [ ] T002 Record the next free Flyway migration filename after prerequisite migrations merge and the test/run commands and red/green evidence sections in `specs/003-user-role-management/quickstart.md`; inspect `backend/src/main/resources/db/migration/`, `backend/pom.xml` and `frontend/package.json`, retain existing dependencies and do not allocate a colliding or guessed account-schema migration.

**Checkpoint**: The handoff is mapped, or missing capabilities are explicitly blocked. Isolated test preparation may proceed with verified principal fixtures; no production mock identity is permitted.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish persistent role state, current authorization and transaction safeguards before exposing any story.

- [ ] T003 [P] Write failing persistence/guard tests in `backend/src/test/java/at/mci/igp/raumlotse/repository/UserRoleRepositoryIntegrationTest.java` using `backend/src/test/java/at/mci/igp/raumlotse/AbstractIntegrationTest.java`: unique supported assignments, foreign keys, version initialization, seeded singleton guard, transactional rollback and consistent roles/version snapshots.
- [ ] T004 [P] Write failing safety primitive tests in `backend/src/test/java/at/mci/igp/raumlotse/service/UserRoleSafetyTest.java` for fresh persisted Admin checks, absent identity, nonempty known unique selections, stale-before-no-op comparison, last-Admin protection and five-second lock timeout mapping; use verified identity fixtures, never an implicit Admin.
- [ ] T005 Implement role entities in `backend/src/main/java/at/mci/igp/raumlotse/domain/Role.java`, `backend/src/main/java/at/mci/igp/raumlotse/domain/UserRoleState.java`, `backend/src/main/java/at/mci/igp/raumlotse/domain/RoleAssignment.java` and `backend/src/main/java/at/mci/igp/raumlotse/domain/RoleMutationGuard.java`, preserving data-model constraints verbatim: UserRoleState "userId primary/foreign key, rolesVersion bigint initially 0" and "One per account; version never decreases"; RoleAssignment "Composite primary key; FK to UserRoleState; code constrained to supported values"; guard "id fixed to 1" and "One seeded row; never publicly exposed"; codes ADMIN, UNIVERSITY_STAFF, STUDENT, LECTURER, VIEWER with their exact labels and no implied membership.
- [ ] T006 Create the Flyway schema migration at the exact filename recorded by T002 in `backend/src/main/resources/db/migration/`: bind to the delivered native account key, add role-state/assignment/guard tables, seed guard 1, enforce supported codes, unique pairs and foreign keys, and index roleCode; do not create a parallel users table or enable role routes before US3 backfill passes.
- [ ] T007 Implement `backend/src/main/java/at/mci/igp/raumlotse/repository/UserRoleStateRepository.java`, `backend/src/main/java/at/mci/igp/raumlotse/repository/RoleAssignmentRepository.java` and `backend/src/main/java/at/mci/igp/raumlotse/repository/RoleMutationGuardRepository.java` with parameterized queries, fresh membership projections, consistent editor snapshots and PESSIMISTIC_WRITE guard locking; configure the transaction-local five-second database lock-wait bound rather than assuming an unsupported JPA hint works.
- [ ] T008 Implement the small prerequisite adapter in `backend/src/main/java/at/mci/igp/raumlotse/service/RoleIdentityAdapter.java` after T001's capability gate, preserving user reference constraints "Stable opaque userId, displayName, unique accountLabel; identity owns account" and "API uses nonempty string id; persistence binds to prerequisite native key"; delegate verified identity and directory access to the delivered prerequisite, with no browser-supplied authority.
- [ ] T009 Implement shared checks and mutation coordination in `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleSafety.java`: enforce "Every account has 1-5 unique assignments." and "Validate 1-5 unique known roles and required expectedVersion."; use READ COMMITTED, lock guard before fresh actor/target reads, require current ADMIN, compare version before no-op, preserve a remaining Admin, commit atomically, and forbid automatic retries or writes outside the common coordinator.
- [ ] T010 Add sanitized typed role exceptions and compatible Problem code handling in `backend/src/main/java/at/mci/igp/raumlotse/exception/UserRoleException.java`, `backend/src/main/java/at/mci/igp/raumlotse/exception/GlobalExceptionHandler.java` and `backend/src/main/java/at/mci/igp/raumlotse/dto/Problem.java`; map all contract codes/statuses including timeout 503 and sanitized 500, retain existing Problem fields/consumers, and emit only event/reason/status/correlation without identities, submitted data, SQL or stack traces.

**Checkpoint**: T003-T004 pass after recorded failures. Stories may consume the primitives; production integration remains blocked until real authentication, migration and lifecycle gates pass.

## Phase 3: User Story 1 — View and modify a user's roles (P1)

**Goal**: Admins can find users, review and replace role selections, cancel edits and explicitly recover from stale saves.
**Independent Test**: With a verified Admin and an existing target, find the user, replace Viewer with Student + Lecturer, reopen and verify; cancel leaves persistence unchanged, and two editors require explicit review after a conflict.

### Tests — write and run failing before implementation

- [ ] T011 [P] [US1] Add API contract tests in `backend/src/test/java/at/mci/igp/raumlotse/controller/UserRoleControllerTest.java` for GET list, GET roles and PUT replacement per `specs/003-user-role-management/contracts/api.md`: query bounds/literal wildcard search/stable paging, opaque encoded IDs, full catalog and canonical decimal versions, required fields, no-store, missing target, no-op, stale conflict and compatible sanitized failures.
- [ ] T012 [P] [US1] Add service/persistence tests in `backend/src/test/java/at/mci/igp/raumlotse/service/UserRoleServiceTest.java` and `backend/src/test/java/at/mci/igp/raumlotse/UserRoleIntegrationTest.java` for all 31 nonempty sets with a separate Admin, exact replacement, persistence after reopen, unchanged version on current no-op, stale no-op, A→B→A version progression and rollback without partial assignments.
- [ ] T013 [P] [US1] Add UI contract tests in `frontend/src/pages/UserRoleListPage.test.tsx`, `frontend/src/pages/UserRolePage.test.tsx` and `frontend/src/components/UserRoles/UserRoleEditor.test.tsx` for search/paging/loading/empty/retry, identity and all labels, save/cancel, success/failure, lost-response refetch, explicit conflict-review-retry without merge/replay and repeated conflicts.

### Implementation

- [ ] T014 [US1] Define validated API DTOs in `backend/src/main/java/at/mci/igp/raumlotse/dto/UserSummary.java`, `backend/src/main/java/at/mci/igp/raumlotse/dto/UserRolesResponse.java`, `backend/src/main/java/at/mci/igp/raumlotse/dto/UserRoleUpdateRequest.java` and `backend/src/main/java/at/mci/igp/raumlotse/dto/UserListResponse.java`: roles contain 1-5 unique supported codes, expectedVersion is required nonnegative canonical bigint decimal text within backend range, and responses return roles/catalog in contract order.
- [ ] T015 [US1] Implement `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleService.java` list/read/replace operations through the prerequisite adapter and shared safety coordinator: trimmed q up to 100 characters, literal case-insensitive substring matching, page >=0, size 1-100/default 25, displayName/id ordering, fresh authorization before target disclosure, coherent reads and atomic versioned writes.
- [ ] T016 [US1] Implement `backend/src/main/java/at/mci/igp/raumlotse/controller/UserRoleController.java` for GET `/api/admin/users`, GET and PUT `/api/admin/users/{userId}/roles`; use prerequisite credentials/CSRF, Bean Validation and typed errors, return committed snapshots with no-store, and keep existing room/catalog permissions unchanged.
- [ ] T017 [P] [US1] Implement `frontend/src/types/userRole.ts` and `frontend/src/API/userRoles.ts` using `frontend/src/API/client.ts`: exact RoleCode/labels, opaque string IDs/versions, path encoding, list/read/replace requests, safe typed error codes and existing credential/CSRF transport; never automatically replay a mutation.
- [ ] T018 [US1] Implement searchable paginated `frontend/src/pages/UserRoleListPage.tsx` with name and unique account label, default 25 results, loading/empty/retry states and stale-search response protection; use the existing application language and layout.
- [ ] T019 [US1] Implement `frontend/src/components/UserRoles/UserRoleEditor.tsx` and `frontend/src/components/UserRoles/UserRoleEditor.css` with exact five native labeled checkboxes, fieldset/legend, shared typed selection validation, Save/Cancel, draft preservation and announced status; disable Save before load, while saving or invalid.
- [ ] T020 [US1] Implement `frontend/src/pages/UserRolePage.tsx` orchestration: cancel discards and returns to list, success uses committed roles/version, stale saves disable further saves until explicit Review latest roles replaces the draft, uncertain outcomes require refetch before user retry, and missing targets explain failure and return to list.
- [ ] T021 [US1] Add protected routes to `frontend/src/App.tsx` for `/admin/users` and `/admin/users/:userId/roles` using the prerequisite session flow; wire list/editor together only with current Admin access and run T011-T013 to green.

**Checkpoint**: US1 works against verified fixtures, including save/reopen and review/retry; this is the functional MVP, not a release waiver for US2/US3.

## Phase 4: User Story 2 — Restrict role management to Admins (P1)

**Goal**: Enforce current Admin authority on every access and preserve at least one usable Admin under concurrent changes.
**Independent Test**: Exercise direct requests and routes as Admin, Admin plus other roles, every non-Admin combination and unauthenticated; revoke Admin during an open session and confirm next-action denial, including a request waiting on the guard.

### Tests — write and run failing before implementation

- [ ] T022 [P] [US2] Write the authorization matrix in `backend/src/test/java/at/mci/igp/raumlotse/controller/UserRoleAuthorizationTest.java`: all 15 nonempty non-Admin combinations, all 16 Admin combinations, missing/expired identity, self-promotion attempts and identity/role spoofing; assert 401/403 before target disclosure, no mutation, current grant/revocation without fresh sign-in and prerequisite request-forgery rejection where applicable.
- [ ] T023 [P] [US2] Add real PostgreSQL race tests in `backend/src/test/java/at/mci/igp/raumlotse/UserRoleConcurrentUpdateIntegrationTest.java` using separate connections and barriers: same-target stale saves, two different Admin self-demotions, actor revoked while waiting, last-Admin removal, lock timeout and rollback; assert final roles/versions and at least one Admin, with no timing-only sleeps.
- [ ] T024 [P] [US2] Add route/navigation/access-loss tests in `frontend/src/App.user-roles.test.tsx` and `frontend/src/components/Navigation/Navigation.test.tsx` for hidden non-Admin links, protected deep links, prerequisite sign-in/session expiry, 403 editing removal, successful self-demotion and current membership refresh.
- [ ] T025 [US2] Extend `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleSafety.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleService.java` until T022-T023 pass: reads consult persisted roles per request, writes recheck only after guard acquisition with fresh projections, last-Admin checks cover self/other targets and cross-target races, and all rejected writes preserve state.
- [ ] T026 [US2] Integrate current membership and the Admin-only entry in `frontend/src/components/Navigation/Navigation.tsx` and `frontend/src/App.tsx` using the T001-mapped prerequisite session API; refresh authority on the next management action, support newly granted Admin without re-login and clear management access after revocation.
- [ ] T027 [US2] Complete authorization error handling in `frontend/src/pages/UserRolePage.tsx` and `frontend/src/pages/UserRoleListPage.tsx`: 401 uses prerequisite sign-in, 403 removes editor access, LAST_ADMIN_REQUIRED preserves draft with explanation, and successful self-demotion refreshes membership then exits role management.
- [ ] T028 [US2] Run real-authentication integration for `backend/src/test/java/at/mci/igp/raumlotse/controller/UserRoleAuthorizationTest.java` and `frontend/src/App.user-roles.test.tsx`, record red/green and cookie-CSRF or alternative transport evidence in `specs/003-user-role-management/quickstart.md`, and keep integration blocked if feature 004 has not delivered the required capabilities.

**Checkpoint**: Direct access cannot bypass authorization, role changes affect the next action, and synchronized database races cannot remove the last Admin.

## Phase 5: User Story 3 — Keep every user assigned at least one role (P1)

**Goal**: Account creation defaults atomically to Viewer; backfill preserves valid assignments; empty/invalid selections never alter persistence.
**Independent Test**: Create accounts through every supported flow and observe only Viewer at commit; reject empty changes without mutation; backfill roleless accounts while retaining valid roles and rejecting unknown legacy values.

### Tests — write and run failing before implementation

- [ ] T029 [P] [US3] Add lifecycle integration tests in `backend/src/test/java/at/mci/igp/raumlotse/UserRoleLifecycleIntegrationTest.java` for every T001-mapped account creation path, version 0 plus sole VIEWER at visibility, account/role rollback together, prohibited registration elevation, guarded provisioning and supported deletion/deactivation preserving the last usable Admin.
- [ ] T030 [P] [US3] Add PostgreSQL migration tests in `backend/src/test/java/at/mci/igp/raumlotse/UserRoleMigrationIntegrationTest.java` for roleless backfill, preservation of each recognized assignment, unsupported legacy role rejection, zero roleless accounts and rollout refusal without an initial Admin; use the actual prerequisite account schema and migration filenames from T002.
- [ ] T031 [P] [US3] Add validation tests in `backend/src/test/java/at/mci/igp/raumlotse/controller/UserRoleValidationTest.java` and `backend/src/test/java/at/mci/igp/raumlotse/service/UserRoleSelectionTest.java` for empty/null/missing/duplicate/unknown roles, missing/invalid/overflow versions, Viewer coexistence and replacement, deleted targets, unchanged persistence after rejection and concurrent valid/empty requests.
- [ ] T032 [P] [US3] Add frontend validation tests in `frontend/src/types/userRole.test.ts` and extend `frontend/src/components/UserRoles/UserRoleEditor.test.tsx` for empty-selection explanation, disabled Save, invalid runtime payloads, Viewer with Student and Viewer replacement without auto-substitution.
- [ ] T033 [US3] Implement shared creation/provisioning/deletion coordination in `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleLifecycleService.java` and wire the exact identity-owned hooks recorded in `specs/003-user-role-management/contracts/authentication.md`: ordinary creation commits VIEWER/version 0 with the account, all writers use guard→state→assignments lock order, provisioning stays operationally controlled and deletion/deactivation cannot remove the last usable Admin.
- [ ] T034 [US3] Create the backfill migration under the exact filename reserved by T002 in `backend/src/main/resources/db/migration/` and rollout verification in `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleReadinessCheck.java`; preserve known assignments, add Viewer only to roleless users, reject unknown roles, and prevent enabling role management until zero roleless accounts and at least one usable Admin are verified.
- [ ] T035 [US3] Complete selection validation in `backend/src/main/java/at/mci/igp/raumlotse/dto/UserRoleUpdateRequest.java`, `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleSafety.java` and `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleService.java` to pass T031: reject invalid selections without defaulting to Viewer, retain Viewer with other roles, permit nonempty replacement, and ensure failed or competing requests leave valid persisted state.
- [ ] T036 [US3] Complete shared frontend validation and visible accessible feedback in `frontend/src/types/userRole.ts` and `frontend/src/components/UserRoles/UserRoleEditor.tsx`; apply identical supported-code/cardinality/uniqueness rules before submission and preserve draft/server roles on rejection.
- [ ] T037 [US3] Run T029-T032 against all delivered identity creation/lifecycle paths and a disposable migrated PostgreSQL database; record actual hook paths, migration filenames, preserved assignments and zero-roleless/usable-Admin verification in `specs/003-user-role-management/quickstart.md`.

**Checkpoint**: No supported creation, edit, migration or lifecycle path leaves an account roleless or removes the last usable Admin.

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T038 [P] Verify safe failure output in `backend/src/test/java/at/mci/igp/raumlotse/controller/UserRoleErrorHandlingTest.java` and fix `backend/src/main/java/at/mci/igp/raumlotse/exception/GlobalExceptionHandler.java` as needed: malformed input, database failure and timeout responses/logs reveal no submitted personal data, identifiers, SQL or stack traces; existing Problem consumers remain compatible.
- [ ] T039 [P] Run keyboard, screen-reader status/focus, conflict focus and small-viewport acceptance for `frontend/src/components/UserRoles/UserRoleEditor.tsx`, `frontend/src/pages/UserRoleListPage.tsx` and `frontend/src/pages/UserRolePage.tsx`; record findings and fix accessibility issues in those files.
- [ ] T040 Run backend Maven tests with real PostgreSQL concurrency coverage and frontend tests/lint/build using commands in `specs/003-user-role-management/quickstart.md`; retain failures-before/passes-after evidence and verify existing room/catalog regression tests without changing their permissions.
- [ ] T041 Execute all browser acceptance scenarios in `specs/003-user-role-management/quickstart.md` with real authenticated accounts, time the locate/edit/reopen journey under two minutes, record SC-001 through SC-008 evidence and keep release blocked for missing prerequisite capabilities or failed invariants.
- [ ] T042 Finalize operational handoff and rollout/rollback instructions in `specs/003-user-role-management/quickstart.md` and `specs/003-user-role-management/contracts/authentication.md`: concrete modules/keys/migrations, existing deployment secret injection, readiness checks, no direct role writes, no blind mutation replay and secure prerequisite-owned initial Admin provisioning; do not commit credentials.

## Dependencies & Execution Order

### Phase dependencies

Setup → Foundation → US1 → US2 → US3 → Polish is the default implementation order. All stories have priority P1; ordering follows the specification. The production adapter, migrations and lifecycle wiring additionally depend on the delivered authentication prerequisite from feature 004.

T001 identifies that external gate; missing authentication does not prevent writing isolated tests, DTOs or UI with contract fixtures, but does block finishing the production foundation and acceptance/release. Test fixtures must never become runtime authorization fallbacks.

Within foundation: T003/T004 → T005 → T006 → T007 → T008 → T009 → T010. T006/T008 require the real prerequisite mapping; T002 must resolve migration names first. The role feature remains unavailable until T034 readiness succeeds.

### Story dependencies

- US1 depends on the foundation's current-role checks, minimum-role rules and last-Admin primitive; security is present from its first endpoint. US1 is independently testable with seeded verified accounts.
- US2 builds on US1 endpoints/routes and hardens/proves access loss and concurrency. Its matrix and race tests can be prepared alongside US1 tests, but must run against the integrated implementation.
- US3 uses the common foundation and prerequisite lifecycle hooks. Its migration/lifecycle tests and service can be developed alongside US2 after US1, but shared safety/DTO/editor changes must be serialized.
- Polish and release require all three stories and real authentication. The functional US1 MVP is an isolated demonstration, not a releasable subset omitting US2/US3 guarantees.

### Within each story

Tests fail before corresponding implementation. Backend order is DTOs/models → services → controllers; frontend types/client precede pages/editor orchestration. Marked test tasks use separate files within their wave. T017 can run alongside T014-T016 after test failures are captured. T025/T035 and T027/T036 touch shared files and must not run concurrently.

## Parallel Examples

### User Story 1

After foundation, prepare T011 controller contracts, T012 service/integration tests and T013 UI tests concurrently. After their red runs, backend T014-T016 can proceed alongside T017 types/client. Then complete T018-T021 integration in order.

### User Story 2

After US1, prepare T022 authorization tests, T023 database races and T024 route/navigation tests concurrently. Backend T025 and frontend T026 can then proceed in separate files; T027-T028 integrate and validate their combined behavior.

### User Story 3

After US1 and prerequisite availability, prepare T029 lifecycle, T030 migration, T031 server-validation and T032 client-validation tests concurrently. T033 lifecycle work and T034 migration/readiness work use different files but share the database contract and must agree before validation. Serialize shared T035/T036 edits with any ongoing US2 work.

### Cross-cutting

T038 error-sanitization verification and T039 accessibility verification use separate files and can run concurrently; record combined evidence afterward in T040-T042.

## Implementation Strategy

1. Prepare the handoff and tests while authentication 004 is pending; do not guess account storage, disable CSRF or introduce an implicit Admin.
2. Once available, finish the foundation and implement US1 for the smallest reviewable Admin list/editor demonstration.
3. Complete US2's full authorization and concurrency evidence, then US3's account lifecycle and migration guarantees. Keep the role feature unavailable until readiness checks pass.
4. Run full quality gates and real browser acceptance, then deliver the complete P1 scope through the existing feature-to-dev workflow.

## Coverage and Notes

| Requirements / outcomes | Primary tasks |
|---|---|
| FR-001/004/007/010, SC-002 | T005, T012, T014-T015, T031-T036 |
| FR-002/008/011, SC-003/006 | T004, T008-T009, T022-T028 |
| FR-003/009, SC-001 | T011-T021, T039, T041 |
| FR-005/006/013, SC-004/005 | T029-T037 |
| FR-012/014, SC-007 | T009, T023, T025, T029, T031, T033 |
| FR-015, SC-008 | T011-T013, T015, T020, T023 |
| Contract errors, privacy, release gates | T010, T016, T028, T034, T038-T042 |

All tasks start unchecked because this command generates implementation work, not completed-code claims. Migration filenames are intentionally resolved by T002 only after prerequisite migrations are present; both migration tasks name their concrete directory and require the recorded exact filenames before execution. No account table or login implementation is added by this feature.
