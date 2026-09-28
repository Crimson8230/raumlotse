# Quickstart: Room Search with Filters

**Feature**: `008-room-search-filter` | **Date**: 2026-09-26

Validation guide proving the feature end to end. Shapes and rules live in [contracts/room-search-api.yaml](contracts/room-search-api.yaml), [contracts/ui.md](contracts/ui.md), and [data-model.md](data-model.md); they are not repeated here.

## Prerequisites

- Dev shell: `direnv allow` (or `nix develop`) — JDK 21, Maven, Node 24.
- `.env` created from `.env.example` with all placeholders replaced, plus a local login account via the `local-auth-fixture` profile (`SPRING_PROFILES_ACTIVE=local-auth-fixture`, `AUTH_FIXTURE_*` set). Disposable local database only.
- Docker running (Compose and Testcontainers).

## 1. Automated tests

```bash
cd backend && ./mvnw test          # includes Testcontainers integration tests; V9 migration applied automatically
cd frontend && npm install && npm test && npm run lint && npm run build
```

Expected: all green. New tests cover the matching rule (every filter, FR-007 same-arrangement rule, barrier-free truth table), validation errors, occupied-room query boundaries, the search panel, pre-fill, and the new catalog/room fields.

## 2. Start the app

```bash
docker compose up --build          # db, pgAdmin, backend (migration V9 runs on startup)
cd frontend && npm run dev         # http://localhost:5173
```

Sign in at `/login` with the fixture account.

## 3. Seed test data (via the UI, `/locations` and `/rooms/new`)

| Building | Elevator | Floors (ground floor?) |
|---|---|---|
| Haus 1 | yes | EG (yes), 1. OG (no) |
| Haus 2 | no | EG (yes), 1. OG (no) |

| Room | Building / Floor | Seating arrangements | Equipment | Not barrier-free |
|---|---|---|---|---|
| A | Haus 1 / 1. OG | Theater 60, U-Shape 20 | Projector, Whiteboard | no |
| B | Haus 2 / 1. OG | Classroom 15 | Projector | no |
| C | Haus 1 / EG | Classroom 120 | Whiteboard | **yes** |
| D | Haus 2 / EG | U-Shape 30 | – | no |

## 4. Scenarios (open `/rooms`, status "Active")

| # | Filters | Expected result | Covers |
|---|---|---|---|
| 1 | none | A, B, C, D (sorted Haus 1 → Haus 2, then name) | US1-1, FR-002 |
| 2 | min 20 | A, C, D | US1-2 |
| 3 | max 50 | A, B, D | US1-3 |
| 4 | building Haus 1 | A, C | US1-4 |
| 5 | min 200 | empty state + "Filter zurücksetzen" | US1-5, FR-012 |
| 6 | Bestuhlung "u-shape" | A, D | US2-1, case-insensitive |
| 7 | Bestuhlung U-Shape + min 30 | D only (A's U-Shape seats 20) | US2-4, FR-007 |
| 8 | Projector + Whiteboard | A | US2-3 |
| 9 | Barrierefrei erreichbar | A (elevator), D (ground floor); not B (upper floor, no elevator), not C (exclusion) | US3-1/2/6 |
| 10 | min 30 + max 10 | inline error, no request sent | SC-004 |
| 11 | Datum only, no times | inline error | edge case |

Deactivate room D, repeat #1 → D is gone. Reactivate it.

## 5. Availability and pre-fill

1. On room A, book tomorrow 10:00–12:00.
2. Search tomorrow 11:00–13:00 → A missing, B/C/D listed (US4-1).
3. Search tomorrow 12:00–13:00 → A listed (back-to-back, US4-2).
4. Cancel A's booking, search 11:00–13:00 again → A listed (US4-3).
5. From search #3, open room B → booking form open with 12:00–13:00 pre-filled; confirm → reservation created (US4-5, FR-011a).
6. Open room B from a search without date/time → booking form closed, no pre-fill (US4-6).
7. Reload `/rooms` with filters set → same filters and results restored (URL state).

## 6. API spot checks (optional)

With a logged-in browser session, open in the browser:

```text
/api/rooms/search?minPersons=20&barrierFree=true
/api/rooms/search?minPersons=abc                      → 400 VALIDATION_FAILED
/api/rooms/search?from=2026-10-05T10:00:00Z           → 400 (to missing)
/api/rooms/search/seating-arrangements                → ["Classroom","Theater","U-Shape"]
```

Without a session, each returns `401 AUTH_REQUIRED`.
