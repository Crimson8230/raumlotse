# Quickstart: Validate Design System & Navigation

Prerequisites: Node tooling already set up for `frontend/` (see repo README); no backend changes are
required for this feature, but running the backend too lets `HomePage`'s health check resolve.

## 1. Run the frontend

```sh
cd frontend
npm install   # only if dependencies changed
npm run dev
```

Open the printed local URL (default `http://localhost:5173`).

## 2. Validate navigation (User Story 1 / FR-001, FR-002, SC-001, SC-006)

1. On any page (`/`, `/locations`, `/rooms`, `/rooms/new`), confirm the same navigation bar is visible
   with three items: Home, Standorte, Räume.
2. Click each item from every other page; confirm it navigates in a single click (SC-001).
3. Confirm the item matching the current page is visually marked active (contract:
   `contracts/navigation-component.md`), and that opening a room's edit page (`/rooms/:roomId`) still
   marks "Räume" active.

## 3. Validate the shared visual system (User Story 2 / FR-003–FR-008, SC-002, SC-003)

1. Open browser DevTools on `index.css` / computed styles; confirm colors resolve to the CSS custom
   properties defined in `contracts/design-tokens.md`, not literal hex values in component code.
2. Visually compare Home, Standorte, Räume list, and the Room form — headings, buttons, spacing, and
   borders should look drawn from the same system (SC-002).
3. Count distinct accent-category colors used across all pages (DevTools > computed styles, or grep
   `--color-accent` usages) — confirm ≤ 3 (SC-003, FR-004).

## 4. Validate semantic feedback states (User Story 3 / FR-009)

1. Go to `/rooms/new`, submit the form with required fields empty; confirm the validation error uses the
   semantic "error" token and is visually distinct from a success confirmation.
2. Reproduce the existing concurrent room-update conflict (see
   `backend/src/test/java/at/mci/igp/raumlotse/RoomConcurrentUpdateIntegrationTest.java` for the
   scenario this mirrors, or open the same room for edit in two tabs and save both): confirm the
   conflict message is visually distinguishable from both a plain error and a success confirmation.

## 5. Validate responsiveness and contrast (FR-010, FR-011, SC-004, SC-005)

1. Use browser DevTools device toolbar to set width to ~375px: confirm no horizontal scrolling and the
   navigation remains fully usable (no hidden/collapsed items).
2. Repeat at ~1280px width.
3. Run an accessibility contrast check (e.g. browser DevTools' built-in contrast checker, or an
   axe/Lighthouse pass) against the design tokens; confirm all text/background pairs meet WCAG 2.1 AA.

## 6. Run automated checks

```sh
cd frontend
npm run lint
npm run test
npm run build
```

All three MUST pass — `lint`/`build` guard Constitution Principle II (typed, lint-clean), `test` guards
Principle I (the new `Navigation.test.tsx` plus any updated existing page/component tests).
