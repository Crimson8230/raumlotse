# Data Model: Room Status Visualization

## Existing entities used

### Room

| Field | Use | Rules |
|---|---|---|
| `id` | Match reservations to the displayed room | Only matching room ids are considered |
| `name` | Display heading | Retained for every successful display state |
| `status` | Room validity | Missing/unresolved room data must not be shown as available |

### Reservation

| Field | Use | Rules |
|---|---|---|
| `id` | Deterministic tie-breaker | Lexicographically smallest id wins equal start times |
| `roomId` | Room relationship | Must equal the displayed room id |
| `startTime` | Current/next selection | Current when `startTime <= now`; next when `startTime > now` |
| `endTime` | Current selection | Current interval is exclusive at end: `now < endTime` |
| `status` | Lifecycle and display state | Current `ACTIVE` is occupied; current `RESERVED` is unclaimed; future `RESERVED` is next |
| `reservedFor` | Next-reservation value | Show a visible fallback if blank |

## Derived view model

```text
RoomDisplayViewModel
├── roomName: string
├── currentDateTime: Date
├── status: AVAILABLE | RESERVED | OCCUPIED | UNAVAILABLE
├── currentReservation: Reservation | null
├── nextReservation: Reservation | null
└── state: loading | unavailable | no-reservation | reservation
```

`AVAILABLE` maps to green and `Verfügbar`, `RESERVED` to yellow and `Reserviert`, and
`OCCUPIED` to red and `Belegt`. `UNAVAILABLE` never maps to an available presentation.

## Selection rules

1. Filter current candidates to the displayed room, statuses `RESERVED` or `ACTIVE`, and `startTime <= now < endTime`.
2. If a current `ACTIVE` candidate exists, derive `OCCUPIED`; select current reservation by start time then id.
3. Otherwise, if a current `RESERVED` candidate exists, derive `RESERVED`; select current reservation by start time then id.
4. Otherwise derive `AVAILABLE`, including when one or more future `RESERVED` reservations exist.
5. Independently select `nextReservation` as the earliest displayed-room `RESERVED` reservation with `startTime > now`, excluding terminal states by status filter.

## State transitions

```text
AVAILABLE --reservation reaches start without check-in--> RESERVED
AVAILABLE --future reservation exists--> AVAILABLE
RESERVED --check-in changes status to ACTIVE--> OCCUPIED
RESERVED --reservation ends/cancels/expires--> AVAILABLE or next current state
OCCUPIED --active reservation ends/completes--> AVAILABLE or next current state
```

Transitions are derived on each display data/time refresh; the feature does not change stored reservation lifecycle state.
