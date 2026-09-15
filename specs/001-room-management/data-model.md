# Phase 1 Data Model: Room Management

Derived from `spec.md` Key Entities and Functional Requirements, and from the decisions in
`research.md`.

## Room

Represents a physical meeting space.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | System-generated, immutable (FR-002). |
| `name` | string | Required, non-empty (FR-007). Unique (case-insensitively) together with the building reached via `floor.building` (FR-013). |
| `floorId` | UUID (FK → Floor) | Required (FR-020). The room's building is derived through `floor.building` rather than stored redundantly on `Room`, to avoid the two ever disagreeing. |
| `status` | enum: `ACTIVE`, `DEACTIVATED` | Defaults to `ACTIVE` on creation (FR-001). Controls list visibility (FR-011). |
| `version` | integer (JPA `@Version`) | Used for optimistic-locking conflict detection (FR-017). Incremented on every update. |
| `createdAt` / `updatedAt` | timestamp | Audit fields; not exposed as user-editable. |

**Relationships**:
- Many `Room` → one `Floor` (a room references exactly one floor; a floor may have many rooms) (FR-020).
- One `Room` → many `SeatingArrangement` (composition: a seating arrangement cannot outlive its room). Minimum 1 (FR-003).
- One `Room` → many `EquipmentType`, through the `room_equipment` join table (many-to-many: an equipment type can be assigned to many rooms) (FR-015).

**Validation rules**:
- `name` non-empty (FR-007).
- `floorId` must reference a `Floor` that is currently `ACTIVE` and whose `Building` is also `ACTIVE`, at the time of assignment (FR-020). As with equipment types, a room may continue to display a floor/building that was later deactivated (existing assignment preserved).
- At least one `SeatingArrangement` at all times, on both create and update (FR-003).
- `(name, buildingId)` — where `buildingId` is read via `floor.buildingId` — unique across all rooms regardless of status (FR-013) — a deactivated room still reserves its name in its building, preventing a confusing duplicate if reactivated. Enforced at the application layer only (`RoomRepository.existsByFloor_Building_IdAndNameIgnoreCase`); unlike Building/Floor/EquipmentType, no DB-level unique index backs this, since it would require denormalizing `buildingId` onto `Room` (reintroducing the redundancy this design avoids) or a join-aware trigger. Accepted risk given the project's low concurrent-write volume (`plan.md` Scale/Scope) — revisit with a trigger-maintained shadow `building_id` column + unique index if this ever becomes a shared-write, high-concurrency system.
- Every assigned `EquipmentType` must currently be `ACTIVE` at the time of assignment (FR-015); a room may continue to display an equipment type that was later deactivated (existing assignments are preserved, see Equipment Type below).

**State transitions**:

```text
        create
          │
          ▼
      ┌────────┐  deactivate   ┌─────────────┐
      │ ACTIVE │ ─────────────▶│ DEACTIVATED │
      └────────┘◀───────────── └─────────────┘
          │        reactivate         │
          │ delete (only if no        │ delete (only if no
          │ dependent history)        │ dependent history)
          ▼                           ▼
       [removed]                  [removed]
```

- `ACTIVE → DEACTIVATED`: FR-008. Configuration is preserved.
- `DEACTIVATED → ACTIVE`: FR-009 (reactivate). Configuration is unchanged.
- `ACTIVE` or `DEACTIVATED → [removed]`: FR-010, permanent delete, only allowed when the room has no dependent history (currently vacuous — no feature references rooms yet — but the check is implemented as a real guard so it activates automatically once, e.g., a booking feature adds a foreign key to `room`).

## Seating Arrangement

Represents one furniture configuration a room can be set up in.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | System-generated. |
| `roomId` | UUID (FK → Room) | Required; cascades on room delete. |
| `name` | string | Required, non-empty (FR-007). Unique within its room (design decision — prevents two arrangements named e.g. "Theater" on the same room, resolving the open edge case in `spec.md` at the implementation level). |
| `maxCapacity` | integer | Required, > 0 (FR-004, FR-007). |

A room always has ≥ 1 seating arrangement; the last one cannot be removed via update (FR-003).

## Equipment Type

Represents a kind of equipment (e.g., projector, whiteboard), managed independently of any
specific room via an administrator-facing catalog (FR-014).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | System-generated. |
| `name` | string | Required, non-empty, unique across the catalog (case-insensitive) (FR-023). |
| `status` | enum: `ACTIVE`, `DEACTIVATED` | Defaults to `ACTIVE` on creation. Deactivated types cannot be newly assigned to a room (FR-015) but remain visible on rooms that already reference them (FR-016). |

**State transitions**:
- `ACTIVE ⇄ DEACTIVATED`: administrator-triggered (FR-014).
- `[any] → [removed]` (hard delete): only allowed while the type has zero room assignments (FR-016); otherwise the system rejects the delete and the administrator must deactivate instead.

## Building

Represents a physical building that contains rooms, managed independently of any specific
room via an administrator-facing catalog (FR-018).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | System-generated. |
| `name` | string | Required, non-empty, unique across all buildings (case-insensitive) (FR-023). |
| `status` | enum: `ACTIVE`, `DEACTIVATED` | Defaults to `ACTIVE` on creation. |

**Relationships**: One `Building` → many `Floor` (composition-like: a floor cannot exist without its building, but deleting the building requires its floors to be gone first rather than cascading the delete — see below).

**State transitions**:
- `ACTIVE → DEACTIVATED`: administrator-triggered; cascades to deactivate every `Floor` under this building in the same operation (FR-021, see `research.md` §7).
- `DEACTIVATED → ACTIVE`: administrator-triggered; does **not** cascade — each `Floor` stays `DEACTIVATED` until reactivated individually (FR-021).
- `[any] → [removed]` (hard delete): only allowed while the building has zero `Floor` rows, active or deactivated (FR-022). The administrator must delete (or simply leave deactivated) all floors first.

## Floor

Represents one floor level within exactly one building, managed independently of any
specific room via an administrator-facing catalog (FR-019).

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | System-generated. |
| `buildingId` | UUID (FK → Building) | Required, immutable after creation — a floor cannot be moved to a different building (not requested; re-creating under the correct building is the supported path if this is ever needed). |
| `name` | string | Required, non-empty, unique within its building (case-insensitive) (FR-023). |
| `status` | enum: `ACTIVE`, `DEACTIVATED` | Defaults to `ACTIVE` on creation. Effectively inactive if either the floor itself or its building is `DEACTIVATED` (see Room validation rules above). |

**State transitions**:
- `ACTIVE ⇄ DEACTIVATED`: administrator-triggered directly (FR-019), or forced to `DEACTIVATED` as a side effect of the parent `Building` being deactivated (FR-021).
- Reactivating a `Floor` (`DEACTIVATED → ACTIVE`) is rejected with a conflict if its parent `Building` is still `DEACTIVATED` (FR-021) — the building must be reactivated first.
- `[any] → [removed]` (hard delete): only allowed while the floor has zero `Room` rows referencing it (FR-022); otherwise the system rejects the delete and the administrator must deactivate instead.

## Room Equipment (join)

| Field | Type | Rules |
|---|---|---|
| `roomId` | UUID (FK → Room) | Composite PK part. |
| `equipmentTypeId` | UUID (FK → EquipmentType) | Composite PK part. |

Pure association table — no attributes of its own. A row's existence is what "removes/deactivates
instead of deletes" (FR-016) protects: an `EquipmentType` with any row here cannot be hard-deleted.
