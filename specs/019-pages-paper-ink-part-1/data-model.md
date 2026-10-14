# Data Model: Pages in "Papier & Tinte", Part 1

No persistent data, API or schema change. Only view-level models are introduced.

## RoomRowModel (view model)
| Field | Source | Notes |
|---|---|---|
| `room` | existing `Room` from list/search | unchanged |
| `isFavorite` | existing favorites list | unchanged |
| `windows` | `RoomDayWindows` | see below |
| `stripState` | `loading` \| `ready` \| `failed` | drives "lädt" / strip / "nicht verfügbar" |

## RoomDayWindows
| Field | Type | Notes |
|---|---|---|
| `roomId` | string | key |
| `day` | local calendar day | cache key together with `roomId` |
| `windows` | `TimeWindow[]` (from `components/ui`) | `state` is `belegt` or `meine` |
| `loadedAt` | timestamp | refetch after 5 min |

**Mapping from `Reservation`** (existing type): `startTime`/`endTime` → window bounds; `ownedByMe ? 'meine' : 'belegt'`; `CANCELLED` and `EXPIRED` are skipped, `COMPLETED` counts as `belegt`. Redacted entries (other users) already contain only time and status.

## FieldErrors
`Record<fieldName, message>` derived from `Problem.errors[]` (existing API error shape) by `fieldErrorsOf(err)`; labels resolved through `fieldLabel()` (018).

## State transitions
`RoomDayWindows`: `loading → ready` (response), `loading → failed` (error), `ready → loading` (refetch keeps old windows visible until the new response arrives).
