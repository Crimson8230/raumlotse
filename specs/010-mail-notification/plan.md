# Implementation Plan: Optional Booking Confirmation Email

**Branch**: `010-mail-notification` | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-mail-notification/spec.md`

## Summary

Add a default-off `Email Notification` checkbox to the existing reservation form and carry its boolean value through the existing typed reservation-create request. A successful authenticated booking creates a durable booking-confirmation record in the same database transaction only when the submitted value is `true`; `false` or an omitted value creates the reservation without a confirmation. A scheduled backend worker claims requested confirmations, resolves the booking user's current account email, formats a German plain-text message in `Europe/Berlin`, and submits it through SMTP. Delivery failures remain isolated from reservation state, and no notification preference is persisted on the reservation or user account.

## Technical Context

**Language/Version**: Java 21 backend; TypeScript in strict mode with React frontend

**Primary Dependencies**: Spring Boot 4.1.1, Spring Web MVC, Spring Data JPA, Bean Validation, Spring Scheduling, `spring-boot-starter-mail` / `JavaMailSender`, Flyway, PostgreSQL JDBC; React, Vite, Zod, React Testing Library, Vitest; Mailpit for local-only SMTP capture

**Storage**: PostgreSQL 17; Flyway migration `V12__create_booking_confirmation.sql` adds the durable confirmation queue and status history. The request-time opt-in is represented by presence or absence of a confirmation row and adds no reservation or account column.

**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot integration tests, MockMvc, Testcontainers PostgreSQL; Vitest and React Testing Library for the Zod reservation schema, default-off checkbox, submitted payload behavior, and prevention of API calls for invalid form data; mail gateway fakes for deterministic automated tests and Mailpit for manual end-to-end validation

**Target Platform**: Browser-based React client and Linux server container through Docker Compose; SMTP service supplied by the deployment environment

**Project Type**: Full-stack web application with a small booking-form and request-contract change plus backend asynchronous mail delivery and local SMTP infrastructure

**Performance Goals**: At least 95% of confirmations requested by 100 successful opted-in bookings are accepted by the configured SMTP service within two minutes with at most 20 concurrent booking requests; queue polling begins at most one second after the preceding poll completes; each SMTP operation has finite connection, read, and write timeouts; opted-out booking response time and behavior remain independent of SMTP

**Constraints**: Checkbox is visible before submit, controlled, and default `false` for every new form; the complete reservation payload is validated with Zod before the API call, `emailNotification` must be boolean, and seating/equipment identifiers must satisfy the UUID formats documented in the API contract; missing request field is treated as `false` for backward compatibility; only an opted-in successful authenticated create may enqueue; one logical confirmation and at most one SMTP submission attempt per opted-in reservation; no confirmation for opted-out, rolled-back, or rejected bookings; mail failure never changes reservation state; no email address, message body, booking times, or SMTP exception text in logs; German plain text only; display times use `Europe/Berlin`; no edit/cancel/lifecycle emails

**Scale/Scope**: One confirmation row per successful opted-in reservation, zero rows for opted-out reservations, bounded queue batches, one new Flyway migration, one new mail dependency, backend DTO/domain/repository/service/configuration changes, one frontend form/type/schema change using the existing Zod dependency, Docker Compose Mailpit service, and no new public endpoint or notification-status UI

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

- **I. Test-First Development — PASS**: Work begins with the failing migration-sequence test T001 before dependencies, configuration, migrations, or production code change. Foundational tests T004–T006 are then written and observed failing before T007–T010. User Story 1 tests T011–T015, including frontend schema and component tests, are written and observed failing before T016–T023. User Story 2 follows the same Red-Green-Refactor sequence with T024–T028.
- **II. Modern, Typed, and Consistent Codebases — PASS**: The request flag is added to the strict TypeScript payload and Java request record, the form remains a functional controlled React component, and backend work follows constructor injection and existing controller → service → repository layering. Frontend lint and build gates remain mandatory.
- **III. Contract-First API Design — PASS**: [contracts/reservation-confirmation.yaml](contracts/reservation-confirmation.yaml) defines the optional `emailNotification` request property with default `false`, unchanged responses, and the conditional asynchronous side effect before implementation. Existing callers that omit the property remain compatible.
- **IV. Secure and Data-Respecting by Default — PASS**: The complete frontend reservation payload is runtime-validated with the existing Zod dependency before submission, including a strict boolean `emailNotification` and contract-matching UUID validation for seating/equipment identifiers; the backend retains Bean Validation and typed deserialization. Recipient email is resolved from the authenticated reservation owner and never accepted from the client. The queue stores references and status metadata, not a duplicate address or body. SMTP credentials remain environment-backed and logs contain no personal data or raw exception messages.
- **V. Simplicity and Observability — PASS**: A controlled checkbox and one optional request property reuse the existing form and endpoint. Absence of a confirmation row represents opt-out, avoiding a redundant reservation preference column. The existing scheduler and PostgreSQL are reused; fixed delivery outcomes and persisted statuses remain greppable and `/api/health` remains available.
- **Post-design re-check — PASS**: The data model makes `Reservation → BookingConfirmation` optional, the contract defaults omitted requests to opt-out, and the validation guide covers both branches end to end. The durable queue remains the smallest design that provides commit-coupled opt-in delivery, failure isolation, uniqueness, and operational status.

## Project Structure

### Documentation (this feature)

```text
specs/010-mail-notification/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── reservation-confirmation.yaml
├── checklists/
│   └── requirements.md
└── tasks.md                         # Regenerated after this plan
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── src/main/java/at/mci/igp/raumlotse/
│   ├── config/BookingConfirmationProperties.java
│   ├── domain/
│   │   ├── BookingConfirmation.java
│   │   └── BookingConfirmationStatus.java
│   ├── dto/ReservationCreateRequest.java            # Optional emailNotification=false request flag
│   ├── repository/BookingConfirmationRepository.java
│   └── service/
│       ├── ReservationService.java                  # Conditional transactional enqueue
│       ├── BookingConfirmationQueueService.java
│       ├── BookingConfirmationWorker.java
│       ├── BookingConfirmationMailGateway.java
│       └── SmtpBookingConfirmationMailGateway.java
├── src/main/resources/
│   ├── application.yaml
│   └── db/migration/V12__create_booking_confirmation.sql
└── src/test/java/at/mci/igp/raumlotse/
    ├── BookingConfirmationIntegrationTest.java
    ├── controller/ReservationControllerTest.java
    ├── config/MailConfigurationPrivacyTest.java
    ├── repository/BookingConfirmationRepositoryTest.java
    └── service/
        ├── BookingConfirmationQueueServiceTest.java
        ├── BookingConfirmationWorkerTest.java
        └── SmtpBookingConfirmationMailGatewayTest.java

