# Research: Room Search with Filters

**Feature**: `008-room-search-filter` | **Date**: 2026-09-26 | **Spec**: [spec.md](spec.md)

All Technical Context items were resolvable from the existing codebase and the clarified spec; no open NEEDS CLARIFICATION remain. Each decision below records the choice, why it was made, and what was rejected.

## R1 — Search endpoint shape

- **Decision**: Add a new read-only endpoint `GET /api/rooms/search` that takes all filters as optional query parameters and returns the same `RoomResponse` shape as `GET /api/rooms` (with two additive fields, see R6). The existing `GET /api/rooms?status=…` stays unchanged.
- **Rationale**: `GET /api/rooms` is the admin list with a `status` filter (active/deactivated/all) used by room management; the search has different semantics (only fully active rooms, FR-002). A separate path avoids a behavioral change on an existing endpoint (Constitution III: no breaking change without migration path) and keeps both easy to test. Query parameters make searches bookmarkable and cacheable GETs; the filter set is small and flat.
- **Alternatives considered**:
  - *Extend `GET /api/rooms` with filter parameters* — rejected: mixing "status=deactivated" with search filters creates contradictory combinations and silently changes an existing contract.
  - *`POST /api/rooms/search` with a JSON body* — rejected: not idempotent-by-convention, needs CSRF token for a read, and gains nothing for a flat filter set.

## R2 — Where the filtering happens

- **Decision**: Two-step evaluation in a new `RoomSearchService`:
  1. Load candidate rooms from the database: status `ACTIVE`, floor `ACTIVE`, building `ACTIVE`, optionally restricted to one building — a single parameterized JPQL query with fetch joins for floor and building (seating arrangements and equipment via batch fetching).
  2. If a time window is given, load the set of occupied room IDs with one parameterized query (R4).
  3. Apply the remaining criteria (person range, seating arrangement name, equipment, barrier-free, occupied set) with a pure domain predicate `RoomSearchCriteria.matches(room, occupiedRoomIds)`.
- **Rationale**: The "same arrangement must satisfy layout and person range" rule (FR-007) and the barrier-free formula (FR-009) are expressed most clearly and testably as plain Java — the predicate can be unit-tested exhaustively without a database (Constitution I, test-first). Spec scale is ≤ 500 rooms (SC-003), for which in-memory filtering of already-narrowed candidates is well within the 2-second target. All database access stays parameterized (Constitution IV).
- **Alternatives considered**:
  - *Spring Data JPA `Specification` / Criteria API with `EXISTS` subqueries* — rejected for now: more code and harder-to-read tests for the per-arrangement rule; justified only at much larger scale (noted as the upgrade path if SC-003 is ever missed).
  - *One large native SQL query* — rejected: string-assembled optional clauses risk violating the parameterized-query rule and are hard to test piecewise.
  - *Filtering entirely in the frontend from `GET /api/rooms`* — rejected: the availability filter needs reservation data the frontend does not have, and the contract would leak filtering rules into the client.

## R3 — Filter semantics (confirmed from spec)

| Filter | Query parameter | Semantics |
|---|---|---|
| Min persons | `minPersons` (int ≥ 1) | arrangement condition: `maxCapacity ≥ minPersons` |
| Max persons | `maxPersons` (int ≥ 1) | arrangement condition: `maxCapacity ≤ maxPersons` |
| Seating arrangement | `seatingArrangement` (string) | arrangement condition: name equals the value, trimmed, case-insensitive |
| **Arrangement rule** (FR-004, FR-007) | — | a room matches if **one single** seating arrangement satisfies **all** given arrangement conditions at once |
| Building | `buildingId` (uuid) | room's floor belongs to that building |
| Equipment | `equipmentTypeId` (uuid, repeatable) | room has **all** given equipment types assigned; an unknown or deactivated ID simply matches rooms that have it (none, if unknown) |
| Barrier-free | `barrierFree=true` | `(floor.groundFloor OR building.hasElevator) AND NOT room.notBarrierFree`; `false`/absent = no restriction |
| Time window | `from`, `to` (ISO-8601 instants) | excludes rooms with a `RESERVED`/`ACTIVE` reservation where `start < to AND end > from` |

All filters combine with AND (FR-010). Result order: building name, then room name, both case-insensitive.

## R4 — Availability check

- **Decision**: New repository query `ReservationRepository.findOccupiedRoomIds(from, to)` returning the distinct room IDs having a reservation in status `RESERVED` or `ACTIVE` overlapping `[from, to)`. It uses the same predicate as the existing `findConflictingReservations` (`start < :to AND end > :from`) and the existing index `ix_reservation_room_time_status`.
- **Rationale**: Reuses the reservation feature's half-open interval rule (FR-014), one query regardless of room count, no turnover buffer (currently zero in `ReservationService.calculateTurnoverBuffer`).
- **Alternatives considered**: Calling `findConflictingReservations` per room — rejected (N queries). Reusing the buffer calculation — deferred: the buffer is zero and depends on the chosen seating arrangement, which a search does not have.
- **Note**: Windows in the past are accepted by the search (spec edge case); the booking form rejects past starts on its own.

## R5 — Input validation and error responses

