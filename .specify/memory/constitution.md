<!--
Sync Impact Report
Version change: [none, initial draft] → 1.0.0
Modified principles: N/A (initial adoption; all placeholders replaced)
Added sections:
  - Core Principles: I. Test-First Development, II. Modern, Typed, and Consistent Codebases,
    III. Contract-First API Design, IV. Secure and Data-Respecting by Default,
    V. Simplicity and Observability
  - Technology Stack Constraints
  - Development Workflow & Quality Gates
  - Governance
Removed sections: none
Templates requiring follow-up: none checked automatically by this command; re-run
  /speckit-plan, /speckit-tasks and /speckit-checklist review after this amendment if
  in-flight artifacts reference the old (placeholder) constitution.
Deferred TODOs: none — ratification date set to the date this initial constitution was
  adopted since no prior document or date existed.
-->

# Raumlotse Constitution

## Core Principles

### I. Test-First Development (NON-NEGOTIABLE)
Every feature and bug fix MUST begin with a failing test — unit, integration, or component —
written before the implementation code that satisfies it. The Red-Green-Refactor cycle is
mandatory: write the test, watch it fail, implement the minimum code to pass, then refactor.
No production code may be merged without tests that a reviewer can see failed before the
change and pass after it.
Rationale: Room-suggestion logic and reservation workflows depend on correctness guarantees
that are cheapest to lock in before implementation, not after. TDD is the explicit governing
practice for this project and is treated as non-negotiable rather than aspirational.

### II. Modern, Typed, and Consistent Codebases
Backend code MUST follow Java 21 and Spring Boot idioms (constructor injection, Bean
Validation on inputs, a layered controller → service → repository structure). Frontend code
MUST use TypeScript in strict mode with functional React components and MUST pass ESLint;
any use of `any` or lint suppression requires an inline justification. Both stacks MUST stay
on actively maintained major versions of their frameworks.
Rationale: The system spans two languages and runtimes; consistent, typed code is what keeps
it maintainable as contributors move between the Spring Boot backend and the React frontend.

### III. Contract-First API Design
REST endpoints MUST have their request/response shapes and status codes defined before or
alongside implementation. The frontend MUST consume the backend only through these documented
contracts — no reaching into internal representations. A breaking change to an existing
endpoint MUST come with a version bump or a documented migration path for callers.
Rationale: Backend and frontend evolve somewhat independently; explicit contracts are what
prevents one side from silently breaking the other, especially once university systems
integrate against these endpoints.

### IV. Secure and Data-Respecting by Default
Any endpoint or form handling reservation or user data MUST validate input (Bean Validation
on the backend, schema validation on frontend forms) and MUST use parameterized queries —
never string-built SQL. Logs MUST NOT contain credentials or personal data. Docker Compose
and configuration files MUST NOT commit secrets; environment-specific credentials are
injected via environment variables or untracked secret files.
Rationale: Integration with university infrastructure means handling institutional and
personal data; protection has to be the default posture, not a later hardening pass.

### V. Simplicity and Observability
Prefer the simplest design that satisfies the current specification (YAGNI); a new
abstraction, library, or service boundary requires a stated reason in the PR description.
Every service exposes a health-check endpoint following the `/api/health` pattern already
established, and request failures MUST produce structured, greppable log output so issues
are diagnosable from `docker compose logs`.
Rationale: A young project accumulates complexity fastest right after its foundations are
laid; keeping designs simple and behavior observable now is cheaper than unwinding
over-engineering later.

## Technology Stack Constraints

- **Backend**: Java 21, Spring Boot, Spring Web MVC, Bean Validation, Maven (or the included
  Maven Wrapper).
- **Frontend**: React, TypeScript, Vite, React Router, ESLint.
- **Database**: PostgreSQL 17, with pgAdmin 4 for local inspection.
- **Infrastructure**: Docker and Docker Compose for local development and deployment parity.
- Adding a dependency or tool outside this stack requires either a constitution amendment
  (if it changes a principle above) or an explicit decision recorded in the introducing PR's
  description (if it's a compatible addition within an existing layer).

## Development Workflow & Quality Gates

- Work happens on `dev` or on feature branches merged into `dev`. Only `dev` may open pull
  requests into `main`; this is enforced by CI (`.github/workflows/validate-main-source.yml`)
  and MUST NOT be bypassed.
- Every pull request MUST include the tests written under Principle I and MUST pass CI
  (build, lint, and test suite) before merge.

## Governance

This constitution supersedes ad hoc practices, prior conventions, and any documentation
comment that conflicts with it. Where README.md or other docs describe *how* to run or build
the project, this constitution governs the *principles and values* that decide what good
changes look like; the two are complementary, not competing.

Amendments require: a documented rationale for the change, a version bump following semantic
versioning (MAJOR for backward-incompatible principle removals or redefinitions, MINOR for a
new principle or materially expanded guidance, PATCH for clarifications and wording), and an
updated Sync Impact Report at the top of this file describing the change. Every pull request
and code review MUST verify compliance with these principles — unjustified complexity or
skipped tests are blocking review feedback, not optional suggestions.

**Version**: 1.0.0 | **Ratified**: 2026-09-15 | **Last Amended**: 2026-09-15
