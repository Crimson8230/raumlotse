# Research: Expire Unclaimed Room Reservations

**Feature**: `007-expire-unclaimed-reservations`
**Date**: 2026-09-23

## Research Topics & Findings

### Decision 1: Background Scheduling Mechanism

- **Context**: The system must automatically transition reservations in `RESERVED` status to `EXPIRED` after a 5-minute grace period has passed without check-in (SC-001 requires transition within 60 seconds).
- **Decision**: Implement a dedicated `ReservationExpirationScheduler` bean annotated with `@Component` containing a method annotated with `@Scheduled(fixedDelay = 30000)` (30 seconds) that invokes `ReservationService.expireUnattendedReservations()`.
- **Rationale**:
  - `RaumlotseApplication` already includes `@EnableScheduling`.
  - Using `fixedDelay = 30000` guarantees that consecutive executions are separated by 30 seconds and never overlap.
  - 30-second polling comfortably fulfills SC-001 (all unattended reservations expired within 60 seconds of deadline) while placing virtually zero load on the database.
  - Follows established project convention in `LoginAttemptCleanupService`.
- **Alternatives Considered**:
  - *Quartz Scheduler / Celery / External Queue*: Rejected per Constitution Principle V (YAGNI). Adding external scheduling infrastructure for a simple periodic query introduces unnecessary operational overhead.
  - *Database Triggers / pg_cron*: Rejected because business logic and audit logging should remain in Spring Boot service layer, and testing becomes dependent on external database extensions.
  - *On-Demand Mutation During GET Requests*: Rejected because HTTP GET requests must remain idempotent and side-effect-free (RFC 9110); executing database mutations inside read queries causes lock contention and violates architectural layering.

---

### Decision 2: Query Strategy and Database State Management

- **Context**: The background process must find all reservations where status is `RESERVED` and `startTime + 5 minutes <= now`.
- **Decision**: Add a derived query method in `ReservationRepository`:
  ```java
  List<Reservation> findByStatusAndStartTimeLessThanEqual(ReservationStatus status, Instant cutoffTime);
  ```
  Where `cutoffTime = clock.instant().minus(Duration.ofMinutes(5))`.
- **Rationale**:
  - Condition `startTime + 5m <= now` is mathematically equivalent to `startTime <= now - 5m`.
  - Spring Data JPA generates an index-friendly query: `WHERE status = ? AND start_time <= ?`.
  - The composite index on `reservation (room_id, status, start_time, end_time)` and status checks already exist in migration `V4__create_reservation_tables.sql`.
  - Database schema requires no Flyway migration changes because `status` already supports `'EXPIRED'` and terminal state checks are in place.
- **Alternatives Considered**:
  - *Bulk `UPDATE reservation SET status = 'EXPIRED' WHERE ...`*: Rejected because updating via entities ensures `@UpdateTimestamp` updates correctly and optimistic locking (`@Version`) is enforced.
  - *Loading all reservations into application memory*: Rejected for obvious scalability reasons.

---

### Decision 3: Time Abstraction and Clock Injection

- **Context**: Constitution Principle I mandates Test-First Development (TDD). Boundary conditions (e.g. 4 minutes 59 seconds vs. 5 minutes 00 seconds) must be deterministically testable without using brittle `Thread.sleep()`.
- **Decision**: Inject `java.time.Clock` into `ReservationService` (via constructor injection), backed by a `@Bean Clock clock() { return Clock.systemUTC(); }` in Spring configuration.
- **Rationale**:
  - Tests can supply `Clock.fixed(Instant, ZoneId)` or advance time deterministically to verify that reservations at 4m59s are kept as `RESERVED` and at 5m00s are transitioned to `EXPIRED`.
  - Adheres to Constitution Principle II (Modern, Typed, and Consistent Codebases) and Principle I (TDD).
- **Alternatives Considered**:
  - *Direct `Instant.now()` calls*: Rejected because it makes boundary condition testing non-deterministic and forces tests to sleep or rely on imprecise system timers.

---

### Decision 4: Concurrency & Race Condition Resolution

- **Context**: An attendee may click "Check-In / Activate" at the exact second the background expiration job is running.
- **Decision**: Rely on JPA optimistic locking (`@Version private Long version;` already present on `Reservation`).
- **Rationale**:
  - When either `activateReservation` or `expireUnattendedReservations` commits first, the second transaction detecting the modified version throws `OptimisticLockingFailureException`.
  - If check-in commits first: the reservation is now `ACTIVE`. The expiration job either never selects it or fails optimistic lock check and skips it.
  - If expiration commits first: the check-in attempt sees status `EXPIRED` (or throws optimistic lock failure), correctly reporting a conflict error to the user.
- **Alternatives Considered**:
  - *Pessimistic row locking (`SELECT FOR UPDATE`)*: Unnecessary given that concurrent activation/expiration on the exact same second is an infrequent edge case.

---

### Decision 5: Observability and Audit Logging

- **Context**: Constitution Principle V requires structured, greppable log output for operational events.
- **Decision**: Log each expiration batch and individual reservation transition via SLF4J:
  ```text
  Auto-expired unattended reservation id=<uuid> roomId=<uuid> startTime=<iso8601>
  ```
  And summary metric:
  ```text
  Unattended reservation expiration sweep completed: expired_count=<int>
  ```
- **Rationale**: Readily diagnosable via `docker compose logs backend` without exposing sensitive user information (Constitution Principle IV).
