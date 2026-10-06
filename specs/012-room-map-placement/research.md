# Research: Room Map Placement

## D1 — Map is bound to a Floor
- **Decision**: `floor_map` has a unique `floor_id`; a room can only be placed on the map of its own floor.
- **Rationale**: `Building`/`Floor`/`Room.floor` already exist. Binding avoids a second naming scheme and makes "room on wrong map" impossible by construction. Spec updated (FR-009, Assumptions, Key Entities) accordingly.
- **Alternatives**: Free-standing named maps (duplicates floor info, allows inconsistent placement); one global map (rejected in clarification).

## D2 — Image storage in PostgreSQL `bytea`
- **Decision**: Store bytes, content type, width, height, and an `image_version` counter in `floor_map`; serve via `GET /api/maps/{id}/image` with ETag = image_version.
- **Rationale**: ≤10 MB and tens of maps; no new infrastructure or volume handling; transactional with placements; included in existing DB backups. Principle V (simplicity).
- **Alternatives**: Filesystem/Docker volume (extra deployment state), object storage (new service).

## D3 — Formats and validation
- **Decision**: PNG and JPEG only. Server decodes with `ImageIO` to confirm a real image and read width/height; content type is derived from the decoded format, not the client header. 10 MB limit via multipart config plus an explicit check. SVG excluded.
- **Rationale**: SVG can contain scripts; JDK `ImageIO` supports both formats without a dependency. (Spec assumption updated.)

## D4 — Coordinates
- **Decision**: `x`,`y` as `NUMERIC(6,5)` fractions in [0,1] relative to the image; DB `CHECK` plus bean validation.
- **Rationale**: FR-010 resolution independence; replacing an image with the same aspect ratio keeps positions. Different aspect ratio → API returns a `aspectRatioChanged` warning flag in the replace response (spec edge case).

## D5 — Frontend rendering
- **Decision**: `<img>` in a relatively positioned container with absolutely positioned markers at `left/top = x*100%/y*100%`; pan/zoom via CSS transform and pointer events.
- **Rationale**: No dependency; markers stay correct at any size. A canvas/Leaflet library is unnecessary for a static image.

## D6 — Authorization
- **Decision**: Reads stay `authenticated()` in `SecurityConfig`. Writes (non-GET under `/api/maps/**`, `/api/floors/{id}/map`, `/api/connections/**`) require ADMIN by extending `RoleAccessFilter`/`UserRoleSafety.requireAdmin` path matching, giving 401/403 Problem responses consistent with user-role endpoints.
- **Rationale**: Reuses the existing role mechanism; existing room/floor writes are not role-gated, so admin enforcement is introduced only for the new endpoints (scope-limited).
- **Display sessions**: The one-time-code display login does not exist yet (flagged in clarification). Any future display session that is an authenticated principal reads through the same `GET` endpoints with no change; adding the login itself is out of scope.

## D7 — Connections
- **Decision**: `connection` (name, type STAIRS|ELEVATOR) with `connection_point` rows `(connection_id, map_id)` unique; `pointCount < 2` returned as `incomplete=true`.
- **Rationale**: Clarified group model; avoids pairwise link tables and dangling links (cascade deletes from map and connection).

## D8 — Lifecycle
- **Decision**: `room_placement` references `room` with `ON DELETE CASCADE`; rename needs no action (label from join). Deactivated rooms keep their placement but are flagged `status` in responses so UIs/routing can ignore them. Deleting a floor with a map is rejected (existing rule: floors with rooms already can't be deleted); map deletion cascades placements and points.
