---

description: "Task list for feature 017 – Ranked Room Search Results"
---

# Tasks: Ranked Room Search Results

**Input**: Design documents from `/specs/017-room-search-ranking/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/room-search-ranking-api.yaml, contracts/ui.md, quickstart.md

**Tests**: REQUIRED. Constitution Principle I (Test-First, non-negotiable): every test task below MUST be written and seen failing before the implementation task that follows it.

**Organization**: Tasks are grouped by user story. Ranking is one comparator (`RoomRanking`) whose keys are always evaluated in the fixed order *favorite → size band → specialised surplus → popularity → building/room name* (FR-002); each story fills in its own key, so stories can land in any order without reordering keys.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story from spec.md (US1–US4)

## Path Conventions

- Backend main: `backend/src/main/java/at/mci/igp/raumlotse/` (abbreviated `BE/`)
- Backend tests: `backend/src/test/java/at/mci/igp/raumlotse/` (abbreviated `BT/`)
- Migrations: `backend/src/main/resources/db/migration/`
- Frontend: `frontend/src/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Schema changes used by several stories.

- [X] T001 Add a test to `BT/MigrationVersionTest.java` asserting `containsSubsequence("V16__create_role_permissions.sql", "V17__room_search_ranking.sql")` (same style as the existing V11→V12 and V13→V14 tests); run it and see it fail.
- [X] T002 Create `backend/src/main/resources/db/migration/V17__room_search_ranking.sql`: (a) `ALTER TABLE equipment_type ADD COLUMN specialised BOOLEAN NOT NULL DEFAULT false;` ("Existing rows become `false` (general)"); (b) `CREATE TABLE room_favorite (user_id UUID NOT NULL REFERENCES user_account (id) ON DELETE CASCADE, room_id UUID NOT NULL REFERENCES room (id) ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (user_id, room_id));` plus `CREATE INDEX ix_room_favorite_room_id ON room_favorite (room_id);`. No index on reservation is needed (existing `ix_reservation_room_time_status` serves popularity, research R4). Re-run T001 → green.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Replace the fixed alphabetical sort by a ranking comparator and pass the current user and a clock into the search, without changing observable order yet.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Create `BT/domain/RoomRankingTest.java` (plain JUnit, no Spring): build `Room`/`Floor`/`Building`/`SeatingArrangement`/`EquipmentType` objects in memory like `BT/domain/RoomSearchCriteriaTest.java` does; first test: with empty criteria, no favorites and empty popularity, rooms are ordered by building name then room name, case-insensitive (the previous `BY_BUILDING_THEN_NAME` behavior). Second test: two rooms with identical building and room name, ids set via `ReflectionTestUtils.setField(room, "id", …)` as in `BT/domain/RoomSearchCriteriaTest.java`, are always returned in room-id order, independent of input order (SC-001). Third test: rooms without an id (unsaved) sort without exception when favorites and popularity are `Set.of()`/`Map.of()` — they count as non-favorite with popularity 0, and among otherwise equal rooms a missing id sorts last. See it fail (class missing).
- [X] T004 Create `BE/domain/RoomRanking.java`: `public final class RoomRanking implements Comparator<Room>` with constructor `RoomRanking(RoomSearchCriteria criteria, Set<UUID> favoriteRoomIds, Map<UUID, Long> popularityByRoomId)` (null-safe → empty). `compare` chains, in this exact order, private key methods `favoriteKey`, `sizeBandKey`, `bandSurplusKey`, `specialisedSurplusKey`, `popularityKey` (each returning a constant `0` for now, to be implemented by the stories) and finally building name, then room name with `String.CASE_INSENSITIVE_ORDER`, then `Room::getId` with `Comparator.nullsLast(Comparator.naturalOrder())` (room names are not unique, SC-001). Null-safety: immutable `Set.of()`/`Map.of()` throw on `contains(null)`/`get(null)`, so `favoriteKey` and `popularityKey` MUST check `room.getId() == null` first (same trap as documented in `RoomSearchCriteria#isOccupied`). Add a Javadoc listing the FR-002 order. T003 → green.
- [X] T005 Update the existing tests in `BT/controller/RoomSearchControllerTest.java` for the new controller call: replace every `when(roomSearchService.search(any()))` / `verify(roomSearchService).search(captor.capture())` / `verify(roomSearchService, never()).search(any())` with the two-argument form `search(any(), any())` / `search(captor.capture(), any())` / `search(any(), any())`, and send the requests as an authenticated `AuthenticatedUser` principal (e.g. `.with(authentication(...))` as in `BT/controller/ReservationControllerTest.java`); add a test that the controller passes the principal's `userId` as second argument. Then extend `BT/controller/RoomSearchControllerTest.java` and `BT/RoomSearchIntegrationTest.java` with a test that `GET /api/rooms/search` as an authenticated user still returns 200 and the alphabetical order when no ranking data exists; extend the existing unit test `BT/service/RoomSearchServiceTest.java` (switch its manual constructor call to the new constructor; Mockito mocks for `RoomRepository` and `ReservationRepository`, fixed `Clock`) asserting `search(criteria)` and `search(criteria, userId)` both return ranked results.
- [X] T006 Update `BE/service/RoomSearchService.java`: inject `java.time.Clock` (bean from `BE/config/ClockConfig.java`, as in `ReservationService`); remove `BY_BUILDING_THEN_NAME`; add `public List<RoomResponse> search(RoomSearchCriteria criteria, UUID userId)` that filters exactly as before, then sorts with `new RoomRanking(criteria, Set.of(), Map.of())`; keep `search(RoomSearchCriteria)` delegating with `userId = null` (public signature kept, research R7). Keep the log line `room_search result_count={}`.
- [X] T007 Update `BE/controller/RoomSearchController.java`: add an `Authentication authentication` parameter to `search`, resolve the `AuthenticatedUser` principal the same way `BE/controller/ReservationController.java#resolveUser` does, and call `search(criteria, user.userId())`. T005 → green.