- **Decision**: Bind query parameters to a `RoomSearchRequest` record validated with Bean Validation (`@Min(1)` on person counts, `@Size(max = 100)` on the arrangement name). Cross-field rules — `minPersons ≤ maxPersons`, `from` and `to` both present or both absent, `to` after `from` — are checked in the service and raised as `IllegalArgumentException`, which the existing `GlobalExceptionHandler` maps to `400 Problem`. Add a handler in `GlobalExceptionHandler` for `MethodArgumentTypeMismatchException`/binding type errors (e.g., `minPersons=abc`, malformed UUID or instant) returning `400` with code `VALIDATION_FAILED`, since today only the user-role controllers handle that case. Because Spring ≥ 6.1 raises `MethodArgumentNotValidException` for `@ModelAttribute` binding (including type mismatches), the existing handler must also map `typeMismatch` field errors to `"has an invalid format"`, so Spring conversion texts never reach the client. On the frontend, the same rules are expressed as a zod schema (`roomSearchSchema`), following `auth/loginSchema.ts` (Constitution IV).
- **Rationale**: Matches the existing Problem-JSON error contract; SC-004 requires actionable messages for every invalid input.
- **Alternatives considered**: A class-level custom constraint annotation for cross-field checks — rejected as more machinery than three `if` statements (Constitution V).

## R6 — Barrier-free data (building, floor, room)

- **Decision**: Migration `V9__add_barrier_free_attributes.sql` adds `building.has_elevator`, `floor.ground_floor`, `room.not_barrier_free`, all `BOOLEAN NOT NULL DEFAULT false`. Request DTOs get optional (`Boolean`, nullable) fields; responses get the plain values plus a derived `RoomResponse.barrierFreeReachable`.
  - `BuildingRequest { name, hasElevator? }` — create: `null → false`; `PUT` (today "rename"): `null → unchanged`.
  - `FloorRequest { name, groundFloor? }` — same rule.
  - `RoomCreateRequest { …, notBarrierFree? }` — `null → false`; `RoomUpdateRequest { …, notBarrierFree? }` — `null → unchanged`.
- **Rationale**: Defaults of `false` guarantee the "never wrongly promise step-free" edge case. Optional request fields plus "null means unchanged" on updates keep every existing client and contract test valid (Constitution III — additive, non-breaking). The derived flag in the response keeps the formula in one place (backend) instead of duplicating it in the UI.
- **Alternatives considered**: A tri-state room override (inherit / force yes / force no) — rejected by stakeholder decision (exclusion only). Storing a computed `barrier_free` column — rejected: would go stale when a building's elevator flag changes (spec edge case).

## R7 — Seating arrangement options

- **Decision**: New endpoint `GET /api/rooms/search/seating-arrangements` returning the sorted, case-insensitively distinct arrangement names of all fully active rooms (first-seen spelling wins).
- **Rationale**: Seating arrangement names are free text per room (feature 001), so the UI needs a source for its dropdown (FR-006). A dedicated endpoint keeps this a documented contract (Constitution III).
- **Alternatives considered**: Deriving names in the frontend from the unfiltered search result — rejected: couples the option list to whatever result is currently displayed.

## R8 — Frontend placement and state

- **Decision**: Extend `RoomListPage` ("Räume") with a `RoomSearchPanel` component above the list. Filter state lives in the URL query string (`useSearchParams`), so browser back/forward, reload, and shared links restore the search. When the status selector is "Active" (default), the list shows search results; when an administrator switches to "Deactivated"/"All", the existing unfiltered management list is shown and the search panel is disabled with a hint that search covers active rooms only. Date + start/end time inputs are converted to ISO instants in the browser's time zone, exactly as `ReservationForm` already does.
- **Rationale**: Matches the spec assumption (search extends the existing "Räume" area, no new navigation item) and preserves admin management behavior. URL state is required for the booking pre-fill round trip anyway (R9).
- **Alternatives considered**: A separate `/rooms/search` page — rejected per spec assumption; component-local state — rejected because filters would be lost when returning from a room.

## R9 — Booking pre-fill from a search

- **Decision**: When the search includes a time window, each result links to `/rooms/{roomId}?start=<ISO>&end=<ISO>`. `RoomDetailPage` reads `start`/`end`; if both parse as valid instants and the room is active, it opens the booking form and passes them as `initialStartTime`/`initialEndTime` props to `ReservationForm`, which converts them to `datetime-local` values. Invalid or partial parameters are ignored (form behaves as before). Save-time validation is unchanged (FR-011a).
- **Rationale**: URL parameters are the simplest stateless hand-over, survive reload, and need no backend change.
- **Alternatives considered**: React Router navigation state — rejected: lost on reload and not linkable.

## R10 — Testing strategy (Constitution I)

- **Backend**: pure unit tests for `RoomSearchCriteria.matches` (every filter, FR-007 combination, barrier-free truth table); service tests with mocked repositories for validation rules; `@WebMvcTest` for `RoomSearchController` (parameter binding, 400 cases, 401 without session); Testcontainers integration test for end-to-end search including the occupied-rooms query and back-to-back boundaries; existing Building/Floor/Room controller and service tests extended for the new optional fields and "null = unchanged".
- **Frontend**: Vitest + Testing Library for `RoomSearchPanel` (validation, reset, URL sync), `RoomListPage` (results, empty state, status switch), `RoomDetailPage`/`ReservationForm` (pre-fill), `BuildingCatalog` (elevator / ground-floor toggles), `RoomForm` (not-barrier-free checkbox).
- Every test is written and observed failing before the implementation that satisfies it.
