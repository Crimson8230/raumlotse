# Bug Assessment: Duplicate Flyway migration version 9 prevents backend startup

- **Slug**: duplicate-flyway-v9
- **Created**: 2026-10-01T17:05:00+02:00
- **Source**: pasted text
- **Verdict**: valid
- **Severity**: high

## Report (verbatim or summarized)

The Docker backend exits during startup. Flyway reports two migrations with version 9:

```text
Caused by: org.flywaydb.core.api.FlywayException: Found more than one migration with version 9
Offenders:
-> .../db/migration/V9__add_barrier_free_attributes.sql
-> .../db/migration/V9__create_room_device_control.sql
raumlotse-backend exited with code 1
```

## Symptom

The backend cannot create its Flyway bean or start because Flyway refuses to resolve two migrations sharing version `9`. The expected behavior is a unique, ordered migration set that allows the container to start and applies both schema changes exactly once.

## Reproduction

1. Build/start the backend Docker container with the current packaged resources.
2. Let `FlywayConfig.flyway()` invoke `Flyway.migrate()` during Spring context creation.
3. Observe `Found more than one migration with version 9` and container exit code 1.

The repository also exposes the same defect through `MigrationVersionTest`, which scans packaged `V*__*.sql` resources and asserts unique `MigrationVersion` values.

## Suspected Code Paths

- `backend/src/main/resources/db/migration/V9__add_barrier_free_attributes.sql` — existing version 9 migration for barrier-free room attributes.
- `backend/src/main/resources/db/migration/V9__create_room_device_control.sql` — second version 9 migration for reservation ownership and room-device state.
- `backend/src/main/resources/db/migration/V10__add_reserved_for_and_user_reservation_index.sql` — confirms version 10 is already allocated.
- `backend/src/main/java/at/mci/igp/raumlotse/config/FlywayConfig.java:19-24` — invokes Flyway migration during application startup.
- `backend/src/test/java/at/mci/igp/raumlotse/MigrationVersionTest.java:12-29` — detects duplicate packaged migration versions.

## Root Cause Hypothesis

The room-device-control feature introduced `V9__create_room_device_control.sql` without accounting for the already existing `V9__add_barrier_free_attributes.sql` migration from room search/filter. The merge therefore created a duplicate Flyway version. Confidence: high; both files are present in the source tree and the runtime stack trace names the same two resources.

## Proposed Remediation

**Preferred**: Rename `V9__create_room_device_control.sql` to the next unused migration version, `V11__create_room_device_control.sql`, while leaving the established barrier-free `V9` and reservation `V10` migrations unchanged. Update feature documentation that names the migration and run `MigrationVersionTest` plus a Flyway-backed startup/integration check. This gives every migration a unique monotonic version and preserves the intended schema order: barrier-free attributes, reserved-for/index changes, then device-control schema.

Before applying this rename to an already deployed database, inspect `flyway_schema_history`. If the device-control SQL was ever applied under version 9, a controlled deployment migration/history repair is required; do not blindly run the renamed SQL because it would attempt to add already-existing columns/tables. The current Docker failure is consistent with the duplicate resources being packaged together, so fresh environments should be repaired by the unique filename alone.

**Alternatives**:
- Rename the barrier-free migration instead. This would conflict with the established feature-008 design and may require more history handling; not recommended.
- Merge the device-control SQL into another migration or make it conditional. This obscures migration history and is riskier than assigning the next unique version.

**Files likely to change**:
- `backend/src/main/resources/db/migration/V9__create_room_device_control.sql` (renamed to `V11__create_room_device_control.sql`)
- `specs/006-room-device-control/plan.md`
- `specs/006-room-device-control/tasks.md`
- `specs/006-room-device-control/data-model.md`
- `backend/src/test/java/at/mci/igp/raumlotse/MigrationVersionTest.java` (only if additional regression coverage is needed; existing test already detects this class of defect)

## Tests to add or update

- Run `./mvnw -q -Dtest=MigrationVersionTest test` and confirm the duplicate-version failure disappears.
- Run backend test compilation and a Flyway-backed application/integration startup check to ensure `V11` applies after `V10`.
- Verify the packaged migration resources contain one entry per version and retain the complete device-control SQL.
- If an existing database has migration history for device control under V9, add or execute a deployment-specific verification for that history before release.

## Risks & Considerations

- Renaming a Flyway migration after it has been applied changes migration history identity; deployed databases need explicit history/schema inspection and a rollout plan.
- Fresh databases will apply the device-control migration after V10, so all referenced tables and columns must already exist as expected.
- Documentation and feature task references currently name the colliding V9 file and must be updated to avoid recreating the defect.

## Open Questions

- [NEEDS CLARIFICATION: Has `V9__create_room_device_control.sql` been applied to any shared or production-like database before this merge, or is the failure limited to fresh Docker/test databases?]

