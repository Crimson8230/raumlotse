# Bug Fix: Reservation ownership API mismatch breaks backend compilation

- **Slug**: 006-room-device-control
- **Fixed**: 2026-10-01T17:00:00+02:00
- **Assessment**: ./assessment.md
- **Status**: applied

## Summary

Reservation creation now passes the authenticated `AuthenticatedUser` through the controller and service. The service derives the stable UUID owner and display snapshot from that identity, resolving the Mockito generic compilation failure and aligning new reservations with the device-authorization query.

## Changes

| File | Change | Notes |
|------|--------|-------|
| `backend/src/main/java/at/mci/igp/raumlotse/controller/ReservationController.java` | modified | Passes `AuthenticatedUser` to reservation creation and adapts the upcoming-reservations lookup. |
| `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` | modified | Accepts the typed identity, persists `createdByUserId`, and derives `createdBy` from the authenticated display name. |
| `backend/src/main/java/at/mci/igp/raumlotse/dto/ReservationCreateRequest.java` | modified | Keeps the legacy constructor field for source compatibility but excludes `createdBy` from JSON input/output. |
| `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationControllerTest.java` | modified | Verifies the typed authenticated-owner service call. |
| `backend/src/test/java/at/mci/igp/raumlotse/ReservationCreationIntegrationTest.java` | modified | Uses `AuthenticatedUser` and asserts persisted UUID ownership. |
| `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationControllerTest.java` | modified | Corrects the blank `reservedFor` expectation to HTTP 400, matching `@NotBlank` validation. |

## Diff Highlights

- `ReservationOwnershipTest` now compiles because `ReservationService.createReservation(..., AuthenticatedUser)` matches `eq(user)`.
- New reservations set both `createdBy` from `user.displayName()` and `createdByUserId` from `user.userId()`.
- A client-provided `createdBy` value is no longer used by the authenticated controller path.

## Tests Added or Updated

- `ReservationOwnershipTest::controllerPassesAuthenticatedIdentityInsteadOfClientOwnerField` — existing regression test now compiles against the typed service contract and continues to reject client-owner substitution through its request fixture.
- `ReservationCreationIntegrationTest::createsReservationWithAuthenticatedUserAndReservedFor` — verifies the persisted `createdByUserId` equals the authenticated UUID and the response snapshot uses the authenticated display name.
- `ReservationControllerTest::createReservation_authenticatedUser_assignsCreatedByAndReservedFor` — updated Mockito expectation for `AuthenticatedUser`.
- `ReservationControllerTest::createReservation_blankReservedFor_returns400` — corrected the stale expected status from 201 to 400.

## Local Verification

- Commands run: `./mvnw -q -DskipTests test-compile` → passed.
- Commands run: `git diff --check` → passed.
- Commands run: `./mvnw -q -Dtest=ReservationOwnershipTest,ReservationControllerTest,ReservationServiceTest test` → passed; all focused ownership, controller, and service tests completed successfully.
- Commands run: `./mvnw -q -DskipTests test-compile` → passed.
- Commands run: `git diff --check` → passed.
- Manual checks: confirmed the changed service signature matches the ownership test and that the UUID ownership field is assigned before persistence.

## Deviations from Assessment

- The legacy `ReservationService.createReservation(UUID, ReservationCreateRequest)` overload and the `createdBy` record component remain for existing direct-service tests/source compatibility. The overload converts that legacy value to a deterministic internal identity; the authenticated controller path never trusts client input. Removing this compatibility path can be considered once older callers are migrated.
- The assessment mentioned changing `ReservationOwnershipTest`; its source did not need modification because the test already expressed the intended typed contract and compiles after the production signature change.
- A stale assertion in `ReservationControllerTest` was corrected after verification exposed it: blank `reservedFor` is rejected by the existing `@NotBlank` constraint with HTTP 400.

## Follow-ups

- Run the full backend test suite and Docker build in an environment with the required PostgreSQL/Docker services.
- Consider removing the legacy request field/overload after all direct service callers migrate to `AuthenticatedUser`.
