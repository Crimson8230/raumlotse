# Implementation Plan: Room Search with Filters

**Branch**: `008-room-search-filter` | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-room-search-filter/spec.md`

## Summary

Signed-in users can search fully active rooms on the existing "Räume" page by person range, building, seating arrangement, equipment (any active catalog type), barrier-free reachability, and a date/time window. Opening a result from a time-window search pre-fills the booking form.

The backend adds a read-only `GET /api/rooms/search` endpoint and a small endpoint for the seating arrangement options:
- Candidate rooms (active room, floor, and building, optionally one building) come from one parameterized JPQL query.
- Occupied rooms come from one reservation query that uses the same `[start, end)` rule as bookings.
- The remaining filters run through a pure, unit-tested domain predicate. It encodes "one seating arrangement must satisfy layout and person range" and the barrier-free formula `(ground floor OR elevator) AND NOT room exclusion`.

Migration V9 adds `building.has_elevator`, `floor.ground_floor`, and `room.not_barrier_free`. These are exposed as optional, backward-compatible request fields and a derived `barrierFreeReachable` response field.

The frontend makes these changes:
- A `RoomSearchPanel` with URL-synchronized filters.
- Toggles for elevator and ground floor in `BuildingCatalog`.
- A not-barrier-free checkbox in `RoomForm`.
- A booking pre-fill in `RoomDetailPage`/`ReservationForm` driven by `?start=&end=`.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript ~6.0, strict (frontend)

**Primary Dependencies**:
- Backend: Spring Boot 4.1.1 (Web MVC, Data JPA/Hibernate, Security, Bean Validation), Flyway (triggered manually via `FlywayConfig`), Jackson 3.
- Frontend: React 19, React Router 7, Vite 8, zod 4, lucide-react.
- No new dependencies.

**Storage**: PostgreSQL 17. One additive migration `V9__add_barrier_free_attributes.sql`.

**Testing**:
- Backend: JUnit 5, Mockito, AssertJ, `@WebMvcTest`, Testcontainers (PostgreSQL 17).
- Frontend: Vitest 5 + React Testing Library, ESLint.

**Target Platform**: Linux containers via Docker Compose. Modern desktop and mobile browsers (layouts from 375px to 1280px, feature 002).

**Project Type**: Web application (Spring Boot backend + React SPA frontend)

**Performance Goals**: Search results within 2 s for ≤ 500 rooms in ≥ 95 % of searches (SC-003). Expected at most 2 SQL queries per search, plus batch fetches (≤ 15 statements for 500 rooms with `default_batch_fetch_size: 100`; guarded by the performance test).

**Constraints**:
- Every endpoint requires an authenticated session.
- Parameterized queries only.
- No PII in logs.
- Additive API changes only (existing clients unaffected).
- Barrier-free is never reported for rooms whose data is not maintained (defaults `false`).

**Scale/Scope**: One institution with up to ~500 rooms. The change touches about 12 backend files, about 12 frontend files, 1 migration, and new tests on both sides.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|---|---|---|
| **I. Test-First (NON-NEGOTIABLE)** | PASS | [research.md](research.md) R10 defines failing-first tests for every layer: domain predicate unit tests, service validation tests, `@WebMvcTest`, a Testcontainers integration test, and Vitest component tests. `tasks.md` must order each test before its implementation. |
| **II. Modern, Typed, Consistent** | PASS | Controller → service → repository layering, constructor injection, Bean Validation on the request record, strict TypeScript with functional components. No `any`. |
| **III. Contract-First API** | PASS | [contracts/room-search-api.yaml](contracts/room-search-api.yaml) defines the new endpoints and the additive fields before implementation. Existing endpoints stay compatible: new request fields are optional, and `null` means "unchanged" on PUT. `specs/001-room-management/contracts/openapi.yaml` will be amended with the additive fields. [contracts/ui.md](contracts/ui.md) fixes the URL contracts. |
| **IV. Secure & Data-Respecting** | PASS | The search is read-only behind the existing session auth. All input is validated (Bean Validation, service cross-field checks, client-side checks). JPQL uses bound parameters. Logs contain only counts and IDs, and never the filter free text. |
| **V. Simplicity & Observability** | PASS | No new library or service. In-memory predicate over pre-narrowed candidates instead of a dynamic Criteria API (R2 records the rejected alternative and the upgrade path). The existing `/api/health` is unchanged. Search failures go through the structured `GlobalExceptionHandler` logging. |
| Tech-stack constraints | PASS | Stays within Java 21 / Spring Boot / React / TS / Vite / PostgreSQL 17. |
| Workflow gates | PASS | Feature branch `008-room-search-filter` → PR into `dev`, with build, lint, and tests required. |

**Post-design re-check (after Phase 1)**: PASS. The design added two GET endpoints, three boolean columns, and one value object. There are no violations, so Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/008-room-search-filter/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── room-search-api.yaml   # new endpoints + additive schema changes
│   └── ui.md                  # search panel, URL state, pre-fill, catalog/form changes
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
backend/src/main/
├── resources/db/migration/
│   └── V9__add_barrier_free_attributes.sql          # NEW: 3 boolean columns, default false
└── java/at/mci/igp/raumlotse/
    ├── domain/
    │   ├── Building.java                             # + hasElevator
    │   ├── Floor.java                                # + groundFloor
    │   ├── Room.java                                 # + notBarrierFree, isBarrierFreeReachable()
    │   └── RoomSearchCriteria.java                   # NEW: value object + matches(room, occupiedIds)
    ├── dto/
    │   ├── BuildingRequest.java / BuildingResponse.java   # + hasElevator (optional in request)
    │   ├── FloorRequest.java / FloorResponse.java         # + groundFloor (optional in request)
    │   ├── RoomCreateRequest.java / RoomUpdateRequest.java # + notBarrierFree (optional)
    │   ├── RoomResponse.java                         # + notBarrierFree, barrierFreeReachable
    │   └── RoomSearchRequest.java                    # NEW: bound query params with Bean Validation
    ├── repository/
    │   ├── RoomRepository.java                       # + findSearchCandidates(buildingId)
    │   └── ReservationRepository.java                # + findOccupiedRoomIds(from, to)
    ├── service/
    │   ├── BuildingService.java / FloorService.java  # create/update with new flag ("null = unchanged")
    │   ├── RoomService.java                          # create/update notBarrierFree
    │   └── RoomSearchService.java                    # NEW: validation, candidates, occupied set, predicate, sort
    ├── controller/
    │   ├── BuildingController.java / FloorController.java  # pass new flag through
    │   └── RoomSearchController.java                 # NEW: GET /api/rooms/search, /search/seating-arrangements
    └── exception/GlobalExceptionHandler.java         # + 400 for type-mismatch/binding errors

backend/src/test/java/at/mci/igp/raumlotse/
├── domain/RoomSearchCriteriaTest.java                # NEW: predicate truth tables
├── service/RoomSearchServiceTest.java                # NEW: validation, sorting, occupied exclusion
├── controller/RoomSearchControllerTest.java          # NEW: binding, 400/401
├── RoomSearchIntegrationTest.java                    # NEW: Testcontainers end-to-end incl. [start,end) boundaries
├── RoomSearchPerformanceIntegrationTest.java         # NEW: SC-003 (500 rooms, p95 < 2 s, SQL statement-count guard)
└── (extended) BuildingControllerTest, FloorControllerTest, RoomControllerTest, RoomServiceTest, CatalogRepositoryIntegrationTest

frontend/src/
├── API/rooms.ts                                      # + searchRooms(criteria), listSeatingArrangementNames()
├── API/buildings.ts, API/floors.ts                   # + hasElevator / groundFloor in create/update
├── types/room.ts                                     # + new fields, RoomSearchCriteria type
├── components/RoomSearch/RoomSearchPanel.tsx (+ .css, .test.tsx)   # NEW
├── components/RoomSearch/roomSearchParams.ts (+ .test.ts)          # NEW: URL <-> criteria <-> API params, zod roomSearchSchema
├── pages/RoomListPage.tsx (+ test)                   # integrate panel, results, empty state, status interplay
├── pages/RoomDetailPage.tsx (+ test)                 # read ?start&end, open form, show barrier-free
├── components/ReservationForm/ReservationForm.tsx (+ test)   # initialStartTime/initialEndTime props
├── components/BuildingCatalog/BuildingCatalog.tsx (+ test)   # elevator / ground-floor toggles
└── components/RoomForm/RoomForm.tsx (+ test)         # "Nicht barrierefrei (Ausnahme)" checkbox

specs/001-room-management/contracts/openapi.yaml       # amended: additive fields (Constitution III)
README.md                                              # document new endpoints
```

