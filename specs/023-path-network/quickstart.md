# Quickstart: Path Network validation

Prerequisites: stack running (`docker compose up`), an admin session, a floor with a map (Feature 012) and at least two placed rooms; for US4 a connection with points on two maps.

References: endpoints in [contracts/path-network-api.yaml](./contracts/path-network-api.yaml); tables in [data-model.md](./data-model.md).

## Automated checks
- Backend: `cd backend && ./mvnw test` — service tests (bounds, same-map integrity, duplicate/self segments, split, scale derivation, cascades), controller tests (401/403/permission), migration test (V19 on top of V18).
- Frontend: `cd frontend && npm run lint && npx tsc -b && npm test`.

## Manual end-to-end scenarios
1. **Draw (US1)**: Open `/maps/:mapId`, enable the path overlay and edit tools. Add waypoints along a corridor and a junction, connect them, move one waypoint (segments follow), split a segment, delete a segment. Reload: network unchanged.
2. **Scale (US2)**: Draw the reference segment along a known distance, enter its length in meters. Select walkway segments: lengths in meters appear. Edit/delete the reference: lengths update / show "unavailable".
3. **Rooms (US3)**: Attach a placed room to a corridor waypoint; reload. Remove the room's placement: its attachment disappears.
4. **Connections (US4)**: Attach an elevator point on both floors; the connection shows fully attached. Delete the waypoint of one point: the connection is flagged not fully attached.
5. **Check (US5)**: Create an isolated sub-network, leave a room unattached, remove the reference segment: the issue list shows all three.
6. **Permissions**: As a non-admin user, the overlay is visible, edit tools are absent, and write calls return 403. Unauthenticated calls return 401.
7. **Routing read model (US6)**: `GET /api/maps/{mapId}/path-network` and `GET /api/path-network` return waypoints, segments with `lengthMeters`, attachments, `connectionGroups`; without a reference segment `scale.available=false` and `lengthMeters=null`.

Expected: all scenarios behave as listed; no orphan segments or attachments remain after any delete (SC-005).
