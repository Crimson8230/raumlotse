# Quickstart: Room Management

Validates that the Room Management feature works end-to-end once implemented. See
`data-model.md` for entity details and `contracts/openapi.yaml` for exact request/response
shapes.

## Prerequisites

- Docker and Docker Compose installed (for PostgreSQL, pgAdmin, and the backend container).
- Backend dependencies added per `research.md` (`spring-boot-starter-data-jpa`, PostgreSQL
  driver, Flyway) and migrations applied.
- Frontend dependencies added per `research.md` (Vitest, React Testing Library) for running
  the test suite; not required just to run the app.

## Setup

```bash
# From the repository root
docker compose up -d db pgadmin
cd backend && ./mvnw spring-boot:run   # applies Flyway migrations on startup
```

In a second terminal:

```bash
cd frontend && npm install && npm run dev
```

## Backend validation (API-level)

With the backend running on `http://localhost:8080`:

```bash
# 1. List the seeded equipment catalog (expect Projector and Whiteboard, both ACTIVE)
curl -s http://localhost:8080/api/equipment-types | jq

# 2. Create a building, then a floor under it (a fresh install has no buildings/floors yet)
curl -s -X POST http://localhost:8080/api/buildings \
  -H "Content-Type: application/json" -d '{"name": "Main"}' | jq
# -> note the returned "id" as <building-id>
curl -s -X POST http://localhost:8080/api/buildings/<building-id>/floors \
  -H "Content-Type: application/json" -d '{"name": "1"}' | jq
# -> note the returned "id" as <floor-id>

# 3. Create a room on that floor with one seating arrangement and both equipment types
curl -s -X POST http://localhost:8080/api/rooms \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Room 101",
        "floorId": "<floor-id>",
        "seatingArrangements": [{"name": "Theater", "maxCapacity": 40}],
        "equipmentTypeIds": ["<projector-id>", "<whiteboard-id>"]
      }' | jq
# Expect: 201, response includes a generated "id", "version": 0, status "ACTIVE",
# and nested "building"/"floor" objects matching what was created in step 2

# 4. Fetch it back
curl -s http://localhost:8080/api/rooms/<room-id> | jq

# 5. Update it, echoing the version from step 4 — expect 200 and version incremented
curl -s -X PUT http://localhost:8080/api/rooms/<room-id> \
  -H "Content-Type: application/json" \
  -d '{ "...": "same body as create, plus", "version": 0 }' | jq

# 6. Re-submit the SAME stale version from step 4 again — expect 409 Conflict (FR-017)

# 7. Deactivate, then confirm it is excluded from the default (active) list
curl -s -X POST http://localhost:8080/api/rooms/<room-id>/deactivate
curl -s "http://localhost:8080/api/rooms?status=active" | jq   # room absent
curl -s "http://localhost:8080/api/rooms?status=all" | jq      # room present, DEACTIVATED

# 8. Attempt to create a second room with the same name on a floor in the same building — expect 409 (FR-013)

# 9. Attempt to create a room with zero seating arrangements — expect 400 (FR-003)

# 10. Deactivate the building — expect the floor from step 2 to also become DEACTIVATED
curl -s -X POST http://localhost:8080/api/buildings/<building-id>/deactivate
curl -s "http://localhost:8080/api/buildings/<building-id>/floors?status=all" | jq  # floor shows DEACTIVATED (FR-021)

# 11. Attempt to delete that building — expect 409, since it still has a floor (FR-022)

# 12. Attempt to delete the floor while <room-id> still references it — expect 409 (FR-022)
```

## Frontend validation (UI-level)

1. Open `http://localhost:5173` (or the port Vite prints).
2. Navigate to the room list — it should load and show any existing active rooms.
3. On a fresh install (no buildings yet), open the room creation form and confirm it lets
   you create a building and a floor inline before selecting them for the room.
4. Create a new room via the form: fill name, select building/floor, add a seating
   arrangement, toggle an equipment type, submit — the new room should appear in the list.
5. Open the room, change its name, save — the change should persist and be visible on
   reload.
6. Deactivate the room from its detail view — it should disappear from the default (active)
   list but remain visible when filtering for deactivated rooms.
7. Attempt to submit the room form with no seating arrangements — the form should show a
   validation error and not submit.
8. Open the building/floor management page, deactivate a building that has an active floor —
   confirm the floor is shown as deactivated afterward, and that neither the building nor its
   floor appear as selectable options in the room form anymore.

## Automated tests (run before considering the feature done)

```bash
# Backend — unit, MockMvc, and Testcontainers integration tests
cd backend && ./mvnw test

# Frontend — component tests
cd frontend && npm run test
```

Both MUST be green, and per the constitution's Test-First principle, each test MUST have
existed and failed before the corresponding implementation code was written.
