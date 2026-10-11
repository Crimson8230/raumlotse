# Quickstart: Admin-Belegungsübersicht

Validation guide for feature 016. Endpoint shapes: [contracts/admin-occupancy.yaml](contracts/admin-occupancy.yaml). Counting rules: [data-model.md](data-model.md).

## Prerequisites

- Stack running: `docker compose up -d` (backend, frontend, PostgreSQL).
- An admin account (has `STATISTICS_READ`) and a Viewer-only account.
- At least two buildings with rooms: A with R1 (EG) and R2 (1. OG), B with R3 (EG).
- Test data: instead of creating it by hand, run the seed scripts in [seed/](seed/) against the running stack:
  ```bash
  # Scenarios 1–8 and 10 ('Seed 016 A' / 'Seed 016 B'); rerun right before scenario 1, R2 expires after the grace period
  docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/016-admin-occupancy-overview/seed/occupancy-seed.sql
  # Scenario 9: 100 rooms, 500 running bookings ('Seed 016 Perf 1–5'); running bookings end after 90 min
  docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/016-admin-occupancy-overview/seed/occupancy-seed-bulk.sql
  # Remove all seed data afterwards
  docker exec -i raumlotse-postgres psql -U raumlotse -d raumlotse -v ON_ERROR_STOP=1 < specs/016-admin-occupancy-overview/seed/occupancy-seed-cleanup.sql
  ```
  With the bulk data loaded, the totals in scenarios 1–3 include the 'Seed 016 Perf' buildings; filter on 'Seed 016 A' / 'Seed 016 B' or load the bulk data only for scenario 9.

## Automated checks

```bash
cd backend && ./mvnw test -Dtest='AdminOccupancy*,RoleAccess*,ProtectedBusinessRoute*,AuthorizationMatrix*'
cd frontend && npm test -- AdminOccupancyPage && npm run lint
```

Expected: all green. The service tests use a fixed `Clock` and cover R1/R2 from [research.md](research.md), including early check-in, ended-but-not-swept bookings, back-to-back bookings within one hour, and a DST day.

## Manual scenarios

| # | Setup | Action | Expected |
|---|---|---|---|
| 1 | R1 `ACTIVE` (4 attendees), R3 `ACTIVE` (10), R2 `RESERVED` started 5 min ago (6) | Admin opens **Admin → Belegung** | 14 eingecheckte Personen, 2 belegte Räume; list shows 3 bookings, R2 as "noch nicht eingecheckt"; no names visible (US1/US3) |
| 2 | Same | Check in R2 in another tab, wait ≤ 30 s | 20 Personen, 3 Räume without reload; data timestamp moves forward (FR-006) |
| 3 | Same | Breakdown and filter on building A | Breakdown A = 10/2, B = 10/1 after step 2; filter A shows only R1, R2 (US4) |
| 4 | Tomorrow, all `RESERVED`: R1 10:00–12:00 (5), R2 10:00–12:00 (12), R3 10:00–11:00 (7), R1 14:00–15:00 (3); R3 11:00–12:00 (20) cancelled | Open forecast for tomorrow | 10:00 → A 17, B 7, total 24 / 3 rooms (peak); 11:00 → A 17, B 0, total 17; 14:00 → A 3, total 3; the cancelled booking never appears (US2) |
| 5 | R1 tomorrow 10:00–10:30 (8) and 10:30–11:00 (6) | Click hour 10:00 | 8 Personen, 1 Raum; list contains both bookings (US2 scenario 5) |
| 5a | Scenario 4 data | Click hour 10:00 | Groups in this order: "A · 1. OG – 12" (R2), "B · EG – 7" (R3), "A · EG – 5" (R1); no names shown (FR-016) |
| 6 | — | Pick yesterday | Rejected with hint to **Statistiken** (FR-017) |
| 7 | — | Viewer opens `/admin/occupancy` and calls `GET /api/admin/occupancy` | Page not offered; API returns 403 (FR-002) |
| 8 | Stop backend while page is open | Wait ≤ 30 s | Last values and their timestamp stay, stale notice appears (FR-012) |
| 9 | Data volume of T033: 100 rooms, 500 running bookings (e.g. run `AdminOccupancyPerformanceIntegrationTest`'s seeding against the local DB, or create bookings via the API) | Open **Admin → Belegung** with the browser's Network/Performance panel recording | Figures, running-bookings list and today's forecast are fully shown within 3 s of navigation start; note the measured time in the PR (SC-006) |
| 10 | Admin opens the page as a user whose role has "Statistiken lesen", then removes that right from the role in another tab | Wait ≤ 30 s, then grant the right again and wait ≤ 30 s | Data, building filter and forecast disappear with "nicht mehr erlaubt"; after granting again they return without reload (FR-012) |

## API smoke test

```bash
curl -b cookies.txt http://localhost:8080/api/admin/occupancy
curl -b cookies.txt "http://localhost:8080/api/admin/occupancy/forecast?date=$(date -d tomorrow +%F)"
```

Check that no response contains `reservedFor`, `createdBy`, `note` or e-mail addresses (SC-005).
