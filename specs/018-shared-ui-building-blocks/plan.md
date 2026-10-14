# Implementation Plan: Shared UI Building Blocks

**Branch**: `018-shared-ui-building-blocks` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/018-shared-ui-building-blocks/spec.md`

## Summary

Layer 2 of the "Papier & Tinte" migration (`docs/DESIGN-UMSTELLUNG.md`): a self-contained set of eight accessible React components under `frontend/src/components/ui/` — `Strip` (with `StatusDot`), `Chip`, `Field`, `ErrorMessage`, `SkipLink`, `DataList`, `DataTable`, `Wayfinding` — plus a shared state vocabulary (frei, belegt, meine, jetzt, gesperrt) and a field-label mapping. Each block is built test-first, styled only with the layer-1 tokens, and exercised by one test that doubles as its usage example. No existing page is touched; the skip link is delivered as a block with a documented "main content" target contract (clarification 2026-10-14). Frontend only; no backend, API or migration changes.

## Technical Context

**Language/Version**: TypeScript strict, React 19 (functional components)

**Primary Dependencies**: none new. Existing: `lucide-react` (symbols, decorative only), `@fontsource/*` (fonts, already bundled)

**Storage**: N/A

**Testing**: Vitest + Testing Library + `@testing-library/user-event` + jest-dom (jsdom); static CSS checks read the block stylesheets as text

**Target Platform**: modern browsers, phone to desktop; German UI only

**Project Type**: Web application (frontend-only change)

**Performance Goals**: none specific; the strip renders in a single pass with no layout measurement (positions are percentages)

**Constraints**: tokens only (no hex/rgb in block CSS); text contrast ≥ 7:1; targets ≥ 44 px; works at 200 % text zoom and 320 px width; no change to existing pages or tests (SC-005)

**Scale/Scope**: 8 components, 1 vocabulary module, 1 label-mapping module, ~9 test files, 0 pages changed

## Constitution Check

| Principle | Assessment | Status |
|---|---|---|
| I. Test-First | FR-016/SC-007: each block's test file is written and seen failing before its implementation; tasks order tests before code. | PASS |
| II. Typed & consistent | Strict TS, functional components, no `any`, ESLint clean; folder-per-component like existing `components/*`. | PASS |
| III. Contract-first | No REST contract. The public props of each block are defined in [contracts/ui-blocks.md](contracts/ui-blocks.md) before implementation. | PASS |
| IV. Secure by default | No data handling, no network, no `dangerouslySetInnerHTML`; user text rendered as text nodes only. | PASS |
| V. Simplicity | No new dependency; no component library or CSS-in-JS; plain CSS files using existing tokens; one small vocabulary module instead of per-block duplication. | PASS |

Re-check after Phase 1: unchanged, PASS.

## Project Structure

### Documentation (this feature)

```text
specs/018-shared-ui-building-blocks/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── ui-blocks.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
frontend/src/components/ui/
├── vocabulary.ts            # state vocabulary: frei, belegt, meine, jetzt, gesperrt (word, pattern, tone)
├── fieldLabels.ts           # technical field name -> German label, extensible
├── ui.css                   # shared block rules (patterns, focus, sizes) – tokens only
├── index.ts                 # public barrel
├── Strip/        Strip.tsx, StatusDot.tsx, Strip.css, Strip.test.tsx
├── Chip/         Chip.tsx, Chip.css, Chip.test.tsx
├── Field/        Field.tsx, Field.css, Field.test.tsx
├── ErrorMessage/ ErrorMessage.tsx, ErrorMessage.css, ErrorMessage.test.tsx
├── SkipLink/     SkipLink.tsx, SkipLink.test.tsx
├── DataList/     DataList.tsx, DataList.css, DataList.test.tsx
├── DataTable/    DataTable.tsx, DataTable.css, DataTable.test.tsx
├── Wayfinding/   Wayfinding.tsx, Wayfinding.css, Wayfinding.test.tsx
└── ui.rules.test.ts         # cross-cutting: no fixed colors, token contrast ≥ 7:1, target sizes in CSS
frontend/src/components/ui/Reference.test.tsx   # SC-006: composes a reference page from the blocks only
```

**Structure Decision**: Frontend-only, one folder per block under `components/ui/` as planned in `docs/DESIGN-UMSTELLUNG.md` §2. The existing global `.skip-link`, `.strip`, `.sr-only` rules in `index.css` stay untouched (FR-015); block CSS uses its own class prefix `ui-` to avoid any effect on existing pages.

## Complexity Tracking

No violations.
