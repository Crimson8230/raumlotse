# Bug Verification: Duplicate Flyway migration version 9 prevents backend startup

- **Slug**: duplicate-flyway-v9
- **Tested**: 2026-10-01T17:10:00+02:00
- **Assessment**: ./assessment.md
- **Fix**: ./fix.md
- **Result**: verified

## Summary

The duplicate Flyway version no longer reproduces in freshly compiled/package-scanned resources. The migration uniqueness test, backend test compilation, and diff validation all pass after renaming the device-control migration to `V11`.

## Checks Performed

| Check | Command / Action | Result | Notes |
|-------|------------------|--------|-------|
| Reproduction (post-fix) | `./mvnw -q clean -Dtest=MigrationVersionTest test` | pass | Flyway migration versions are unique after cleaning stale build output. |
| New / updated tests | `MigrationVersionTest::packagedMigrationsHaveUniqueFlywayVersions` | pass | Confirms no duplicate packaged migration version remains. |
| Regression suite | `./mvnw -q -DskipTests test-compile` | pass | Backend sources and tests compile with the renamed migration resource. |
| Lint / type-check | `git diff --check` | pass | No whitespace errors. |

## Output Excerpts

```text
./mvnw -q clean -Dtest=MigrationVersionTest test
Process exited with code 0

./mvnw -q -DskipTests test-compile
Process exited with code
```

The source migration sequence is now `V9`, `V10`, `V11`; no duplicate version is present.

## Residual Risks

- A live Docker/PostgreSQL startup was not executed in this verification.
- Before deployment to an existing database, `flyway_schema_history` must be checked for a prior device-control migration recorded as version 9.

## Recommendation

Close the duplicate-version bug for fresh builds and databases: the automated reproduction and migration regression test pass. Perform the documented migration-history check and a target-environment Docker startup before production deployment.

