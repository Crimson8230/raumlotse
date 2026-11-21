# Quickstart: Validating Routing and Neighbor Rooms

Prerequisites: Docker Compose stack running; Feature 023 data for at least two floors (network, scale, attached rooms, one elevator and one staircase attached on both floors); floors ordered on `/admin/locations`.

## 1. Floor order
1. Open `/admin/locations`, reorder floors with the up/down buttons; the bottom entry shows as ground floor and the old checkbox is gone.
2. Reload: order persists. `GET /api/buildings/{id}/floors` shows `groundFloor=true` only for the lowest entry.

## 2. Neighbors
1. `GET /api/rooms/{roomId}/neighbors` for a room on a corridor: up to 3 `LEFT` and 3 `RIGHT` room entries by distance, plus `UP`/`DOWN` stairs and elevator entries (see [contract](./contracts/routing-api.yaml)).
2. Room without attachment / map without scale: `available=false` with reason.
3. Open `/rooms/{roomId}/display`: neighbors appear as Wayfinding entries with distance, walking time and status; for the room from step 2 no neighbor area appears.

## 3. Routes
1. `GET /api/routes?fromRoomId=..&toRoomId=..` same floor: distance, time, steps.
2. Different floors: steps contain a `floorChange`.
3. Add `accessibleOnly=true`: never a stairs change; without an elevator → 409 `NO_ACCESSIBLE_ROUTE`.
4. Unauthenticated: 401.

## 4. Automated
- Backend: `./mvnw test` in `backend/` (routing unit tests, integration, migration)
- Frontend: `npm test`, `npx tsc -b`, `npm run lint` in `frontend/`

Data model: [data-model.md](./data-model.md)

## Reuse by other features (floor display, 026)

- **Neighbors of every room of a floor**: `RoutingService.neighborsForFloor(mapId)` returns the `NeighborsResponse` of each room attached on that map from one graph build. It is a service method only; no HTTP endpoint was added in this feature.
- **Result shapes**: `NeighborsResponse` (`available`, `reason`, `entries[]`) and `RouteResponse` (`distanceMeters`, `walkingSeconds`, `steps[]`) are identical for every room and every consumer; see [the contract](./contracts/routing-api.yaml).
- **Reasons** when `available=false`: `ROOM_NOT_ATTACHED`, `SCALE_MISSING`, `NO_ORIENTATION`, `NO_NEIGHBORS`. Route problems (409): `ROOM_NOT_ATTACHED`, `SCALE_MISSING`, `NOT_CONNECTED`, `NO_ACCESSIBLE_ROUTE`.
- **Direction semantics**: `LEFT`/`RIGHT`/`STRAIGHT` are seen by a person in the corridor facing the room's door; `UP`/`DOWN` follow the floor order of the building (lowest entry = ground floor).
- **Limits**: routing works within one building; connections between buildings are ignored. Walking speed 1.2 m/s, stairs 15 s per floor step, elevator 30 s per change (`RoutingDefaults`).
- **Freshness**: results are computed on every request from the current network; there is no cache to invalidate.
