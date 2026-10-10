# Data Model: On-Site Presence Check-In

**Feature**: `014-presence-checkin` | **Date**: 2026-10-08

## Reservation (existing table `reservation`, extended)

| Column | Type | Null | Notes |
|---|---|---|---|
| `check_in_method` | `TEXT` with `CHECK (check_in_method IN ('QR','NFC','MANUAL'))` | yes | Set once on `RESERVED → ACTIVE` (FR-013) |
| `checked_in_at` | `TIMESTAMPTZ` | yes | Server time of the accepted check-in |
| `checked_in_by_user_id` | `UUID` | yes | Owner or administrator who confirmed; not exposed in redacted views |
| `last_presence_at` | `TIMESTAMPTZ` | yes | Latest motion event during `ACTIVE` (FR-016) |

**Rules**
- The three check-in columns are either all null or all set. They are set in the same save as `status = ACTIVE` and are never changed afterwards.
- `last_presence_at` may only be updated while `status = ACTIVE` and `startTime <= now < endTime`.
- Existing rows keep `NULL`. Reservations that were activated before this feature have no recorded method.

**Lifecycle (unchanged states, new side effects)**

```text
RESERVED ──check-in (QR/NFC, window open) or manual activate──▶ ACTIVE   ⇒ prepare room
RESERVED ──grace elapsed (sweep)───────────────────────────────▶ EXPIRED  (no device effect)
RESERVED ──cancel──────────────────────────────────────────────▶ CANCELLED (no device effect)
ACTIVE   ──endTime reached (sweep) or manual complete──────────▶ COMPLETED ⇒ release room*
ACTIVE   ──cancel──────────────────────────────────────────────▶ CANCELLED ⇒ release room*
```

\* Release is skipped if another reservation for the same room is `ACTIVE` at that moment.

**Check-in window (all check-in methods, including manual activation)**: `startTime - PT10M <= now <= startTime + PT5M` and `now < endTime`; while `now < startTime`, additionally no other `RESERVED`/`ACTIVE` reservation of the room may be ongoing. `PT10M` and `PT5M` are the current check-in settings (see below; defaults 10 and 5 minutes). `checkInOpensAt` is the later of `startTime - PT10M` and the end of the ongoing reservation.

**In use**: an `ACTIVE` reservation is in use from `coalesce(checkedInAt, startTime)` until `endTime`; a `RESERVED` one covers `[startTime, endTime)`. Room status, device-control eligibility and presence events use this rule.

Rejection reasons (API `reason` and log field):

| Reason | Condition |
|---|---|
| `TOO_EARLY` | A matching reservation exists, but `now < startTime` |
| `EXPIRED` | The matching reservation is `EXPIRED` or its window has passed |
| `ALREADY_ACTIVE` | The matching reservation is already `ACTIVE`. Idempotent: responds `200` with `alreadyActive: true` and sends no device commands |
| `NO_MATCH` | No reservation for this room is owned by the caller (or visible to an administrator) today |

Only reservations whose `startTime` falls on the current day (application time zone `Europe/Berlin`, as `AdminStatisticsPeriod.ZONE`) are considered for `TOO_EARLY` and `EXPIRED`. Anything else gives `NO_MATCH`. `checkInOpensAt` is therefore always a time today.

## CheckInMethod (new enum)

`QR`, `NFC`, `MANUAL`. The API accepts `QR` and `NFC` from the check-in endpoint; `MANUAL` is set by the existing activate action.

## RoomDeviceKind (existing enum, extended)

`LIGHTING`, `VENTILATION`, `PROJECTOR`, **`DOOR`**

Migration `V14__presence_checkin.sql`:
- Replaces the `room_device_state.kind` check constraint to include `'DOOR'`.
- Adds the four reservation columns above.

## RoomDeviceState (existing table `room_device_state`, unchanged shape)

| Kind | `state = true` | `state = false` | Default without a row |
|---|---|---|---|
| `LIGHTING` | on | off | off |
| `VENTILATION` | on | off | off |
| `DOOR` | unlocked | locked | locked |
| `PROJECTOR` | on | off | off (never automated) |

**Automation targets**

| Phase | LIGHTING | VENTILATION | DOOR | PROJECTOR |
|---|---|---|---|---|
| prepare | on | on | unlocked | unchanged |
| release | off | off | unchanged (FR-008, Assumptions) | unchanged |

## AutomationResult (in-memory value, not persisted)

- `failedDevices: List<RoomDeviceKind>`: devices whose gateway call threw. These are reported in the check-in response and logged.

## RoomStatus (read model, not persisted)

Derived per request for `GET /api/rooms/{roomId}/status`:
- `status`:
  - `OCCUPIED` if an `ACTIVE` reservation covers now.
  - Otherwise `RESERVED` if a `RESERVED` one covers now.
  - Otherwise `AVAILABLE`.

  This is the same rule as `frontend/src/components/RoomDisplay/roomDisplayLogic.ts#deriveRoomDisplayStatus`.
- `lighting`, `ventilation`: boolean. `door`: `LOCKED | UNLOCKED`. Each comes from `room_device_state` or the default.
- `lastPresenceAt`: from the covering `ACTIVE` reservation. Administrators only; otherwise `null`.
- Contains no names, notes or reservation ids (FR-020).

## CheckInSettings (new table `check_in_settings`, exactly one row)

| Column | Type | Rule |
|---|---|---|
| `id` | `SMALLINT` PK | always `1` (`CHECK (id = 1)`) |
| `early_check_in_minutes` | `INTEGER` | `0–60`, default `10`; `0` disables early check-in |
| `grace_period_minutes` | `INTEGER` | `1–30`, default `5`; also the expiry threshold of feature 007 |
| `updated_at` | `TIMESTAMPTZ` | set on every change |
| `updated_by_user_id` | `UUID` NULL | administrator who changed it last; `NULL` for the migrated defaults |

Read on every check-in decision and every expiry sweep, so a change applies immediately. Migration `V15__check_in_settings.sql` creates the row with the defaults.
