# Research: User Role Management

Date: 2026-09-16. Evidence: feature spec and constitution; backend/pom.xml; frontend/package.json; existing RoomService/version patterns; GlobalExceptionHandler and Problem; frontend API/client.ts and App.tsx; PostgreSQL AbstractIntegrationTest.

## Existing architecture

**Decision:** Reuse Spring MVC/JPA/Flyway and React Router, API client, Vitest and PostgreSQL Testcontainers.
**Rationale:** Existing layers already support validation, persistence and HTTP 409 conflict handling.
**Alternatives considered:** New service, policy engine or UI framework; unnecessary for five predefined roles.

## Authentication boundary

**Decision:** Authentication and account creation remain a separate prerequisite, with an explicit handoff contract. This feature owns role assignments; the prerequisite owns verified principal identity and initial Admin provisioning.
**Rationale:** Follows the accepted clarification without weakening authorization.
**Alternatives considered:** Implicit Admin, browser-supplied identity, or implementing login here; incompatible with security or scope.
**Resolution:** Use an opaque user identifier and a small directory adapter. Bind to the delivered account key rather than guessing a physical account schema.

## Concurrent mutations

**Decision:** Lock one seeded guard row with JPA PESSIMISTIC_WRITE inside a short READ COMMITTED transaction before fresh reads of actor roles, target state/version and Admin count. Every role writer follows this order.
**Rationale:** Design inference: the common guard prevents two different Admin removals independently passing the count check. PostgreSQL holds row locks through transaction completion; Spring Data exposes repository lock metadata. Sources: [PostgreSQL locking](https://www.postgresql.org/docs/17/explicit-locking.html), [Spring Data JPA locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
**Alternatives considered:** Target-only versions miss global races; serializable transactions add retry handling; advisory locks are less visible in this JPA model.
**Tradeoff:** Administrative writes serialize. Do not hold the guard during external calls or user interaction.

## Stale edits

**Decision:** Dedicated rolesVersion, represented in JSON as decimal text; expectedVersion is mandatory on save. Compare before no-op detection. Increment on actual changes only.
**Rationale:** Detects stale selections and change-away/change-back; explicit version updates avoid relying on collection dirty-tracking. Decimal text avoids JavaScript integer precision loss.
**Alternatives considered:** Last-write-wins and automatic merge contradict clarification; HTTP preconditions add another pattern beyond existing version-based updates.

## Defaults and rollout

**Decision:** Viewer assignment and account creation commit together. Backfill only roleless accounts; preserve valid roles; block rollout on unknown legacy roles or absence of an initial Admin.
**Rationale:** Avoids observable roleless accounts and unauthorized promotion.
**Alternatives considered:** Lazy assignment at login leaves some accounts roleless; promoting a random user is unauthorized.

## UI and failures

**Decision:** Searchable paginated list, five checkboxes, Save/Cancel, explicit review-latest action on conflict. Preserve Problem fields with an additive role error code.
**Rationale:** Reuses current client conventions and distinguishes stale edits from last-Admin rejection.
**Alternatives considered:** Role hierarchy, custom roles and bulk editing exceed scope.

## Verification and logging

**Decision:** Existing tools; separate-connection PostgreSQL race tests with synchronization barriers. Role errors log only event/reason/status/correlation, excluding personal data and raw database errors.
**Rationale:** In-memory tests cannot prove serialization. Existing exception handlers log raw details, so role failures need sanitization before reaching these paths.
**Alternatives considered:** Audit-history UI and new telemetry infrastructure are not required.

All initial unknowns have design decisions. Authentication delivery remains an explicit prerequisite, not an unresolved feature clarification.