frontend/src/
├── components/ReservationForm/
│   ├── ReservationForm.tsx                         # Schema-validated controlled form
│   ├── ReservationForm.css
│   ├── ReservationForm.test.tsx                    # UI and API-call tests
│   ├── reservationFormSchema.ts                    # Zod runtime validation
│   └── reservationFormSchema.test.ts               # Valid/invalid payload tests
└── types/reservation.ts                            # Typed emailNotification boolean

docker-compose.yml                                  # Local Mailpit and backend SMTP wiring
.env.example                                        # Non-secret SMTP placeholders
README.md                                           # Checkbox behavior, Mailpit, production SMTP variables
```

**Structure Decision**: Extend the established full-stack reservation path instead of adding an endpoint. `ReservationForm` owns a local default-false checkbox state, validates the complete submission through a colocated Zod schema, and sends only a successful parse through the existing typed create payload. `ReservationCreateRequest` supplies the server-side false default, and `ReservationService` conditionally enqueues in the same transaction that saves the reservation. Persistence stays under `domain`/`repository`, SMTP stays behind a service-layer gateway, and no opt-in value is retained after the create decision.

## Complexity Tracking

No constitution violations require exceptions. The optional request field is backward-compatible and avoids a separate notification endpoint or preference model. The mail gateway is a deliberate boundary around an external SMTP side effect, enabling deterministic tests. The durable database queue is required by FR-013 and FR-014 and avoids introducing a message broker.