**Checkpoint**: Search behaves exactly as before, but through `RoomRanking`; stories can now fill in keys.

---

## Phase 3: User Story 1 – Smallest fitting room first (Priority: P1) 🎯 MVP

**Goal**: Rooms whose capacity is close to the group size (`minPersons`) come first, using size bands.

**Independent Test**: Rooms seating 6, 30, 100; search `minPersons=4` → order 6, 30, 100. Rooms 21 and 24 with `minPersons=20` count as equal on size.

### Tests for User Story 1 ⚠️ (write first, see them fail)

- [X] T008 [P] [US1] Add to `BT/domain/RoomRankingTest.java` one test per US1 acceptance scenario: (1) capacities 6/30/100, group 4 → A, B, C; (2) D with "Theater" 80 + "Workshop" 10 vs E "Theater" 20, group 8, no layout → D before E; (3) same with layout "Theater" → E before D; (4) group 20, F 21 vs G 24 → equal on size (name decides when nothing else differs); (5) group 20, H 45 vs J 90 → H first (band 4, exact surplus); (6) no `minPersons` → size ignored. Plus boundary tests: group 20, 25 seats → band 1, 26 seats → band 2; 30 seats → band 2, 31 → band 3; 40 → band 3, 41 → band 4. Also: with `maxPersons` only, size is ignored.
- [X] T009 [P] [US1] Add to `BT/RoomSearchIntegrationTest.java` an end-to-end test: three rooms (6/30/100 seats) in one fresh building, `GET /api/rooms/search?minPersons=4&buildingId=…` → response ids in order 6, 30, 100.

### Implementation for User Story 1

- [X] T010 [US1] In `BE/domain/RoomRanking.java` implement size keys per data-model.md: `group = criteria.minPersons()`; if null → both keys 0. Relevant capacity = smallest `maxCapacity` among the room's arrangements that satisfy min persons, max persons and (if set) layout name `equalsIgnoreCase` — reuse the same predicate as `RoomSearchCriteria#matchesArrangementRule` by extracting it into a package-visible method `boolean arrangementMatches(SeatingArrangement a)` on `BE/domain/RoomSearchCriteria.java` (keep `matches` behavior unchanged; `BT/domain/RoomSearchCriteriaTest.java` must stay green). `s = capacity − group`; band 1 if `4*s <= group`, 2 if `2*s <= group`, 3 if `s <= group`, else 4 (integer arithmetic, research R2). `bandSurplusKey` = `s` if band 4 else 0. Compute keys once per room (cache in a `Map<Room, …>` or precompute in the service) so sorting 500 rooms stays cheap. T008, T009 → green.
- [X] T011 [P] [US1] Write a failing test in `frontend/src/pages/RoomListPage.test.tsx`: when the search returns ≥ 1 room, the text "Sortiert nach Eignung" is visible above the list; it is absent in the empty state; the list renders rooms in the exact order returned by the API (no client re-sort).
- [X] T012 [US1] In `frontend/src/pages/RoomListPage.tsx` render the note "Sortiert nach Eignung" above the search result list when results exist (FR-012), and remove any client-side sorting of search results if present. T011 → green.

