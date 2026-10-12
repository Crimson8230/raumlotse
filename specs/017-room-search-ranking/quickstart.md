# Quickstart: Ranked Room Search Results

Validation guide for feature 017. Endpoint shapes: [contracts/room-search-ranking-api.yaml](contracts/room-search-ranking-api.yaml). UI behavior: [contracts/ui.md](contracts/ui.md). Ranking keys: [data-model.md](data-model.md).

## Prerequisites

- Stack running: `docker compose up -d` (backend, frontend, PostgreSQL); migration `V17` applied on start.
- The local fixture admin account (`AUTH_FIXTURE_EMAIL` in `.env`, has `EQUIPMENT_TYPE_MANAGE`, `ROOM_MANAGE`).
- Test data: instead of creating it by hand, load the seed scripts in [seed/](seed/) into the running stack.
  Run the commands from the project root; they work in bash, zsh and fish, and all scripts are safe to rerun.
  ```sh
  # Scenarios 1–10d: lecturers X and Y, equipment catalog, building 'Seed 017 Haus R' with rooms A–M, no bookings
  docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/017-room-search-ranking/seed/ranking-seed.sql
  # Scenario 8 only: bookings for G and M (load it after scenarios 1–7, it changes their order)
  docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/017-room-search-ranking/seed/ranking-seed-popularity.sql
  # Scenario 11 only: 500 rooms in 'Seed 017 Perf 1–10' with 5 000 recent bookings
  docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/017-room-search-ranking/seed/ranking-seed-bulk.sql
  # Remove all seed data afterwards (keeps the equipment types)
  docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/017-room-search-ranking/seed/ranking-seed-cleanup.sql
  ```
  `ranking-seed.sql` gives X and Y the password of the oldest administrator account, normally the fixture account
  from `AUTH_FIXTURE_EMAIL`. To use another account's password, add `-v fixture_email=you@example.org` before the `<`.
- Logins X = `x@seed017.local` and Y = `y@seed017.local` (role Lecturer), both with that administrator's password.
- The seed creates this data. Your database usually holds other rooms and bookings too, so in every scenario also
  select the building **"Seed 017 Haus R"** in the search panel:
  - Equipment catalog: "Whiteboard" (general), "PC-Arbeitsplätze" (**specialised**), "Klimaanlage" (general),
    "Steckdosen am Platz" (general).
  - Building "Seed 017 Haus R", ground floor "EG", these active rooms with one arrangement each:

| Room | Arrangement(s) | Equipment |
|---|---|---|
| A | 6 | Whiteboard |
| B | 30 | Whiteboard |
| C | 100 | Whiteboard |
| F | 21 | Whiteboard, PC-Arbeitsplätze |
| G | 24 | Whiteboard |
| H | 45 | Whiteboard |
| J | 90 | Whiteboard |
| M | 24 | Whiteboard, Klimaanlage, Steckdosen am Platz |

## Automated checks

```bash
cd backend && ./mvnw test -Dtest='RoomRanking*,RoomSearch*,RoomFavorite*,EquipmentType*,RoleAccess*,MigrationVersion*'
cd frontend && npm test -- RoomListPage RoomDetailPage RoomSearchPanel EquipmentCatalog && npm run lint
```

Expected: all green. `RoomRankingTest` covers every acceptance scenario of US1–US4 and the band boundary (group 20: 25 seats → band 1, 26 → band 2) without a database; popularity uses a fixed `Clock`.

## Manual scenarios

| # | Setup | Action | Expected |
|---|---|---|---|
| 1 | Data above, no favorites, no bookings | X searches "Personen min. 4", building "Seed 017 Haus R" | Order: A (band 2); then F (surplus 17), G, M (20, tie → name), B (26), H (41), J (86), C (96), all band 4 by exact surplus; note "Sortiert nach Eignung" is visible (US1, FR-012) |
| 2 | Same | X searches min. 20, Ausstattung "Whiteboard" | Order: G, M, F (all band 1; F last because of specialised PCs; G/M tie on equipment since Klimaanlage/Steckdosen are general → name); then B (band 2); then H, J, C (band 4); A not listed (US3) |
| 3 | Same | X searches with only the building filter | Order A, B, C, F, G, H, J, M: no group size, no required equipment, no popularity yet, so the name decides |
| 4 | — | X clicks the star on C, then searches min. 4 | C first, then the order from scenario 1; star on C filled (US2) |
| 5 | C favorite of X | X searches min. 4 with "PC-Arbeitsplätze" required | Only F is listed; C absent (FR-009) |
| 6 | C favorite of X | Y searches min. 4 | C not pinned, star outline for Y (FR-007) |
| 7 | Scenario 4 result displayed, Performance panel recording | X clicks the star on C again | Within 1 s C moves back to its band position without pressing "Suchen"; note the measured time in the PR (FR-011, SC-003) |
| 8 | Load `ranking-seed-popularity.sql`: 12 completed bookings for M and 3 for G in the last 90 days, plus 10 cancelled bookings and 10 bookings next week for G | X repeats scenario 2 | Order: M, G, F, … — M now before G (12 vs. 3; G's cancelled and future bookings not counted); F stays behind both despite popularity rules (US4) |
| 9 | Admin adds new equipment type "Beamer 4K" without ticking "Spezialausstattung" and assigns it to G | X repeats scenario 2 | Order unchanged (new types are general, FR-004a) |
| 10 | Admin deactivates C (favorite of X), later reactivates it | X searches min. 4 after each step | C absent while deactivated; pinned first again after reactivation |
| 10a | X has favorites C and A; admin deactivates A | X ticks "Nur Favoriten" | Results: C only (ranked); "Nicht verfügbar" lists A with filled star; URL contains `favoritesOnly=true` (FR-013, FR-014) |
| 10b | Same | X clicks the star on A in "Nicht verfügbar" | A disappears from the section and from `/api/me/favorite-rooms` |
| 10c | Y has no favorites | Y ticks "Nur Favoriten" | Hint how to mark favorites with the star (FR-015) |
| 10d | X has 10 favorites | X clicks the star on an 11th room | Star stays outline, message "Höchstens 10 Favoriten – entferne zuerst einen anderen."; API returns 409 `FAVORITE_LIMIT_REACHED` (FR-006a) |
| 11 | Load `ranking-seed-bulk.sql`: 500 rooms, 5 000 bookings in the last 90 days | X searches min. 10 (no building filter) with network panel open | Ranked results shown within 2 s (SC-004); note time in PR |

## API smoke test

```bash
curl -b cookies.txt "http://localhost:8080/api/rooms/search?minPersons=4"
curl -b cookies.txt http://localhost:8080/api/me/favorite-rooms
curl -b cookies.txt -X PUT -H "X-XSRF-TOKEN: $XSRF" http://localhost:8080/api/me/favorite-rooms/<roomId>   # 204
curl -b cookies.txt -X DELETE -H "X-XSRF-TOKEN: $XSRF" http://localhost:8080/api/me/favorite-rooms/<roomId> # 204
```

Check that the search response contains no favorite, popularity or ranking fields, that an 11th `PUT` returns 409, and that `/api/me/favorite-rooms` of user Y never contains X's marks.
