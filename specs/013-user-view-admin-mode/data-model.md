# Data Model: User View, Administration Mode and Access Control

No new tables. No data migration required.

## Reservation (existing, changed semantics)

| Field | Meaning after this feature |
|---|---|
| `created_by_user_id` (UUID, nullable) | **Authoritative owner.** Always set for new reservations from the authenticated principal. `NULL` only on legacy rows → manageable by ADMIN only (A4). |
| `created_by` (text) | Display-name snapshot at creation; informational only, never used for ownership or queries. Hidden from non-owners in shared schedules. |
| `reserved_for` (text) | Free text "booked for" person; not an owner. Hidden from non-owners. |

### Access rules

| Action | Owner | ADMIN | Other user |
|---|---|---|---|
| Read details | yes | yes | 404 |
| Edit (PATCH), activate, complete, expire, cancel | yes (status rules as before) | yes | 404 |
| See slot in room schedule | full entry | full entry | time window + status only |
| Appear in "my upcoming" | yes | only own | no |

### Index

`ix_reservation_user_upcoming (created_by, status, end_time, start_time)` (V10) serves the old name-based query. A replacement index on `(created_by_user_id, status, end_time, start_time)` is **optional**: add it via migration V14 only if the quickstart performance check shows the user-id query degrading (expected: not needed at current scale; V11 added the column without index). Decision deferred to implementation with a measured check.

## Administration Mode (session state, not persisted)

| Attribute | Type | Lifetime |
|---|---|---|
| `adminMode` | boolean in HTTP session | Until set off, session end/expiry, or loss of ADMIN role (cleared on next read). New sessions start `false`. |

Effective value returned to clients: `adminMode && actorIsAdmin`.

## User / Role (existing)

Unchanged. Only `ADMIN` carries rights; other roles equal the user view (A1, A9).
