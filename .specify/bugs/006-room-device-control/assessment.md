# Bug Assessment: Reservation ownership API mismatch breaks backend compilation

- **Slug**: 006-room-device-control
- **Created**: 2026-10-01T16:47:02+02:00
- **Source**: pasted text
- **Verdict**: valid
- **Severity**: high

## Report (verbatim or summarized)

The backend cannot be built reliably in Docker after merging `006-room-device-control`. Maven reports:

```text
[ERROR] /app/src/test/java/at/mci/igp/raumlotse/controller/ReservationOwnershipTest.java:[34,24]
incompatible types: inference variable T has incompatible bounds
upper bounds: java.lang.String,java.lang.Object
lower bounds: at.mci.igp.raumlotse.dto.AuthenticatedUser
```

## Symptom

The reservation ownership test attempts to verify a service call with an `AuthenticatedUser`, but the service mock exposes `createReservation(UUID, ReservationCreateRequest, String)`. Java therefore cannot infer a compatible type for `eq(user)`, and test compilation aborts the backend/Docker build. The expected behavior is for reservation creation to pass the authenticated identity through the controller and persist stable ownership derived from it.

## Reproduction

1. Run the backend Maven test/compile lifecycle, for example `./mvnw test` from `backend/` or the project Docker backend build.
2. Compile `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationOwnershipTest.java`.
3. Observe the reported generic type-inference error at `verify(service).createReservation(eq(roomId), eq(request), eq(user));`.

## Suspected Code Paths

- `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationOwnershipTest.java:34` — verifies an `AuthenticatedUser` argument against the service mock.
- `backend/src/main/java/at/mci/igp/raumlotse/controller/ReservationController.java:39-57` — resolves `AuthenticatedUser` to a `String` before calling the service.
- `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java:67-143` — accepts a `String` owner and can fall back to the client-supplied `request.createdBy()` value.
- `backend/src/main/java/at/mci/igp/raumlotse/dto/ReservationCreateRequest.java:19-20` — still exposes `createdBy` in the client request model.
- `backend/src/main/java/at/mci/igp/raumlotse/domain/Reservation.java:60-64` — has both the display snapshot `createdBy` and stable `createdByUserId` ownership fields.
- `backend/src/main/java/at/mci/igp/raumlotse/repository/ReservationRepository.java:40-45` — filters eligible device reservations by `UUID createdByUserId`, so the creation path must populate that field.

## Root Cause Hypothesis

The merge partially implemented the authenticated-owner design: the ownership test and repository query use a typed `AuthenticatedUser`/UUID model, while the controller and reservation service retain the previous string-based API and client-controlled fallback. This is a high-confidence API contract mismatch, not merely a Mockito generic issue. The current code can also leave `createdByUserId` unset, which would make device authorization fail even after test compilation is repaired.

## Proposed Remediation

**Preferred**: Change the reservation creation boundary to pass the authenticated `AuthenticatedUser` from `ReservationController` to `ReservationService`. In the service, derive `createdByUserId` from `user.userId()` and the legacy/display `createdBy` snapshot from `user.displayName()`, reject a missing authenticated identity, and do not use `ReservationCreateRequest.createdBy` as an authoritative owner. Remove the owner field from the request contract or otherwise ignore it for ownership while preserving any compatibility required by existing response/data fixtures. Update the controller/service tests and existing callers to use the typed identity contract, then verify that newly created reservations populate both ownership fields.

This keeps the repository's UUID ownership query aligned with the persisted model and with the feature design in `specs/006-room-device-control/tasks.md` (T010, T046, T047). It fixes the reported compile error as a consequence of restoring one consistent method signature rather than weakening the test to compare a string.

**Alternatives**:
- Change only `ReservationOwnershipTest` to verify `user.userId().toString()`. This would restore compilation but leave the service API/client-owner fallback inconsistent with the feature and could leave `createdByUserId` unset; not recommended.
- Add an overloaded string-based service method alongside an `AuthenticatedUser` overload. This may ease compatibility temporarily, but risks retaining two ownership paths and allowing insecure caller-controlled identity; use only if a staged migration is required.

**Files likely to change**:
- `backend/src/main/java/at/mci/igp/raumlotse/controller/ReservationController.java`
- `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java`
- `backend/src/main/java/at/mci/igp/raumlotse/dto/ReservationCreateRequest.java`
- `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationOwnershipTest.java`
- `backend/src/test/java/at/mci/igp/raumlotse/controller/ReservationControllerTest.java`
- `backend/src/test/java/at/mci/igp/raumlotse/ReservationCreationIntegrationTest.java`
- Other backend tests/callers that compile against the changed reservation-creation signature, if required by the compiler.

## Tests to add or update

- Update `ReservationOwnershipTest` to compile against and verify the typed `AuthenticatedUser` service call, including that a client-supplied `createdBy` value cannot override the authenticated owner.
- Add or update an integration assertion that a newly created reservation stores `createdByUserId == authenticatedUser.userId()` and `createdBy == authenticatedUser.displayName()`.
- Verify unauthenticated or malformed principals are rejected and that the complete backend Maven test lifecycle compiles successfully.
- Retain coverage for legacy/historical reservations with nullable `createdByUserId` where device authorization requires it.

## Risks & Considerations

- Existing tests and service callers currently pass string owner values and will need a coordinated signature update.
- Removing `createdBy` from the request JSON can affect frontend payloads and API compatibility; at minimum it must no longer be trusted for authorization.
- Historical reservations may legitimately have a null `createdByUserId`; the device-control query must continue to exclude them safely.
- The fix changes ownership security behavior and should be verified with integration tests, not only Mockito interaction tests.

## Open Questions

- [NEEDS CLARIFICATION: Should the legacy `createdBy` request field be removed from the public request DTO immediately, or retained as an ignored/deprecated compatibility field?]
