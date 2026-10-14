# Quickstart: Validating the Shared UI Building Blocks

## Prerequisites
```bash
cd frontend && npm install
```

## 1. Automated checks
```bash
npm test -- src/components/ui     # block tests + ui.rules + Reference page
npm run lint
npm run build                     # tsc -b must pass
npm test                          # full suite unchanged (SC-005)
```
Expected: all green; no existing test file modified.

## 2. Test-first evidence (SC-007)
For each block, the commit adding `*.test.tsx` precedes (and fails without) the implementation commit.

## 3. Visual review (manual, in the browser)
Render the reference composition from `Reference.test.tsx` temporarily in a scratch route or Storybook-less page and check:
- Grayscale (DevTools → emulate vision deficiencies → achromatopsia): every state still distinguishable (SC-001).
- Keyboard: Tab through SkipLink, Field, chip buttons, wayfinding links; focus ring visible (SC-004).
- Zoom text to 200 % and 320 px width: no horizontal page scroll (FR-017).
- Windows high-contrast / `forced-colors` emulation: states remain distinguishable.

## 4. Screen reader spot check
Strip: one stop, reads the whole day and "Jetzt". Field with error: label, "Pflichtfeld", error, hint. Table: caption, column and row headers.

## 5. Regression
`git diff --stat main -- frontend/src/pages frontend/src/App.tsx frontend/src/index.css` shows no changes (FR-015).