**Checkpoint**: MVP – resource-friendly ordering by group size works end-to-end.

---

## Phase 4: User Story 2 – Pinned favorite rooms (Priority: P2)

**Goal**: Users mark up to 10 rooms as personal favorites; matching favorites are pinned on top; "Nur Favoriten" filter with "Nicht verfügbar" section.

**Independent Test**: Mark a large room as favorite → it is first in a matching search, absent in a non-matching one, not pinned for another user; 11th mark refused.

### Tests for User Story 2 ⚠️ (write first, see them fail)

- [X] T013 [P] [US2] Add to `BT/domain/RoomRankingTest.java`: favorites first regardless of size band (US2 scenario 2: C 100 seats favorite, A 6, B 30, group 4 → C, A, B); several favorites are ordered among themselves by the remaining keys (scenario 4).
- [X] T014 [P] [US2] Create `BT/RoomFavoriteIntegrationTest.java` (extends `AbstractIntegrationTest`, users via `TestActors.registered(jdbc, …)`): `PUT /api/me/favorite-rooms/{id}` → 204 and appears in `GET /api/me/favorite-rooms` as `{roomId, roomName, buildingName, floorName, available: true}`, sorted by building then room name; second `PUT` same room → 204, still one entry; `DELETE` → 204, gone; `DELETE` of non-favorite / unknown id → 204; `PUT` on deactivated room or room in deactivated floor/building → 404 code `ROOM_NOT_FOUND`; `PUT` on unknown id → 404; after deactivating a favorite room, `GET` returns it with `available: false`; user Y never sees X's favorites; with 10 favorites, `PUT` of an 11th → 409 code `FAVORITE_LIMIT_REACHED`, re-`PUT` of an existing favorite → 204; concurrency: user with 9 favorites, two `PUT`s for different rooms started in parallel (two threads, `CountDownLatch`) → exactly one 204 and one 409, and the DB holds 10 rows; unauthenticated → 401; missing CSRF on PUT/DELETE → 403.
- [X] T015 [P] [US2] Add rows to `BT/security/AuthorizationMatrixTest.java` (and `BT/security/ProtectedBusinessRouteTest.java` if it enumerates routes): `GET /api/me/favorite-rooms`, `PUT` and `DELETE /api/me/favorite-rooms/{id}` require permission `READ`; a role without `READ` gets 403.
- [X] T016 [P] [US2] Add to `BT/RoomSearchIntegrationTest.java`: X marks C (100 seats); `GET /api/rooms/search?minPersons=4` as X → C first; same search as Y → C in its size position; X search requiring an equipment C lacks → C absent; `favoritesOnly=true` as X → only favorites, ranked; `favoritesOnly=true` combined with another filter → only favorites matching it; `favoritesOnly=true` with no favorites → empty list; deactivated favorite never in search results.

### Implementation for User Story 2 – backend

