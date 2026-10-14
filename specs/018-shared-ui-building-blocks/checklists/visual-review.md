# Visual review: Shared UI Building Blocks (T036)

Manual checks from `quickstart.md` §3–4. Not yet performed (needs a browser and a screen reader).
Render the composition from `frontend/src/components/ui/Reference.test.tsx` in a scratch route.

- [ ] Grayscale emulation: every state of strip, chip and status dot still distinguishable (SC-001)
- [ ] Keyboard only: SkipLink, Field, chip buttons, wayfinding links reachable, focus ring visible (SC-004)
- [ ] 200 % text zoom and 320 px width: no horizontal page scroll, no lost content (FR-017)
- [ ] forced-colors emulation: states remain distinguishable
- [ ] Screen reader: strip is one stop and reads the day plus "Jetzt"; field reads label, Pflichtfeld, error, hint; table reads caption and headers
