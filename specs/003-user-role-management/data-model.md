# Data Model: User Role Management

## Entities

| Entity | Fields / ownership | Constraints |
|---|---|---|
| User reference | Stable opaque userId, displayName, unique accountLabel; identity owns account | API uses nonempty string id; persistence binds to prerequisite native key |
| UserRoleState | userId primary/foreign key, rolesVersion bigint initially 0 | One per account; version never decreases |
| RoleAssignment | userId, roleCode | Composite primary key; FK to UserRoleState; code constrained to supported values |
| RoleMutationGuard | id fixed to 1 | One seeded row; never publicly exposed |

Codes/labels: ADMIN / Admin; UNIVERSITY_STAFF / University Staff; STUDENT / Student; LECTURER / Lecturer; VIEWER / Viewer. No hierarchy or implied membership.

Every account has 1-5 unique assignments. Nonempty-set and last-Admin constraints are transaction invariants enforced by the common service, not simple row CHECK constraints. Database constraints reject duplicates, unknown codes and orphan assignments. Index roleCode for Admin membership queries.

## State transitions and transaction order

Ordinary account creation -> role state version 0 and VIEWER in the same transaction. Initial Admin provisioning is a separate secure operation owned by the prerequisite, never a registration parameter.

1. Resolve authenticated actor identity.
2. Begin READ COMMITTED transaction and lock the guard row.
3. Freshly read actor roles and deny without ADMIN; never trust sign-in-time role claims.
4. Load target and role state; return not found if absent.
5. Validate 1-5 unique known roles and required expectedVersion.
6. Compare version before unchanged-selection detection; mismatch rejects the whole request.
7. If removing ADMIN, ensure another Admin remains, including self-removal.
8. Replace assignments and increment version for a change; unchanged current selection returns the current version.
9. Commit before returning success; any failure rolls back all changes.

Lock order: guard, actor/target state, assignments. Avoid entities loaded into a persistence context before acquiring the guard; use fresh reads/projections. All mutation paths share the guard. Initial engineering lock-wait bound: five seconds; timeout rolls back and returns temporary unavailability, never automatic replay.

Protected reads check persisted roles per request. Editor roles/version must be one consistent snapshot, through a single query/projection or equivalent snapshot transaction.

## Migration and lifecycle

Allocate Flyway versions after prerequisite migrations merge. Reference the actual account key; create no parallel users table. Create guard/state/assignment constraints. Preserve recognized assignments; add Viewer only where none exist. Reject unsupported legacy roles rather than guessing mappings. Verify zero roleless users and at least one Admin before enabling this feature.

Creation hooks, provisioning and deletion-related role writers use the shared guard and lock order. If the identity feature supports deletion/deactivation, it must coordinate so it cannot remove the last usable Admin. This is a handoff obligation, not a new lifecycle endpoint. Direct role edits outside the common service are unsupported.

## Version examples

Version 4 + changed set -> 5.
Version 4 + same set with expectedVersion 4 -> 4.
Version 5 + expectedVersion 4 -> conflict, even for the same set.
A -> B -> A increments twice, so an editor holding the first A remains stale.

