# Phase 0 Research: Room Management

All unknowns from the Technical Context are resolved below.

## 1. Database migration tool

- **Decision**: Flyway (`flyway-core` + `flyway-database-postgresql`), with SQL migrations under `backend/src/main/resources/db/migration/`.
- **Rationale**: Plain versioned SQL keeps migrations readable and reviewable, which fits the constitution's simplicity principle better than an XML/YAML changelog format. The project already runs PostgreSQL via Docker Compose, so Flyway can target it directly in every environment (local, CI, prod-like).
- **Alternatives considered**: Liquibase — more powerful (multi-DB abstraction, rollback tooling) but heavier for a single-database, single-team project; rejected as unnecessary complexity. Hibernate `ddl-auto` — rejected outright, since implicit schema generation is unreviewable and unsafe for a project that will accumulate real data.
- **Correction (discovered during implementation)**: unlike earlier Spring Boot versions, Spring Boot 4.1.1 does **not** auto-configure Flyway — there is no `FlywayAutoConfiguration` (or any Flyway-aware class) anywhere on the resolved classpath, confirmed by scanning every `AutoConfiguration.imports` file across the project's full dependency tree. Placing `spring.flyway.*` properties in `application.yaml` has no effect. Migrations must be triggered manually via a `Flyway` bean (see `backend/src/main/java/at/mci/igp/raumlotse/config/FlywayConfig.java`), whose factory method calls `.migrate()` as a side effect during context refresh — which completes before the embedded web server starts accepting requests, so ordering is safe without any extra `@DependsOn` wiring.

## 2. Backend persistence & entity IDs

- **Decision**: Spring Data JPA over the new PostgreSQL dependency; `UUID` primary keys (DB-generated via `gen_random_uuid()`, requires the `pgcrypto`/`pgcrypto`-free `gen_random_uuid()` available natively in PostgreSQL 13+) for `Room` and `EquipmentType`.
- **Rationale**: Spring Data JPA is the idiomatic persistence layer for Spring Boot (constitution Principle II) and keeps the repository layer thin. UUIDs avoid leaking sequential counts of rooms/equipment through the API and are the conventional choice for REST resource identifiers that may eventually be referenced by other systems (per the project's stated goal of integrating with university infrastructure).
- **Alternatives considered**: Auto-increment `BIGINT` keys — simpler and marginally faster, but rejected because they leak business volume (`/api/rooms/42` implies ~42 rooms exist) and are harder to keep stable if data is ever merged across environments.

## 3. Optimistic concurrency control (FR-017)

- **Decision**: A `@Version` column (`bigint`) on the `Room` entity, using JPA's built-in optimistic locking. The `version` value is round-tripped through the API: returned in `RoomResponse`, required in room-update requests, and JPA throws `OptimisticLockingFailureException` on a stale write, which is translated to `409 Conflict`.
- **Rationale**: This is the standard, minimal-code way to implement optimistic locking in Spring Data JPA and requires no extra infrastructure — it satisfies FR-017 exactly (reject a save based on stale data) without building custom conflict-detection logic.
- **Alternatives considered**: `updated_at` timestamp compare-and-swap in a custom query — functionally equivalent but reinvents what `@Version` already does; rejected as unnecessary custom code.

## 4. Backend integration testing strategy

- **Decision**: Testcontainers (`org.testcontainers:postgresql`, JUnit 5 module) spinning up a real PostgreSQL 17 container for repository and full-stack (`@SpringBootTest`) integration tests; `@WebMvcTest` + MockMvc with mocked services for controller-layer contract tests; plain JUnit + Mockito for service-layer unit tests.
- **Rationale**: The constitution's Test-First principle expects tests that give real correctness guarantees; testing against an in-memory substitute (H2) risks passing tests that fail against real PostgreSQL (e.g., UUID handling, constraint behavior, case sensitivity) — the exact kind of mock/prod divergence that is costly to discover late. Testcontainers reuses the same `postgres:17-alpine` image already pinned in `docker-compose.yml`, so behavior is consistent between tests and local/deployed environments.
- **Alternatives considered**: H2 in-memory database — fast and simple, but PostgreSQL-specific behavior (UUID generation, unique constraints, case-sensitive collation) would not be verified; rejected in favor of fidelity.

## 5. Frontend testing strategy

- **Decision**: Vitest + React Testing Library (`@testing-library/react`, `@testing-library/jest-dom`), configured via Vite's existing `vite.config.ts` (`test` block) — no separate test runner config needed.
- **Rationale**: Vitest is designed as a drop-in for Vite projects and requires no parallel build/transform configuration (unlike Jest, which would need its own Babel/ts-jest setup duplicating what Vite already does). React Testing Library is the de facto standard for testing React function components from a user-interaction perspective, matching the constitution's "functional React components" requirement and this feature's form-heavy UI (room form, equipment picker).
- **Alternatives considered**: Jest — mature and widely used, but requires extra configuration to work with Vite's ESM/TS pipeline that Vitest gets for free; rejected to keep tooling minimal (Principle V).

## 6. API contract format

- **Decision**: OpenAPI 3.0 YAML (`contracts/openapi.yaml`), hand-written and versioned alongside the spec, covering every endpoint's request/response schema and status codes.
- **Rationale**: OpenAPI is the standard, tool-agnostic contract format for REST APIs, satisfies Principle III (contract before/alongside implementation) precisely, and gives the frontend a single source of truth for request/response shapes without introducing a code-generation dependency this small feature doesn't need.
- **Alternatives considered**: Generating the contract from backend annotations (springdoc-openapi) at runtime — rejected for Phase 1 because it would require implementation to exist before the contract could be produced, inverting the contract-first order; can be adopted later as a documentation convenience without changing this plan.

## 7. Building/Floor hierarchy and cascade deactivation

- **Decision**: `Floor` has a required `buildingId` foreign key (a floor belongs to exactly one building — per the change-request clarification). Deactivating a `Building` cascades to deactivate all its `Floor` rows in the same transaction (application-level cascade in `BuildingService`, not a DB trigger); reactivating a `Building` does not cascade back — each `Floor` must be reactivated individually.
- **Rationale**: A floor with `status = ACTIVE` under a deactivated building would let a room be created against a "half-active" location, which is a contradiction the API would otherwise have to re-check on every room write. Doing the cascade once, at building-deactivation time, keeps the "is this floor assignable?" check a single `floor.status == ACTIVE AND floor.building.status == ACTIVE` read. Not cascading reactivation avoids silently reopening floors the administrator may have deliberately deactivated for other reasons before the building itself was deactivated.
- **Alternatives considered**: Checking both statuses on every read/write without cascading — rejected as strictly more error-prone for no benefit, since the cascade is a one-time, easily testable operation. A DB trigger — rejected as it would hide business logic outside the service layer and Flyway-versioned schema, against Principle V (observability/simplicity).

## 8. Equipment catalog seed data

- **Decision**: Flyway migration seeds the equipment catalog with the two equipment types named in the source request — `Projector` and `Whiteboard` — as regular (non-privileged) rows the administrator can rename or deactivate like any other catalog entry.
- **Rationale**: Matches the documented assumption that the catalog is "seeded with at least Projector and Whiteboard"; keeps seed data as ordinary migrated rows rather than hard-coded application constants, consistent with FR-014 treating all equipment types uniformly.
- **Alternatives considered**: No seed data, requiring an administrator to create these two types manually on first use — rejected as unnecessary friction for the two equipment types explicitly named in the feature request.
