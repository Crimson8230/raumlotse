# Quickstart: Room Device Control

## Prerequisites

- Docker Compose services are running, or the existing backend and frontend development commands are available.
- A test user account exists and can log in.
- A room exists with standard lighting and ventilation.
- A second room or test fixture can be configured with an active `PROJECTOR` equipment assignment.
- A reservation can be created and transitioned to `ACTIVE` using the existing reservation workflow.

## Automated validation

Run from the repository root:

```bash
cd backend && ./mvnw test
cd ../frontend && npm test -- --run
cd ../frontend && npm run build
cd ../frontend && npm run lint
```

The feature tests must cover:

1. A booking user's `ACTIVE` in-window GET returns lighting and ventilation.
2. An `ACTIVE` booking user's command succeeds at the start boundary and fails at the end boundary.
3. `RESERVED`, terminal, expired, and cancelled reservations are rejected.
4. A different authenticated user is rejected even when they know the room or reservation ID.
5. A configured active `PROJECTOR` adds projector capability; absent or deactivated projector configuration does not.
6. A failed device operation returns a structured error and leaves the prior state unchanged.
7. Frontend controls render loading, available, forbidden, unavailable-projector, and command-failure states.
8. Capability reads and commands meet the documented p95 response target of 1 second; successful state refresh is visible within 2 seconds.
9. A usability review confirms that a booking user can locate and operate an available device within 30 seconds.

## Manual end-to-end check

1. Log in as the reservation owner and open the room detail page.
2. Open the room-device-control view while the reservation is `RESERVED`; verify that controls are unavailable.
3. Transition the reservation to `ACTIVE`, set the current time inside the interval, and open the controls again.
4. Verify lighting and ventilation are present. Verify projector presence matches the Room Management equipment assignment.
5. Toggle a device and verify the returned state is reflected after reload.
6. Repeat as another authenticated user; expect `403` and no state change.
7. Repeat at or after `endTime`, and after cancellation or completion; expect rejection and no state change.
8. Remove/deactivate the projector assignment and reload; projector control must disappear and direct projector commands must be rejected.

## Contract references

- REST shapes and status codes: [room-device-control-api.yaml](./contracts/room-device-control-api.yaml)
- Entities, constraints, and authorization predicate: [data-model.md](./data-model.md)