- [X] T017 [P] [US2] Create `BE/domain/RoomFavorite.java` JPA entity for table `room_favorite` with an `@Embeddable` id class `RoomFavoriteId(UUID userId, UUID roomId)` (`@EmbeddedId`, equals/hashCode), field `createdAt` (`Instant`, set in constructor from a passed `Instant`). Constraints from data-model.md: "Primary key `(user_id, room_id)` → at most one mark per user and room"; "At most **10** rows per `user_id` … including rows for unavailable rooms".
- [X] T018 [P] [US2] Create `BE/repository/RoomFavoriteRepository.java` (`JpaRepository<RoomFavorite, RoomFavoriteId>`) with: `Set<UUID>`-returning JPQL `findRoomIdsByUserId(UUID userId)`, `long countByIdUserId(UUID userId)`, `boolean existsById(RoomFavoriteId id)`, `void deleteById(RoomFavoriteId id)`, and a JPQL query returning favorites joined with room, floor, building for the list endpoint (all parameterized, Constitution IV).
- [X] T019 [P] [US2] Add to `BE/repository/UserAccountRepository.java` a method `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from UserAccount u where u.id = :id") Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id)` (research R5a).
- [X] T020 [P] [US2] Extend `BE/exception/ConflictException.java` with an optional `code` (constructor `ConflictException(String message, String code)` + `getCode()`, mirroring `NotFoundException`) and update `handleConflict` in `BE/exception/GlobalExceptionHandler.java` to use `Problem.of(409, "Conflict", message, code)` when a code is set; existing `ConflictException(String)` callers unchanged.
- [X] T021 [P] [US2] Create `BE/dto/FavoriteRoomResponse.java` record `(UUID roomId, String roomName, String buildingName, String floorName, boolean available)`; `available` = room, floor and building all `EntityStatus.ACTIVE`.
- [X] T022 [US2] Create `BE/service/RoomFavoriteService.java` (`@Service`, constructor injection, `Clock`): `List<FavoriteRoomResponse> list(UUID userId)` sorted by building name, then room name (case-insensitive); `Set<UUID> favoriteRoomIds(UUID userId)`; `@Transactional void mark(UUID userId, UUID roomId)`: load room, throw `NotFoundException("Raum nicht gefunden", "ROOM_NOT_FOUND")` unless room, floor and building are ACTIVE; if already a favorite → return; lock user row via `findByIdForUpdate`; if `countByIdUserId >= 10` throw `ConflictException("Höchstens 10 Favoriten – entferne zuerst einen anderen.", "FAVORITE_LIMIT_REACHED")`; else save. `@Transactional void unmark(UUID userId, UUID roomId)`: delete if present, never fails. Log `room_favorite_marked count={}` / `room_favorite_limit_reached` without user data (Constitution IV/V). Make the limit a `static final int MAX_FAVORITES = 10`.
- [X] T023 [US2] Create `BE/controller/RoomFavoriteController.java` mapped to `/api/me/favorite-rooms`: `GET` → `list`, `PUT /{roomId}` → 204, `DELETE /{roomId}` → 204; user id only from the `Authentication` principal (same resolution as T007).
- [X] T024 [US2] Add to `BE/service/RoleAccessFilter.java` rules: `GET /api/me/favorite-rooms`, `PUT /api/me/favorite-rooms/[^/]+`, `DELETE /api/me/favorite-rooms/[^/]+` → `PermissionCode.READ`. T014, T015 → green.
- [X] T025 [US2] Add `boolean favoritesOnly` to `BE/domain/RoomSearchCriteria.java` (last component; keep all existing convenience constructors, defaulting it to `false`) and `Boolean favoritesOnly` to `BE/dto/RoomSearchRequest.java` (`Boolean.TRUE.equals(...)` in `toCriteria`).
- [X] T026 [US2] In `BE/service/RoomSearchService.java` inject `RoomFavoriteService`; in `search(criteria, userId)` load `favoriteRoomIds(userId)` (empty when `userId == null`); if `criteria.favoritesOnly()` keep only candidates whose id is in that set; pass the set to `RoomRanking`. Extend the log line with `favorites_only={}`. Update `BT/service/RoomSearchServiceTest.java` (T005) to the new constructor: add a Mockito mock for `RoomFavoriteService` (default `favoriteRoomIds` → empty set) so the existing tests compile and stay green, and add a test that a mocked favorite id is pinned first.
- [X] T027 [US2] Implement `favoriteKey` in `BE/domain/RoomRanking.java`: favorites sort before non-favorites. T013, T016 → green.

### Implementation for User Story 2 – frontend

