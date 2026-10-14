# Data Model: Part 2

No stored data, API or schema changes. View models only:

- **RoomOverview** (derived from `Room`, `Building`, `Floor`, equipment type names): `seating: {name, maxCapacity}[]`, `equipment: string[]` (names resolved from equipment types; unknown id → id as text), `access: {barrierFreeReachable: boolean, hasElevator: boolean, groundFloor: boolean}`. Empty arrays render "keine Angabe".
- **DayWindow** (derived from `Reservation`): `start`, `end`, `state` (`frei|belegt|meine|…` per vocabulary), `ownedByMe`. Only windows overlapping today; others' details stay redacted as today.
- **HelpContent**: static constant (sections with heading and paragraphs, contact address).
