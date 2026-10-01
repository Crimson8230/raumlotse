# Data Model: Room Status Visualization

## Existing entities used

### Room

Source: existing room response.

| Field | Use | Rules |
|---|---|---|
| `id` | Match reservations to the displayed room | Only records with the displayed room id are considered |
| `name` | Display heading | Retained in every status, loading-success, and no-next state |
| `status` | Existing room lifecycle | An unresolved/deactivated room must not be presented as available by this feature |

### Reservation

Source: existing room-reservation response.

| Field | Use | Rules |
|---|---|---|
| `id` | Deterministic tie-breaker | Lexicographically smallest id wins equal start times |
| `roomId` | Room relationship | Must equal the displayed room id |
| `startTime` | Current/next selection and display | ISO timestamp; current uses `startTime <= now`, next uses `startTime > now` |
| `endTime` | Current selection and display | Current interval is exclusive at end: `now < endTime` |
| `status` | Lifecycle/status color | `ACTIVE` is occupied; future `RESERVED` is next; terminal states are excluded |
| `reservedFor` | Next reservation label value | Render after `Reserved for:`; use a visible fallback if unexpectedly blank |

## Derived view model

The frontend should derive a single display model from the room, reservation list, and current
time. Suggested shape:

```text
RoomDisplayViewModel
├── roomName: string
├── currentDateTime: Date
├── status: AVAILABLE | RESERVED | OCCUPIED
├── statusLabel: Available | Reserved | Reserved and Occupied
├── currentReservation: Reservation | null
├── nextReservation: Reservation | null
└── state: loading | unavailable | ready
```

`status` and `statusLabel` are presentation values and are not persisted. `OCCUPIED` maps to
red, `RESERVED` to yellow, and `AVAILABLE` to green. The status presentation is rendered before
the current date/time. If required source data cannot be loaded, the model is `unavailable` and
must not map to `AVAILABLE`.

The `nextReservation` values are presented together on one readable line in this order:
`Next Reservation` / `Reserved for:` / `Start Time` / `End Time`. The exact separators are a
presentation concern, but none of the labels or values may be hidden or wrapped at the intended
display size.

## Selection rules

1. Filter current candidates to the displayed room, statuses `RESERVED` or `ACTIVE`, and
   `startTime <= now < endTime`.
2. If an `ACTIVE` current candidate exists, derive `OCCUPIED` and show the selected current
   reservation; tie-break by start time then id.
3. Otherwise, derive `RESERVED` only when a future `RESERVED` reservation exists; select its
   earliest `startTime`, then id.
4. Otherwise derive `AVAILABLE`.
5. Select `nextReservation` independently as the earliest displayed-room reservation with
   `status == RESERVED` and `startTime > now`, excluding terminal states by status filter.

## State transitions

```text
AVAILABLE --future RESERVED appears--> RESERVED
RESERVED --reservation becomes ACTIVE/current--> OCCUPIED
OCCUPIED --active reservation ends/completes--> RESERVED or AVAILABLE
RESERVED --reservation cancelled/expired--> RESERVED or AVAILABLE
```

Transitions are derived on each display data/time refresh; the feature does not change stored
reservation lifecycle state.