- [X] T028 [P] [US2] Create `frontend/src/API/favoriteRooms.test.ts` (follow `frontend/src/API/roomStatus.test.ts` style) then `frontend/src/API/favoriteRooms.ts` with `listFavoriteRooms(): Promise<FavoriteRoom[]>`, `markFavoriteRoom(roomId)`, `unmarkFavoriteRoom(roomId)` using the shared client in `frontend/src/API/client.ts` (CSRF handled there); add type `FavoriteRoom { roomId: string; roomName: string; buildingName: string; floorName: string; available: boolean }` to `frontend/src/types/room.ts`.
- [X] T029 [P] [US2] Create `frontend/src/components/FavoriteToggle/FavoriteToggle.test.tsx` then `FavoriteToggle.tsx`: star `<button>` with `aria-pressed`, accessible name "Als Favorit markieren" / "Favorit entfernen", filled vs outline star using existing design tokens; props `{ roomId, isFavorite, onChange(roomId, next) }`; optimistic toggle; on API error reverts and shows the error; on 409 `FAVORITE_LIMIT_REACHED` shows inline "Höchstens 10 Favoriten – entferne zuerst einen anderen." (FR-006a).
- [X] T030 [P] [US2] Extend `frontend/src/components/RoomSearch/roomSearchParams.test.ts` and `RoomSearchPanel.test.tsx` first, then `roomSearchParams.ts` and `RoomSearchPanel.tsx`: checkbox "Nur Favoriten" ↔ query param `favoritesOnly=true`, restored from URL, cleared by "Filter zurücksetzen" (FR-013).
- [X] T031 [US2] Extend `frontend/src/pages/RoomListPage.test.tsx` first, then `RoomListPage.tsx`: fetch `listFavoriteRooms()` once on load; render `FavoriteToggle` per active search result; after a successful toggle re-run the current search with the unchanged query string and refresh favorites (research R6); with `favoritesOnly` active: section "Nicht verfügbar" listing favorites with `available === false` as "building · floor · room name", sorted by building then room name, without "Buchen", with a removable `FavoriteToggle`, hidden if empty (FR-014); if the user has no favorites at all, show "Du hast noch keine Favoriten. Markiere Räume mit dem Stern, um sie hier zu sehen." instead of the generic no-results message (FR-015). Admin non-search list: no toggle for deactivated rooms.
- [X] T032 [US2] Extend `frontend/src/pages/RoomDetailPage.test.tsx` first, then `RoomDetailPage.tsx`: `FavoriteToggle` next to the room name for active rooms, initial state from `listFavoriteRooms()` (FR-006).

**Checkpoint**: Favorites work end-to-end and are pinned; US1 ordering still applies below them.

---

## Phase 5: User Story 3 – Fewest unneeded specialised equipment first (Priority: P2)

