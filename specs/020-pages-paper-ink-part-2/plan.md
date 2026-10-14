# Implementation Plan: Pages in "Papier & Tinte", Part 2

**Branch**: `020-pages-paper-ink-part-2` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/020-pages-paper-ink-part-2/spec.md`

## Summary

Rebuild the room detail page into the "Lektorat" layout (mine / day occupancy / room overview left, booking form right) from existing data; apply serif/mono inside composed text lines; move catalog and role lists onto `DataList`; add a shared footer with a static help page; remove the last uppercase-with-letter-spacing label; add an axe-based test suite and record browser checks (navigation at 1280 px, reduced motion). Frontend only, no backend change.

## Technical Context

**Language/Version**: TypeScript (strict), React 19, Vite
**Primary Dependencies**: existing; one new devDependency `axe-core` (test only, see research R9)
**Storage**: N/A
**Testing**: Vitest + Testing Library, ESLint; rule tests in the style of `pages.rules.test.ts`; tests first
**Target Platform**: desktop and phone browsers, verified at 1280 px and ~360 px
**Project Type**: web application, frontend only
**Performance Goals**: no additional requests on the room detail page (one reservation list, shared by the three sections)
**Constraints**: tokens only, contrast ≥ 7 : 1, targets ≥ 44 px, German texts, behavior unchanged (FR-012)
**Scale/Scope**: 1 page re-layout, 2 composed-line files, ~7 list sites, 1 footer + 1 page, 1 CSS rule, 1 test suite

## Constitution Check

| Principle | Assessment |
|---|---|
| I. Test-First | Pass. Failing tests before each change; existing room-detail tests adapted, none dropped. |
| II. Typed/consistent | Pass. Strict TS, functional components. |
| III. Contract-First | Pass. No API change; UI contract in `contracts/ui-pages.md`. |
| IV. Secure/Data-respecting | Pass. No new data; foreign reservations remain redacted; help page is static; contact address is not a secret. |
| V. Simplicity | Pass with note. One devDependency (`axe-core`) justified in research R9; small components `RoomOverview`, `RoomDayOccupancy`, `Footer`, `HelpPage`, `RoomName`/`TimeText`; no other abstractions. |

Re-check after design: Pass. Complexity Tracking not needed.

## Project Structure

```text
specs/020-pages-paper-ink-part-2/
├── plan.md  research.md  data-model.md  quickstart.md
├── contracts/ui-pages.md
├── checklists/
└── tasks.md            # by /speckit-tasks
```

```text
frontend/src/
├── pages/RoomDetailPage.{tsx,css}        # grid layout, always-visible form
├── pages/HelpPage.tsx, helpContent.ts    # NEW static help, route /hilfe (public)
├── pages/AdminOccupancyPage.tsx          # RoomName/TimeText, DataList
├── components/RoomOverview/              # NEW "Raum auf einen Blick"
├── components/RoomDayOccupancy/          # NEW strip + list of today's windows
├── components/ReservationList/           # scope "mine" | "managed"; badge CSS (R8)
├── components/Footer/                    # NEW, used in RequireAuth and LoginPage
├── components/ui/                        # RoomName, TimeText additions
├── components/{Building,Equipment,Connection}Catalog, pages/UserRoleListPage  # DataList
├── App.tsx                               # /hilfe route
└── a11y.test.tsx                         # NEW axe suite over pages in scope
```

**Structure Decision**: Frontend only; new folders only for the new components, everything else edited in place.

## Complexity Tracking

No constitution violations to justify.
