# Phase 0 Research: Optional Booking Confirmation Email

## 1. Opt-In Propagation and Backward Compatibility

**Decision**: Add an optional boolean `emailNotification` property to the existing reservation-create request. The frontend validates the complete booking submission with a colocated Zod schema and always submits the controlled checkbox value after a successful parse; the backend treats an omitted property as `false`. Only `true` permits confirmation enqueueing.

**Rationale**: The choice belongs to one booking attempt and is needed at the existing transactional decision point. An optional default-false property preserves existing API callers and tests while making the browser behavior explicit. Runtime validation with the existing Zod dependency satisfies the frontend input-validation rule and prevents malformed form data from reaching the API; TypeScript continues to provide compile-time consistency.

**Alternatives considered**:

- Required boolean property: rejected because existing callers that omit the new property would break.
- Separate notification endpoint after booking: rejected because it introduces a race, allows notification requests detached from successful creation, and complicates duplicate prevention.
- Account-level preference: rejected because the specification requires a fresh choice for each booking.
- TypeScript typing without runtime schema validation: rejected because browser values can still be malformed at runtime and Constitution Principle IV explicitly requires schema validation for frontend forms.

## 2. Persistence of the User Choice

**Decision**: Do not add an `email_notification` column to `reservation` or `user_account`. For a committed reservation, an associated `booking_confirmation` row records that notification was requested; absence of a row means no confirmation was requested or the reservation was not successfully created.

**Rationale**: The boolean has no purpose after the enqueue decision. Persisting it separately would duplicate the existence semantics of the confirmation row and expand retention without supporting a specified user or operational workflow.

**Alternatives considered**:

- Store the boolean on every reservation: rejected as redundant state that can disagree with the queue relationship.
- Store a reusable account preference: rejected because it would violate default-off behavior for every new form.
- Store an explicit opt-out audit row: rejected because the feature does not require opt-out auditing and it would create unnecessary data.

## 3. Delivery Coupling and Durability

**Decision**: When `emailNotification=true`, persist a `booking_confirmation` queue row in the same transaction that creates the reservation, then deliver it outside the request through a scheduled worker. When false or omitted, commit only the reservation.

**Rationale**: Conditional insertion makes reservation creation and an opted-in notification intent atomic. A direct SMTP call would make booking latency and success depend on an external mail server, while an in-memory after-commit event could be lost and would not provide the persisted outcome required by FR-014.

**Alternatives considered**:

- Synchronous SMTP before commit: rejected because SMTP failure could roll back a valid booking and the database could still roll back after the external send.
- `@TransactionalEventListener(AFTER_COMMIT)` only: rejected because it is process-local and does not preserve pending work or status across restart.
- External message broker: rejected as unnecessary infrastructure for the current project scale.

**Reference**: [Spring transaction-bound events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)

## 4. Duplicate Policy and State Machine

**Decision**: Enforce at most one confirmation row per opted-in reservation with a unique constraint and permit one SMTP submission attempt. Use `PENDING → PROCESSING → SENT|FAILED`; all terminal states are immutable.

**Rationale**: SMTP does not provide a portable idempotency key or atomic commit with PostgreSQL. Automatic retries after an ambiguous network outcome could produce duplicate emails and violate FR-013/SC-006. The worker commits `PROCESSING` before contacting SMTP. A crash or timeout leaves the row in `PROCESSING`; stale rows become `FAILED` and are not re-sent automatically.

**Alternatives considered**:

- At-least-once retry with exponential backoff: rejected because duplicate delivery is possible after an ambiguous SMTP acknowledgement.
- Mark sent before SMTP: rejected because ordinary send failures would be falsely reported as successful.
- No persisted status: rejected because support could not distinguish pending, successful, and failed requested delivery.

## 5. Mail Integration and Message Format

**Decision**: Add `spring-boot-starter-mail`, implement the gateway with `JavaMailSender`, and send a UTF-8 German plain-text message. Configure finite SMTP connection, read, and write timeouts.

**Rationale**: This is the native Spring Boot mail integration and is auto-configured from `spring.mail` settings. Plain text is sufficient for room/start/end content, avoids HTML injection and rendering differences, and keeps the feature small. Finite timeouts prevent mail operations from blocking indefinitely.

**Alternatives considered**:

- Hand-written Jakarta Mail session setup: rejected because it duplicates Spring Boot configuration and lifecycle support.
- HTML templating engine: rejected because the specification requires only a simple confirmation and no branded layout.
- Third-party email HTTP API: rejected because it adds vendor coupling and a second contract not requested by the feature.

