# Implementation Plan: Room Device Control

**Branch**: `006-room-device-control` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-room-device-control/spec.md`

## Summary

Add authenticated room-device control for lighting, ventilation, and optionally a projector. The backend authorizes every read and command against the authenticated booking user, an `ACTIVE` reservation for the same room, and `[startTime, endTime)`. Room Management remains the source of truth for projector presence through a canonical equipment code. Device state is persisted per room/device kind so the UI can display the last confirmed state and report failed commands without claiming success.

## Technical Context

**Language/Version**: Java 21 / Spring Boot backend; TypeScript 6 / React 19 frontend

**Primary Dependencies**: Spring Web MVC, Spring Data JPA, Bean Validation, Flyway, Spring Security; React Router, existing typed API client, Vitest, React Testing Library

**Storage**: PostgreSQL 17; one migration for reservation ownership, canonical equipment identification, and room-device state

**Testing**: Backend JUnit/Spring integration tests and repository/service tests; frontend Vitest, React Testing Library, TypeScript build, and ESLint

**Target Platform**: Authenticated browser client and Spring Boot service running in the existing Docker Compose deployment

**Project Type**: Full-stack web application with REST API and React SPA

**Performance Goals**: Device availability reads and commands complete within 1 second at p95 under normal local deployment; successful state refresh is visible within the 2-second product target

**Constraints**: No command may bypass authorization; `ACTIVE` status and `[startTime, endTime)` are checked for every request; projector controls are absent unless Room Management reports an enabled projector; no new runtime dependency or hardware protocol is introduced

**Scale/Scope**: One room and its three device kinds per request; lighting and ventilation are available by default for every room; projector presence is optional; administrative override, scheduling, scenes, device telemetry, and real hardware protocol implementations are out of scope

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Rule | Status | Validation in this Feature Design |
|---|---|---|
| **I. Test-First Development (NON-NEGOTIABLE)** | PASS | Add failing authorization, boundary, projector-availability, command-failure, and UI tests before production code. |
| **II. Modern, Typed, and Consistent Codebases** | PASS | Use Java 21 layered controller/service/repository code, constructor injection, Bean Validation, strict TypeScript, functional React components, and ESLint. |
| **III. Contract-First API Design** | PASS | Define the device-control GET and command POST contracts in [contracts/room-device-control-api.yaml](./contracts/room-device-control-api.yaml) before implementation. |
| **IV. Secure and Data-Respecting by Default** | PASS | Authorize from the server-side authenticated user and persisted owner ID; validate room/device identifiers and command payloads; do not log user data or device payloads. |
| **V. Simplicity and Observability** | PASS | Add one device service boundary and a small device-state model; return structured errors and log command failures without credentials or personal data. |
| **Technology Stack Constraints** | PASS | Reuse the existing Spring Boot, PostgreSQL, Flyway, React, TypeScript, and Vitest stack without an external dependency. |

## Project Structure

### Documentation (this feature)

```text
specs/006-room-device-control/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── room-device-control-api.yaml
└── tasks.md                 # Phase 2; not created by this command
```

### Source Code (repository root)

```text
backend/src/main/java/at/mci/igp/raumlotse/
├── controller/RoomDeviceController.java
├── service/RoomDeviceGateway.java
├── service/PersistedRoomDeviceGateway.java
├── domain/RoomDeviceKind.java
├── domain/RoomDeviceState.java
├── dto/RoomDeviceResponse.java
├── dto/RoomDeviceCommandRequest.java
├── repository/RoomDeviceStateRepository.java
└── service/RoomDeviceService.java

backend/src/main/resources/db/migration/V9__create_room_device_control.sql

frontend/src/
├── API/roomDevices.ts
├── components/RoomDeviceControls/
│   ├── RoomDeviceControls.tsx
│   ├── RoomDeviceControls.css
│   └── RoomDeviceControls.test.tsx
├── pages/RoomDeviceControlPage.tsx
├── pages/RoomDeviceControlPage.test.tsx
└── types/roomDevice.ts
```

Existing `Reservation`, `ReservationService`, `RoomService`, `ReservationController`, room detail navigation, authentication context, and shared API error handling are extended where required. `RoomDeviceGateway` is the replaceable device-operation seam; `PersistedRoomDeviceGateway` provides the local deterministic implementation for this version, without introducing a vendor-specific hardware protocol.

**Structure Decision**: Keep the feature in the existing backend/frontend layers. The backend owns authorization and state changes; the frontend only renders capabilities returned by the backend and never decides whether a command is allowed.

## Complexity Tracking

No constitution violations. The additional reservation owner ID and device-state table are required because comparing a free-form creator name is not a safe authorization mechanism and the UI must display the last confirmed state across requests.

## Phase 0: Research Summary

See [research.md](./research.md) for decisions on authorization ownership, reservation boundary evaluation, projector discovery, device-state persistence, gateway acknowledgement, and command failure behavior.

## Phase 1: Design Summary

- [data-model.md](./data-model.md) defines reservation ownership, canonical equipment identification, room-device state, and validation/state rules.
- [contracts/room-device-control-api.yaml](./contracts/room-device-control-api.yaml) defines the read and command REST contracts, status codes, and problem responses.
- [quickstart.md](./quickstart.md) defines automated and manual validation scenarios for authorization, time boundaries, optional projector control, and failed commands.

## Post-Design Constitution Check

All gates remain PASS. The design keeps authorization server-side, uses explicit contracts and typed DTOs, introduces no new dependency, and makes device failures observable through structured problem responses and safe log events.
