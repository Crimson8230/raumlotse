# Research: Pages in "Papier & Tinte", Part 1

No `NEEDS CLARIFICATION` remains; decisions below come from reading the current code.

## R1 – Where the skip link and the main target live
- **Finding**: `App.tsx` has no layout component. `RequireAuth` renders `<Navigation/><Outlet/>` (all pages except login); each page/guard renders its own `<main>` (14 files, ~25 occurrences incl. loading/error/forbidden states). `LoginPage` is outside `RequireAuth`.
- **Decision**: Mount `<SkipLink targetId="inhalt"/>` as first element in `RequireAuth`'s frame (before `Navigation`) and at the top of `LoginPage`. Every `<main>` gets `id="inhalt"`. A static rule test scans `src/**/*.tsx` (non-test) and fails on any `<main` without `id="inhalt"`.
- **Rationale**: Smallest change that guarantees one target per rendered state; no new layout abstraction (constitution V).
- **Alternatives**: A `<PageMain>` wrapper component (rejected: 25 call-site edits anyway, plus an abstraction); a layout route with `<main>` (rejected: pages already own `<main>` and tests assert their structure; would be a larger refactor than this feature).
- **Note**: `RoomDisplayPage` sits under `RequireAuth` and therefore shows the skip link too. It only gets the `id`; its visual conversion stays out of scope. (Spec edge case wording "not given the skip link" is corrected accordingly in tasks/spec note.)
- **Guard states**: `RequireAuth`, `RequirePermission`, `RequireAdminMode`, `RequireRoleManagement` render `<main>` in non-happy states; they get the id as well (FR-002).

## R2 – Field-name labels in errors
- **Finding**: `formatApiError` (API/client.ts:45) builds `"{detail} (field: message; …)"` using raw `field`. 69 call sites use it, returning a string.
- **Decision**: Keep the signature `formatApiError(err): string` and map `e.field` through `fieldLabel()` there (one place fixes all pages, SC-002). Add `fieldErrorsOf(err): Record<string,string>` (field → message) so forms with `Field` can attach errors to the exact field.
- **Rationale**: Central, backward compatible, test-first on one function.
- **Alternatives**: Per-page mapping (rejected: 69 call sites, drift).

## R3 – Strip data in the room list
- **Finding**: The list endpoints return rooms only. Per-room data exists via `listRoomReservations(roomId, from, to)`; other users' entries are redacted by the backend (only time + status).
- **Decision**: Hook `useRoomDayWindows(roomIds, day)`:
  - loads windows only for rows that are visible (IntersectionObserver; when unavailable, e.g. jsdom, the first 20 rows);
  - at most 4 queries in flight, results cached per `(roomId, day)`;
  - state per room: `loading | ready | failed` → strip shows "lädt" / windows / "nicht verfügbar" (edge case);
  - `now` ticks every 60 s (marker moves, FR-005); windows are refetched every 5 min and when the tab becomes visible.
- **Mapping**: reservation → `TimeWindow`: `ownedByMe` → `meine`, otherwise `belegt`; only active statuses (not cancelled/expired) count; "gesperrt" is not part of reservations and is not shown in the list in this part.
- **Range (verified)**: No day-range, opening-hours or booking-window constant exists in frontend or backend (searched `src/` and `backend/src/main`). The room detail page loads all reservations of a room without `from`/`to`. Decision: new constant `STRIP_DAY_START = 07:00`, `STRIP_DAY_END = 20:00` (local time) next to the hook; reservations outside the range are clipped by `Strip`. Nothing to reuse; this is a display range, not a business rule.
- **Query (verified)**: `GET /api/rooms/{id}/reservations?from&to` (ISO instants) returns reservations *overlapping* the interval (`endTime >= from && startTime <= to`) of **all statuses** (`RESERVED, ACTIVE, COMPLETED, EXPIRED, CANCELLED`). The client therefore passes `from = day start`, `to = day end` (shortest payload) and drops `CANCELLED` and `EXPIRED`; `COMPLETED` windows are shown as `belegt` (the room was in use then).
- **Rationale**: Honors "no backend change" and FR-020; interface (`windows` per room) stays the same if a combined query is added later.
- **Alternatives**: New batch endpoint (rejected by clarification); load all rooms eagerly (rejected: N requests on page load).

## R4 – Form errors
- **Decision**: Replace `<p role="alert" className="feedback-error">` (17 uses) with `ErrorMessage`; field-bound errors via `Field`'s `error` using `fieldErrorsOf`. Form-wide errors on top of the form, `role="alert"` (already in `ErrorMessage`).
- **Required marker**: one hint line per form with required fields: "Pflichtfelder sind mit „Pflichtfeld“ gekennzeichnet." (FR-010).
- **Native inputs (verified)**: `ReservationForm` already uses `<input type="datetime-local">` for start and end (lines ~193/205), which satisfies FR-011; no split into date + time inputs. The remaining work in this form is `Field` wrapping, required marker, `ErrorMessage` and the primary submit button. Note that the form renders its own labels/`id`s (`res-*`); tests that query by label must keep working.

## R5 – Tables and lists
- **Finding**: Raw `<table>` in `MyUpcomingReservations`, `AdminOccupancyPage` (2), `AdminStatisticsPage` (2, one-liners with hidden captions and partial `scope`). 14 `<ul>` outside `ui/`, of which record lists become `DataList`; navigation and purely structural lists stay.
- **Decision**: Tables → `DataTable` (visible or visually-hidden caption allowed only where the page heading already names the table; default visible). A static rule test fails on raw `<table` outside `components/ui`.

## R6 – Typography
- **Finding**: `index.css` has `--font-serif`, `--font-mono`, a `.mono` utility and serif only for the room list name; 26 places format date/time via `utils/date.ts`.
- **Decision**: Add utility class `.room-name` (serif) in `index.css`; wrap date/time output with `<time className="mono">` where text is rendered (not inside inputs). No change to `utils/date.ts` signatures.
- **Rationale**: Atkinson's slashed zero is the stated reason (docs/DESIGN-UMSTELLUNG.md §3).

## R7 – Admin navigation at 1280 px
- **Finding**: `.nav-inner` is `min(1120px, 100% − 32px)` with `flex-wrap: wrap`; in admin mode 3 user + 7 admin links + indicator + button overflow into a second row at 1280 px.
- **Decision**: Widen `.nav-inner` for the navigation (`min(1280px, 100% − 32px)`), reduce link gap/padding in admin mode, keep the indicator text; verify by screenshot at 1280 px (`visual-review` checklist). Below 1280 px, wrapping stays controlled (second row, still keyboard-operable).
- **Risk**: May still not fit with all rights; fall back to moving the indicator into the mode button's row (second row by design, no overlap). Acceptance is "no unintended wrap/overlap at 1280 px with all links", measured in the visual review.

## R8 – Primary actions
- **Decision**: Use existing `.btn-primary`; at most one per form/section: "Buchen" (room row), "Reservierung bestätigen"/"Buchen" (booking form), "Speichern" (RoomForm, catalogs, settings), "Anmelden" (login). Management actions are secondary (FR-021).

## R9 – Row structure and "Verwalten"
- **Decision**: `RoomRow` = `<li>` with: favorite toggle (state), number, name link (serif), building/floor, `Strip`, main action `Buchen`/`Öffnen` (primary when the user has `RESERVE`, otherwise link to the room), then in admin mode `<div role="group" aria-label="Verwalten">` with edit/deactivate/delete as secondary buttons.
- **Alternatives**: separate admin page (rejected by clarification).
