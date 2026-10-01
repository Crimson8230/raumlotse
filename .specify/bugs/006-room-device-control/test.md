# Bug Verification: Reservation ownership API mismatch breaks backend compilation

- **Slug**: 006-room-device-control
- **Tested**: 2026-10-01T17:03:00+02:00
- **Assessment**: ./assessment.md
- **Fix**: ./fix.md
- **Result**: verified

## Summary

The reported Java generic compilation error no longer reproduces. Backend test compilation and the focused ownership, controller, and service regression suite all pass after correcting the stale blank-`reservedFor` expectation.

## Checks Performed

| Check | Command / Action | Result | Notes |
|-------|------------------|--------|-------|
| Reproduction (post-fix) | `./mvnw -q -DskipTests test-compile` | pass | Backend and test sources compile; the original `eq(AuthenticatedUser)` type error is gone. |
| New / updated tests | `./mvnw -q -Dtest=ReservationOwnershipTest,ReservationControllerTest,ReservationServiceTest test` | pass | Focused suite completed successfully. |
| Regression suite | Same focused Maven command | pass | Ownership, controller validation/authentication, and reservation service tests pass. |
| Lint / type-check | `git diff --check` | pass | No whitespace errors. |

## Output Excerpts

```text
./mvnw -q -DskipTests test-compile
Process exited with code

./mvnw -q -Dtest=ReservationOwnershipTest,ReservationControllerTest,ReservationServiceTest test
Process exited with code 0
```

The previously failing validation assertion now correctly expects HTTP 400 for blank `reservedFor`.

## Residual Risks

- The complete backend integration suite and Docker build were not run in this verification.
- The legacy two-argument service overload remains for source compatibility and should be removed after older direct callers migrate to `AuthenticatedUser`.
- Maven emitted standard Mockito/JDK dynamic-agent warnings; they did not affect test results.

## Recommendation

Close the bug for the reported compilation failure: the reproduction and focused regression checks pass. Run the full backend/Docker validation before release to cover integration infrastructure and container-specific behavior.

