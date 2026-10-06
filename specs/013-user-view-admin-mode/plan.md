# Implementation Plan: User View, Administration Mode and Access Control

**Branch**: `013-user-view-admin-mode` | **Date**: 2026-10-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-user-view-admin-mode/spec.md`

## Summary

Three coordinated changes on the existing stack:

1. **Server-side authorization, deny by default.** The existing `RoleAccessFilter` (today an allow-list of two admin path groups) is inverted: every non-read request under `/api` requires ADMIN unless it is on a short, explicit list of user actions (create a reservation, reservation actions, device control, administration-mode query is separate). `/api/admin/**` stays admin-only for reads too. This closes the open master-data and maintenance-sweep endpoints (FR-013–FR-016) and makes future endpoints safe by default.
2. **Reservation ownership by identity.** Reservation reads and actions go through one ownership policy (owner by `created_by_user_id`, or ADMIN). Non-owners get `404`. The shared room schedule is redacted for non-owners. "My upcoming reservations" queries by user id instead of display name (fixes the current mismatch).
3. **User view and administration mode.** Administration mode is a flag in the server session, settable only by ADMINs via a new endpoint and returned with the roles lookup. The SPA shows administration options only when the effective flag is on; administration pages move under `/admin/*`, old addresses redirect.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript strict / React 19 (frontend)

**Primary Dependencies**: Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway; React Router 7, Zod, Vitest/Testing Library. No new dependencies.

**Storage**: PostgreSQL 17. No schema change required; one optional index (see data-model.md). Administration mode lives in the HTTP session (no table).

**Testing**: JUnit 5 + MockMvc + Testcontainers (backend), Vitest + Testing Library (frontend). Test-first per constitution I.

**Target Platform**: Linux server (Docker Compose), modern browsers.

**Project Type**: Web application (backend + frontend).

**Performance Goals**: No user-visible regression; the admin check stays one indexed lookup per non-read request (reads need no role lookup except `/api/admin/**`).

**Constraints**: Backward-compatible API additions only (new field, new endpoint); behavior changes for non-owners/non-admins are intentional and documented in contracts. Cache-Control `no-store` on role/mode responses as today.

**Scale/Scope**: ~16 controllers audited, 3 backend services touched, ~12 frontend files (routing, navigation, 4 pages with admin controls).

## Constitution Check

| Principle | Status | Notes |
|---|---|---|
| I. Test-First | PASS (process) | Tasks order a failing test before each change; authorization matrix test enumerates every mapped endpoint. |
| II. Typed/consistent code | PASS | Touched compact-style code (`RoleAccessFilter`) is reformatted to the surrounding project style while edited. |
| III. Contract-First | PASS | `contracts/access-control-api.yaml` defines new endpoint, new field, status codes, redaction. |
| IV. Secure by default | PASS | This feature is the hardening; refusals are logged without personal data (FR-026). |
| V. Simplicity | PASS | No new library; one filter inverted, one policy class, one session flag. Rejected heavier alternatives in research.md. |
| Workflow gate (CI) | NOTE | Constitution references `.github/workflows/validate-main-source.yml`, but the workflow currently sits in `workflows/` and has no build/test job. Not in scope; tracked in docs/SYSTEMDOKUMENTATION.md (P1). |

Post-design re-check: PASS, no violations; Complexity Tracking not needed.

## Project Structure

### Documentation (this feature)

```text
specs/013-user-view-admin-mode/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/access-control-api.yaml
└── tasks.md            # created by /speckit-tasks
```

### Source Code (affected paths)

```text
backend/src/main/java/at/mci/igp/raumlotse/
├── config/SecurityConfig.java                 # filter wiring, no new rules per path
├── service/RoleAccessFilter.java              # inverted: deny-by-default, allow-list of user actions
├── service/ReservationAccessPolicy.java       # NEW: owner-or-admin decision, redaction rule
├── service/ReservationService.java            # actor-aware reads/actions, my-upcoming by user id
├── service/AdminModeService.java              # NEW: read/write session flag, revocation handling
├── controller/ReservationController.java      # pass actor, drop duplicated identity fallback
├── controller/CurrentRolesController.java     # adds adminMode
├── controller/AdminModeController.java        # NEW: PUT /api/auth/admin-mode
├── repository/ReservationRepository.java      # findTop10ByCreatedByUserId…
└── dto/ReservationResponse.java               # ownedByMe, nullable personal fields
backend/src/test/java/…                        # authorization matrix, ownership, admin mode, my-upcoming

frontend/src/
├── App.tsx                                    # route split + redirects
├── auth/AdminModeProvider.tsx, useAdminMode.ts  # NEW (state from /api/auth/roles + PUT)
├── auth/RequireAdminMode.tsx                  # NEW (replaces RequireAdmin for /admin/*)
├── components/Navigation/                     # user items; admin items only when mode is on; mode switch + indicator
├── pages/{RoomListPage,RoomDetailPage,MapPage,LocationCatalogPage,…}  # hide/relocate admin controls
├── API/{userRoles,reservations}.ts, types/reservation.ts
```

**Structure Decision**: Existing web-application layout (backend + frontend); no new modules.

### Route plan

| Class | Address | Notes |
|---|---|---|
| User | `/`, `/rooms`, `/rooms/:id`, `/rooms/:id/display`, `/rooms/:id/control`, `/maps`, `/maps/:mapId` | `/maps*` read-only for everyone |
| Admin | `/admin/locations`, `/admin/rooms/new`, `/admin/rooms/:id/edit`, `/admin/maps`, `/admin/maps/:mapId`, `/admin/users`, `/admin/users/:id/roles` | require ADMIN and mode on |
| Redirect | `/locations`→`/admin/locations`, `/rooms/new`→`/admin/rooms/new`, `/rooms/:id/edit`→`/admin/rooms/:id/edit` | non-permitted users land on the "not available" notice |

## Complexity Tracking

No constitution violations to justify.
