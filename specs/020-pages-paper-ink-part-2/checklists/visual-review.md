# Visual review: Pages in Papier & Tinte, Part 2

Manual checks from `quickstart.md`. To be performed in a browser (1280 px and 360 px) with backend and DB running.

- [ ] `/rooms/:id` at 1280 px: left "Meine Buchungen", "Belegungen des Tages", "Raum auf einen Blick"; booking form in the right column
- [ ] `/rooms/:id` at 360 px: order mine, form, day, overview; no horizontal page scroll
- [ ] Room without equipment/seating: "keine Angabe" in the overview
- [ ] Administration mode with `OTHER_RESERVATION_MANAGE`: "Weitere Reservierungen" shows details and actions
- [ ] `/admin/occupancy`: room names serif, times mono inside the lines
- [ ] Catalogs and role page: lists with and without records, actions unchanged
- [ ] Footer "Hilfe" at the same place on login and in the app; `/hilfe` reachable by keyboard
- [ ] Status badges: no wide-spaced capitals, still distinguishable
- [ ] Administration mode at 1280 px with all links: navigation on one row (or a controlled second row; note the link count): ______
- [ ] `prefers-reduced-motion: reduce`: no non-essential animation
- [ ] Contrast spot checks (text ≥ 7 : 1) on the new sections
