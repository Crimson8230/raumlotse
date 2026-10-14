# Quickstart: Validating feature 019

## Prerequisites
- `cd frontend && npm ci`
- Backend and DB running for manual checks: `docker compose up` (see root README).

## Automated
```bash
cd frontend
npx vitest run            # all suites incl. new rule tests
npx tsc --noEmit
npm run lint
```
Expected: all green; new rule tests: every `<main` has `id="inhalt"`; no raw `<table` outside `components/ui`; `formatApiError` shows labels; room list rows show strips.

## Manual (browser, 1280 px and ~360 px)
1. Any page: press Tab once → "Zum Inhalt springen" appears; Enter → focus in main content.
2. `/rooms`: each row has number, serif name, strip with "Jetzt", one "Buchen"; empty search shows empty-state text; wait 1 min → marker moves.
3. Booking form: submit empty → error says "Beginn: …" with symbol + "Fehler:".
4. Occupancy and statistics: tables have captions and header cells; screen-reader table navigation works.
5. Admin mode at 1280 px: navigation on one row.
6. Record results in `checklists/visual-review.md` (as in 018).

## Accessibility check
Run an automated check (axe via browser extension or `jest-axe` in tests if already present) on the pages above; expect no new violations (SC-006).
