# Implementation Plan: Ranked Room Search Results

**Branch**: `017-room-search-ranking` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/017-room-search-ranking/spec.md`

## Summary

Order the results of the existing room search (feature 008) by suitability instead of alphabetically: personal favorites first, then size band relative to the group size, then fewest unrequested *specialised* equipment types, then popularity (bookings of all users in the last 90 days), then building/room name. Ranking is a pure backend comparator (`RoomRanking`) applied after the unchanged in-memory filtering in `RoomSearchService`. New data: a `specialised` flag on equipment types and a `room_favorite` table (max. 10 per user, enforced under a per-user lock) with three `/api/me/favorite-rooms` endpoints. The search gains a `favoritesOnly` filter. The frontend adds a favorite star to search results and room detail, a "Nur Favoriten" checkbox with a "Nicht verfügbar" section, a "Sortiert nach Eignung" note, and a "Spezialausstattung" checkbox in the equipment catalog.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript strict (frontend)

**Primary Dependencies**: Spring Boot (Web MVC, Data JPA, Security, Bean Validation), Flyway; React, React Router, Vite

**Storage**: PostgreSQL 17 — migration `V17__room_search_ranking.sql` (equipment_type.specialised, room_favorite)

**Testing**: JUnit 5 + Spring Boot Test (`AbstractIntegrationTest`, Testcontainers-backed), Vitest + Testing Library

**Target Platform**: Linux server via Docker Compose; modern browsers

**Project Type**: Web application (backend + frontend)

**Performance Goals**: Ranked search ≤ 2 s for 500 rooms at p95 (SC-004); favorite toggle reflected ≤ 1 s (SC-003)

**Constraints**: Search response schema unchanged (order only, one new optional query parameter); max. 10 favorites per user, race-safe; ranking deterministic for unchanged data (SC-001); favorites strictly per authenticated user

**Scale/Scope**: ≤ 500 rooms, thousands of reservations per 90 days; 1 migration, ~3 new backend classes, 3 endpoints, 3 frontend touch points

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment | Status |
|---|---|---|
| I. Test-First | Ranking is a pure comparator → unit tests per acceptance scenario written first (`RoomRankingTest`); integration tests for popularity query, favorites endpoints and authorization; frontend tests for star toggle and catalog checkbox before implementation. | PASS |
| II. Typed & consistent | Constructor injection, records for DTOs, controller → service → repository; frontend functional components, strict TS, no `any`. | PASS |
| III. Contract-first | [contracts/room-search-ranking-api.yaml](contracts/room-search-ranking-api.yaml) and [contracts/ui.md](contracts/ui.md) defined before implementation; all changes additive (new optional field, new endpoints, same search schema). Order change of `/rooms/search` documented as a behavior change in the contract description. | PASS |
| IV. Secure by default | Favorites use the principal's user id only (no user id in requests); new endpoints added to `RoleAccessFilter` with `READ`; JPQL with parameters; logs only counts (`room_search result_count=…`), no user data. | PASS |
| V. Simplicity & observability | No new library/service; boolean instead of enum/weights; one aggregate popularity query; existing index reused; structured log line extended with `favorites_count`. | PASS |

**Post-design re-check**: unchanged — PASS. No Complexity Tracking entries.

## Project Structure

### Documentation (this feature)

```text
specs/017-room-search-ranking/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── room-search-ranking-api.yaml
│   └── ui.md
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/
├── resources/db/migration/V17__room_search_ranking.sql        # new
└── java/at/mci/igp/raumlotse/
    ├── domain/
    │   ├── EquipmentType.java          # + specialised
    │   ├── RoomSearchCriteria.java     # + favoritesOnly
    │   ├── RoomFavorite.java           # new entity (composite key user_id, room_id)
    │   └── RoomRanking.java            # new pure comparator (size band, specialised surplus, popularity, favorites)
    ├── dto/
    │   ├── FavoriteRoomResponse.java   # new: roomId, roomName, buildingName, floorName, available
    │   ├── EquipmentTypeRequest.java   # + Boolean specialised
    │   └── EquipmentTypeResponse.java  # + boolean specialised
    ├── exception/
    │   ├── ConflictException.java      # + optional code (FAVORITE_LIMIT_REACHED)
    │   └── GlobalExceptionHandler.java # pass code into Problem on 409
    ├── repository/
    │   ├── ReservationRepository.java  # + countRecentByRoom(since, now)
    │   ├── RoomFavoriteRepository.java # new
    │   └── UserAccountRepository.java  # + findByIdForUpdate (pessimistic lock for favorite limit)
    ├── service/
    │   ├── RoomSearchService.java      # + search(criteria, userId), ranking instead of BY_BUILDING_THEN_NAME
    │   ├── RoomFavoriteService.java    # new: list/mark/unmark, limit 10 under user-row lock
    │   ├── EquipmentTypeService.java   # handle specialised on create/update
    │   └── RoleAccessFilter.java       # + /api/me/favorite-rooms rules (READ)
    └── controller/
        ├── RoomSearchController.java   # pass authenticated user id
        └── RoomFavoriteController.java # new

backend/src/test/java/at/mci/igp/raumlotse/
├── domain/RoomRankingTest.java                       # new
├── RoomSearchIntegrationTest.java                    # + ranking & popularity cases
├── RoomFavoriteIntegrationTest.java                  # new (personal, idempotent, inactive room 404, limit 10 + concurrent marks, auth)
├── controller/EquipmentTypeControllerTest.java       # + specialised
└── MigrationVersionTest.java                         # + V17

frontend/src/
├── API/favoriteRooms.ts (+ test)                      # new
├── API/equipmentTypes.ts                             # + specialised
├── types/room.ts                                     # EquipmentType.specialised
├── components/FavoriteToggle/FavoriteToggle.tsx (+ test)  # new star button
├── components/RoomSearch/RoomSearchPanel.tsx, roomSearchParams.ts (+ tests)  # "Nur Favoriten" checkbox + URL state
├── pages/RoomListPage.tsx (+ test)                   # star per result, "Sortiert nach Eignung", "Nicht verfügbar" section, limit message, re-run search on toggle
├── pages/RoomDetailPage.tsx (+ test)                 # star next to name
└── components/EquipmentCatalog/EquipmentCatalog.tsx (+ test)  # "Spezialausstattung" checkbox and badge
```

**Structure Decision**: Existing web application layout (`backend/`, `frontend/`); the feature extends the 008 search classes in place and adds one entity, one repository, one service and one controller for favorites.

## Complexity Tracking

No constitution violations to justify.