**Structure Decision**: The existing web-application layout (`backend/` Spring Boot, `frontend/` React) is kept. Search logic gets its own controller and service rather than extending `RoomController`/`RoomService`, because its semantics (active-only, read-only, multi-source) differ from room management. Frontend search UI lives in a new `components/RoomSearch/` folder and is mounted on the existing `RoomListPage`.

## Implementation Order (for /speckit-tasks)

1. **Foundation**: migration V9, entity fields, DTO fields (additive), and "null = unchanged" semantics. Test first, via the existing controller and service tests.
2. **US1 (P1, MVP)**:
   - `RoomSearchCriteria` with person range and building.
   - `RoomSearchService` and `RoomSearchController` with validation.
   - Frontend panel with person and building filters, results, empty state, reset, and URL state.
3. **US2 (P2)**:
   - Seating arrangement name and equipment filters.
   - Seating arrangement options endpoint.
   - Equipment checkboxes.
4. **US3 (P2)**:
   - Barrier-free predicate and filter.
   - Amend `specs/001-room-management/contracts/openapi.yaml` with the additive fields alongside the DTO changes (Constitution III).
   - Catalog toggles for elevator and ground floor.
   - Room form exclusion checkbox.
   - Detail view marker.
5. **US4 (P3)**:
   - Occupied-rooms query and time-window filter.
   - Booking pre-fill via `?start&end`.
6. **Polish**: update the README, review logging, add the SC-003 performance test (500 rooms, p95 < 2 s, statement-count guard; Hibernate batch fetching is enabled only after that test fails), run all suites and the quickstart validation.

## Complexity Tracking

No constitution violations; nothing to justify.
