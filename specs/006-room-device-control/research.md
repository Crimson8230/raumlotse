# Research: Room Device Control

**Feature**: [spec.md](./spec.md)

## Decision 1: Authorize with a persisted reservation owner ID

**Decision**: Add a nullable `created_by_user_id` foreign key to reservations for compatibility with historical records, require it for newly created reservations, and keep `createdBy` as the display/audit snapshot. Device control compares the authenticated `AuthenticatedUser.userId()` with `created_by_user_id`.

**Rationale**: The current reservation model stores `createdBy` as free text. Display names are not a stable or secure authorization key. The existing security filter already exposes a stable account UUID.

**Alternatives considered**: Comparing display names was rejected because names can collide or change. Making the new column immediately non-null was rejected because existing reservations need a safe migration path; historical reservations without an owner cannot authorize device control.

## Decision 2: Use `ACTIVE` and a half-open interval for every request

**Decision**: A command is authorized only when reservation status is `ACTIVE`, the reservation belongs to the requested room and authenticated user, and `startTime <= now < endTime`. Perform this check inside the service for both capability reads and state-changing commands.

**Rationale**: This exactly matches the clarified specification and prevents a stale UI or crafted request from bypassing the time boundary. The half-open interval makes the end instant unambiguously unavailable.

**Alternatives considered**: Authorizing only when the control page loads was rejected because reservations can be cancelled or reach their end while the page remains open. Treating `RESERVED` as confirmed was rejected by clarification.

## Decision 3: Identify projectors with a canonical equipment code

**Decision**: Extend the equipment catalog with a stable `code` (for example, `PROJECTOR`) and have Room Management assign that code to projector equipment. Projector capability is returned only when a room has an active assigned equipment type with that code.

**Rationale**: Matching the localized display name `Projector` is fragile and would make availability dependent on spelling or translation. The existing room-to-equipment relationship remains the source of truth.

**Alternatives considered**: Matching names case-insensitively was rejected as not stable enough for authorization and API behavior. Adding a separate projector flag to `Room` was rejected because it duplicates Room Management data.

## Decision 4: Use an explicit device gateway and persist the last confirmed state

**Decision**: Define a `RoomDeviceGateway` interface for device operations and provide `PersistedRoomDeviceGateway` as the local implementation. Store one `RoomDeviceState` row per room and device kind (`LIGHTING`, `VENTILATION`, `PROJECTOR`) with a boolean operational state and update timestamp. Persist the state only after the gateway acknowledges the command.

**Rationale**: The current repository has no hardware protocol integration. The explicit gateway makes acknowledgement and failure behavior testable now while allowing a future hardware adapter to replace the local implementation without changing authorization or the REST contract.

**Alternatives considered**: In-memory state was rejected because it disappears on restart and is inconsistent across service instances. Calling the repository directly from the controller was rejected because it cannot represent device acknowledgement or future hardware integration. Adding a third-party smart-building dependency was rejected because no protocol or vendor is specified and it violates the simplicity constraint.

## Decision 5: Return capability-specific problem responses

**Decision**: Return `401` for unauthenticated requests, `403` for authenticated users without an eligible reservation, `404` for unknown rooms/devices, `409` for unavailable projector configuration or command conflicts, and `503` when the device operation cannot be completed. Do not update persisted state after a failed operation.

**Rationale**: The existing application already uses structured `Problem` responses and global exception handling. Distinct status categories let the UI show a safe, actionable state without exposing authorization details unnecessarily.

**Alternatives considered**: Returning `200` with a disabled flag for all unauthorized requests was rejected for command endpoints because it weakens the authorization boundary. Returning success before device acknowledgement was rejected because it violates the specification's failure semantics.