**Reference**: [Spring Boot — Sending Email](https://docs.spring.io/spring-boot/reference/io/email.html)

## 6. Queue Processing

**Decision**: Reuse Spring scheduling with a fixed delay and claim a bounded number of rows per sweep using PostgreSQL row locking with `FOR UPDATE SKIP LOCKED`. Claim and finalization execute in short, separate transactions; SMTP runs outside a database transaction.

**Rationale**: Scheduling is already enabled in `RaumlotseApplication`. A short fixed delay supports SC-003 without coupling the web request to delivery. Row-level skip-locked claiming prevents two application instances from processing the same requested confirmation while avoiding a database lock during network I/O.

**Alternatives considered**:

- `@Async` from the request thread: rejected because work is not durable and can disappear on restart.
- Holding a pessimistic lock during SMTP: rejected because an unresponsive server would retain a database transaction and lock.
- One worker thread per email: rejected as unnecessary and unbounded.

**Reference**: [Spring Framework — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)

## 7. Data Minimization and Operational Logging

**Decision**: Store only the reservation reference, lifecycle timestamps, attempt count, and a fixed failure code. Resolve the recipient through `reservation.createdByUserId → user_account.id` immediately before sending; do not persist a second email copy, rendered body, or the transient opt-in boolean.

**Rationale**: The reservation identifies the authenticated creator, the account owns the email address, and the confirmation row itself proves opt-in. Logs contain only fixed event names, confirmation/reservation UUIDs, status, and a bounded failure code. Raw SMTP messages and exception text are excluded because they can contain addresses or provider details.

**Alternatives considered**:

- Snapshot recipient email and body in the queue: rejected due to duplicated personal data and retention burden.
- Log the SMTP exception stack/message: rejected because provider exceptions may embed addresses or message content.
- No operational log: rejected because FR-014 and Constitution V require diagnosable failures.

## 8. Time and Content Rules

**Decision**: Convert stored `Instant` values to `Europe/Berlin` when rendering and include date, local time, and the literal zone identifier for both start and end. The subject is `Raumlotse: Buchung bestätigt – {roomName}`; the body includes only confirmation wording, room, start, and end.

**Rationale**: Storing instants remains unchanged, while `ZoneId` conversion handles daylight-saving boundaries correctly. Showing the zone identifier removes ambiguity. Omitting notes, attendee details, and `reservedFor` minimizes data beyond the requested content.

**Alternatives considered**:

- UTC-only email: rejected because the specification adopts the local application zone.
- Numeric offset without zone name: rejected because it is less understandable and changes around daylight-saving boundaries.
- Locale-dependent system default zone: rejected because deployments may use different host settings.

## 9. Local End-to-End SMTP Validation

**Decision**: Add Mailpit to local Docker Compose, wire the backend to `mailpit:1025`, and expose its UI on port `8025`. Pin the image to a reviewed release or digest during implementation. Production uses deployment-provided SMTP settings and does not run Mailpit.

**Rationale**: Mailpit captures real SMTP messages and makes recipient, subject, encoding, and body visible without sending external mail. It also makes the opted-in versus opted-out behavior directly observable.

**Alternatives considered**:

- Use a real institutional mailbox for development: rejected due to credentials, privacy, and nondeterminism.
- Automated tests only: rejected for final acceptance because browser-to-request-to-SMTP behavior needs one wire-level validation path.

**Reference**: [Mailpit project documentation](https://github.com/axllent/mailpit)

## 10. Public Interface Scope

**Decision**: Extend `POST /api/rooms/{roomId}/reservations` with optional `emailNotification: boolean = false`. Keep the `201` response and all error responses unchanged. A successful commit creates a pending confirmation only when the property is true. Do not expose notification status in a new API or UI.

**Rationale**: The booking form must communicate the per-booking choice to the existing authenticated creation operation. An optional false-default field is backward-compatible and keeps reservation plus notification intent in one transaction. Persisted status and privacy-safe logs satisfy support needs without a new authorization surface.

The legacy direct-service overload used by tests/setup defaults to opt-out unless a caller deliberately supplies an opted-in request through the authenticated path. Reads and lifecycle operations never enqueue.

**Alternatives considered**:

- Add confirmation status to `ReservationResponse`: rejected because the asynchronous status is not needed to complete booking.
- Add a dedicated notification endpoint: rejected because it separates intent from reservation commit and expands authorization and duplicate-delivery concerns.
- Infer opt-in from the presence of an email address: rejected because it ignores the user's explicit checkbox choice.
