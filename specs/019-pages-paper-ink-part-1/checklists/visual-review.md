# Visual review: Pages in Papier & Tinte, Part 1

Manual checks from `quickstart.md`. To be performed in a browser (1280 px and 360 px) with backend and DB running.

- [ ] Every page: first Tab shows "Zum Inhalt springen"; Enter moves focus into the main content (SC-001)
- [ ] `/rooms`: rows show number, serif name, strip with "Jetzt", one main action; empty state shows its text
- [ ] `/rooms` after one minute: the "Jetzt" marker has moved without reload
- [ ] `/rooms` at 360 px: number/name stack above strip and action, long names wrap, no horizontal page scroll
- [ ] Booking form: empty submit → "Fehler:" + "Beginn" on the field, announced by the screen reader
- [ ] Occupancy and statistics: tables read caption and headers with a screen reader
- [ ] Administration mode at 1280 px with all links: navigation on one row (or a controlled second row; note the link count): ______
- [ ] axe check of `/rooms`, `/rooms/:id`, `/login`, `/admin/occupancy`, `/admin/statistics`: no new violations compared with the baseline (SC-006)
- [ ] Grayscale and forced-colors emulation: strip states stay distinguishable