**Goal**: Admins classify equipment types as *specialised*/*general*; within the same size band, rooms with fewer unrequested specialised types rank higher.

**Independent Test**: "PC-Arbeitsplätze" specialised, "Klimaanlage" general; two 20-seat rooms (whiteboard + AC vs whiteboard + PCs); search requiring whiteboard → AC room first.

### Tests for User Story 3 ⚠️ (write first, see them fail)

- [X] T033 [P] [US3] Add to `BT/domain/RoomRankingTest.java` one test per US3 scenario: (1) same band, plain whiteboard room before whiteboard + PC room; (2) better size band wins over fewer specialised items; (3) no required equipment → fewer specialised types first; (4) group 20, K 21 seats with PCs vs L 24 whiteboard only → L first; (5) general types (AC, sockets) never lower a room; (6) newly created type (default general) has no effect; (7) required specialised type does not count; plus: a deactivated specialised type assigned to the room still counts.
- [X] T034 [P] [US3] Extend `BT/controller/EquipmentTypeControllerTest.java`: `POST` without `specialised` → response `specialised: false`; `POST` with `specialised: true` → `true`; `PUT` with `specialised` omitted keeps the current value; `PUT` with `false` resets it; requires `EQUIPMENT_TYPE_MANAGE` as before. Extend `BT/repository/CatalogRepositoryIntegrationTest.java` to assert the column defaults to `false` for existing/seeded rows.

### Implementation for User Story 3

- [X] T035 [P] [US3] Add `private boolean specialised;` (column `specialised`, default `false`) with getter/setter to `BE/domain/EquipmentType.java`.
- [X] T036 [P] [US3] Add `Boolean specialised` (nullable; "On create, omitted means false (general). On update, omitted keeps the current value.") as last component of `BE/dto/EquipmentTypeRequest.java`, keeping the existing `(name)` and add a `(name, code)` convenience constructor so current callers compile; add `boolean specialised` to `BE/dto/EquipmentTypeResponse.java` (update `from` and keep the existing 3-arg constructor defaulting it to `false`).
- [X] T037 [US3] Apply `specialised` in `BE/service/EquipmentTypeService.java` create (null → false) and update (null → keep). T034 → green.
- [X] T038 [US3] Implement `specialisedSurplusKey` in `BE/domain/RoomRanking.java`: count of the room's equipment types with `isSpecialised()` whose id is not in `criteria.equipmentTypeIds()`; fewer first. T033 → green.
- [X] T039 [P] [US3] Extend `frontend/src/components/EquipmentCatalog/EquipmentCatalog.test.tsx` first, then `EquipmentCatalog.tsx`, `frontend/src/API/equipmentTypes.ts` and `frontend/src/types/room.ts` (`EquipmentType.specialised: boolean`): create/edit form checkbox "Spezialausstattung (wirkt sich auf die Sortierung aus)", unchecked by default on create; list shows a "Spezial" badge for specialised types; `createEquipmentType`/`renameEquipmentType` send `specialised`.

**Checkpoint**: Equipment fit refines the order inside each size band; new catalog types need no code change.

---

## Phase 6: User Story 4 – Popular rooms first among equally fitting rooms (Priority: P3)

**Goal**: Among rooms equal on all previous keys, rooms with more recent bookings (all users, last 90 days) come first.

**Independent Test**: Two otherwise identical rooms, 12 vs 3 completed bookings in the last 90 days → 12 first; cancelled, expired and future bookings ignored.

### Tests for User Story 4 ⚠️ (write first, see them fail)

- [X] T040 [P] [US4] Add to `BT/domain/RoomRankingTest.java`: equal rooms, popularity 12 vs 3 → 12 first; popularity never overrides favorites, size band or specialised surplus; missing entry in the map = 0; no criteria → favorites, then popularity, then name (US4 scenario 3).
- [X] T041 [P] [US4] Create `BT/repository/ReservationPopularityRepositoryTest.java` (integration, `AbstractIntegrationTest`): fixed `now`; reservations for room R with status `COMPLETED`, `ACTIVE`, `RESERVED` starting within `[now − 90 days, now]` count; `CANCELLED` and `EXPIRED` do not; start exactly at `now − 90 days` counts, one second earlier does not; start after `now` does not; rooms without bookings are absent from the result.
- [X] T042 [P] [US4] Add to `BT/RoomSearchIntegrationTest.java`: two identical rooms, insert 12 and 3 completed reservations in the last 90 days plus 10 cancelled and 10 future ones for the second → search lists the first room first; with a fixed `Clock` override if the test config supports it, else relative to `Instant.now()`.

### Implementation for User Story 4

- [X] T043 [US4] Add to `BE/repository/ReservationRepository.java` a JPQL query `countRecentByRoom(@Param("since") Instant since, @Param("now") Instant now)` returning `List<RoomBookingCount>` (new record or interface projection `roomId`, `count` in `BE/repository/` or `BE/dto/`): `select r.room.id, count(r) from Reservation r where r.status in (RESERVED, ACTIVE, COMPLETED) and r.startTime >= :since and r.startTime <= :now group by r.room.id`. T041 → green.
- [X] T044 [US4] In `BE/service/RoomSearchService.java` compute `now = Instant.now(clock)`, `since = now.minus(Duration.ofDays(90))` (constant `POPULARITY_WINDOW = Duration.ofDays(90)`), load counts once per search into `Map<UUID, Long>` and pass to `RoomRanking`. Skip the query when no candidate passes the filters. Update `BT/service/RoomSearchServiceTest.java`: stub `reservationRepository.countRecentByRoom(any(), any())` (default empty list), use a fixed `Clock`, and add a test that it is called with `since = now − 90 days` and `now` from that clock.
- [X] T045 [US4] Implement `popularityKey` in `BE/domain/RoomRanking.java`: higher count first, missing = 0. T040, T042 → green.

**Checkpoint**: All four ranking criteria active in the specified order.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T046 [P] Extend `BT/RoomSearchPerformanceIntegrationTest.java`: with its 500-room seed plus 5 000 reservations in the last 90 days and 10 favorites for the caller, a ranked `minPersons=10` search completes within the existing time budget (SC-004).
- [X] T047 [P] Add to `BT/config/AccessDeniedLogPrivacyTest.java` (or a new log privacy test in `BT/config/`) an assertion that favorite and search log lines contain no user id or e-mail (Constitution IV).
- [X] T048 [P] Update `specs/008-room-search-filter/contracts/room-search-api.yaml` description of `/rooms/search` ("Sorted by building name, then room name") to reference feature 017 ranking, so the two contracts do not contradict each other.
- [X] T049 Run all automated checks from `specs/017-room-search-ranking/quickstart.md` (`./mvnw test` subset, `npm test`, `npm run lint`) and fix failures.
- [x] T050 Execute the manual scenarios 1–11 in `specs/017-room-search-ranking/quickstart.md` against `docker compose up`; record for the PR description the measured time of scenario 11 (SC-004) and of scenario 7, from star click until the reordered list is rendered, browser Performance panel (SC-003, ≤ 1 s).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: after Phase 1. Blocks all stories.
- **US1 (Phase 3)**: after Phase 2. No dependency on other stories.
- **US2 (Phase 4)**: after Phase 2 (needs `room_favorite` from T002). Independent of US1/US3/US4.
- **US3 (Phase 5)**: after Phase 2 (needs `specialised` column from T002). Independent; scenario 2/4 tests assume US1's size key — if US3 is built before US1, mark those two tests `@Disabled("needs US1")` until T010 lands.
- **US4 (Phase 6)**: after Phase 2. Independent.
- **Polish (Phase 7)**: after all desired stories.

### Within Each Story

Tests (seen failing) → entity/DTO → repository → service → controller/filter → ranking key → frontend (each frontend task: test first).

### Shared-file caution

`BE/domain/RoomRanking.java`, `BT/domain/RoomRankingTest.java`, `BE/service/RoomSearchService.java` and `BT/RoomSearchIntegrationTest.java` are touched by several stories: tasks on them are not [P] across stories; merge sequentially.

## Parallel Example: User Story 2

```bash
# Tests in parallel (different files):
T013 RoomRankingTest favorites | T014 RoomFavoriteIntegrationTest | T015 AuthorizationMatrixTest | T016 RoomSearchIntegrationTest
# Backend building blocks in parallel:
T017 RoomFavorite | T018 RoomFavoriteRepository | T019 UserAccountRepository lock | T020 ConflictException code | T021 FavoriteRoomResponse
# Frontend in parallel with backend service work:
T028 favoriteRooms API | T029 FavoriteToggle | T030 RoomSearchPanel "Nur Favoriten"
```

## Parallel Example: User Story 3

```bash
T033 RoomRankingTest equipment | T034 EquipmentTypeControllerTest | T035 EquipmentType entity | T036 DTOs | T039 EquipmentCatalog UI
```

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 + Phase 2 (order unchanged, infrastructure in place).
2. Phase 3 (US1) → validate quickstart scenario 1 → deployable MVP: resource-friendly order by group size.

### Incremental Delivery

1. + US2 favorites (quickstart 4–7, 10–10d) → demo.
2. + US3 specialised equipment (quickstart 2, 9) → demo.
3. + US4 popularity (quickstart 8) → demo.
4. Polish (performance scenario 11, contract alignment).

### Notes

- Commit after each task or logical group; every PR shows the failing-then-passing tests (Constitution I).
- Do not change the search response schema; ordering and the new `favoritesOnly` parameter are the only API changes for `/rooms/search`.

---

## Phase 8: Convergence

- [X] T051 Add `favorites_count={}` (size of the searching user's favorite set, no ids) to the `room_search` log line in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomSearchService.java` and assert in `backend/src/test/java/at/mci/igp/raumlotse/config/RoomRankingLogPrivacyTest.java` that it is logged without user or room ids per plan: Constitution Check V (partial)
