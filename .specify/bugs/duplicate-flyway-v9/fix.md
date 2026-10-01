# Bug Fix: Duplicate Flyway migration version 9 prevents backend startup

- **Slug**: duplicate-flyway-v9
- **Fixed**: 2026-10-01T17:08:00+02:00
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

The room-device-control migration was assigned the next unused Flyway version, `V11`, eliminating the collision with the established barrier-free `V9` migration. Feature documentation was updated so the migration numbering remains consistent.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `backend/src/main/resources/db/migration/V9__create_room_device_control.sql` | renamed | Replaced by the unique `V11__create_room_device_control.sql`. |
| `backend/src/main/resources/db/migration/V11__create_room_device_control.sql` | added | Preserves the complete room-device-control schema migration. |
| `specs/006-room-device-control/plan.md` | modified | References `V11__create_room_device_control.sql`. |
| `specs/006-room-device-control/tasks.md` | modified | Updates task T007 to the unique migration filename. |
| `specs/006-room-device-control/data-model.md` | modified | Updates the migration reference. |

## Diff Highlights

- Migration sequence is now `V9__add_barrier_free_attributes.sql`, `V10__add_reserved_for_and_user_reservation_index.sql`, and `V11__create_room_device_control.sql`.
- The migration SQL itself was preserved; only its Flyway version/name changed.

## Tests Added or Updated

- No test code changes were needed. Existing `MigrationVersionTest::packagedMigrationsHaveUniqueFlywayVersions` directly covers this failure mode.

## Local Verification

- Commands run: `./mvnw -q clean -Dtest=MigrationVersionTest test` → passed after cleaning stale generated resources; the packaged migration set has unique versions.
- Commands run: `./mvnw -q -DskipTests test-compile` → passed.
- Commands run: `git diff --check` → passed.
- Manual checks: confirmed the source migration directory contains versions V1 through V11 without duplicate version numbers and retains the full device-control SQL.

## Deviations from Assessment

None.

## Follow-ups

- Before deploying to an existing database, inspect `flyway_schema_history` to determine whether the device-control SQL was previously applied under version 9. If so, execute a controlled migration-history/schema transition rather than applying `V11` blindly.
- Run the Docker startup/integration validation against the target PostgreSQL environment.

