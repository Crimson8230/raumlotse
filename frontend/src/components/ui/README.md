# Gemeinsame Bausteine ("Papier & Tinte", Schicht 2)

Import: `import { Strip, Chip, Field, ... } from '../components/ui'`. Nur Tokens aus `index.css`, Klassenpräfix `ui-`.
Jedes Beispiel unten steht auch als Test neben dem Baustein; die Referenzseite in `Reference.test.tsx` setzt alle zusammen.
Vertrag und Props: `specs/018-shared-ui-building-blocks/contracts/ui-blocks.md`.

| Baustein | Beispiel | Test |
|---|---|---|
| `Strip` | `<Strip name="Raum 2.14" rangeStart={a} rangeEnd={b} windows={[{ start, end, state: 'belegt' }]} now={now} />` | `Strip/Strip.test.tsx` |
| `StatusDot` | `<StatusDot state="belegt" />` | `Strip/StatusDot.test.tsx` |
| `Chip` | `<Chip state="meine" />`, `<Chip>Spezial</Chip>`, `<Chip state="frei" onClick={…} />` | `Chip/Chip.test.tsx` |
| `Field` | `<Field label="Name" required hint="…" error={e}>{(c) => <input {...c} />}</Field>` | `Field/Field.test.tsx` |
| `ErrorMessage` | `<ErrorMessage field="startTime">darf nicht null sein</ErrorMessage>` | `ErrorMessage/ErrorMessage.test.tsx` |
| `fieldLabel` | `fieldLabel('startTime')` → "Beginn"; `fieldLabel(n, { eventTitle: 'Veranstaltung' })` | `fieldLabels.test.ts` |
| `SkipLink` | `<SkipLink />` als erstes Element, `<main id="inhalt">` | `SkipLink/SkipLink.test.tsx` |
| `DataList` | `<DataList items itemKey renderItem emptyText="…" aria-label="…" />` | `DataList/DataList.test.tsx` |
| `DataTable` | `<DataTable caption columns rows rowKey rowHeader="name" emptyText="…" />` | `DataTable/DataTable.test.tsx` |
| `Wayfinding` | `<Wayfinding direction="hoch" target="Aufzug" distance="ca. 40 m" />` | `Wayfinding/Wayfinding.test.tsx` |

## Invarianten (für jeden Baustein getestet)

1. Jede Bedeutung steht auch als Text (Zustandswort, "Fehler:", "Pflichtfeld").
2. Bedienelemente sind per Tastatur erreichbar, mind. 44 px, mit sichtbarem Fokus.
3. Keine festen Farbwerte im Baustein-CSS (`ui.rules.test.ts`).
4. Fehlt ein optionaler Teil, bleibt keine verwaiste `aria-*`-Referenz.

## Hinweise

- Der `SkipLink` ist seit Feature 019 in `RequireAuth` und `LoginPage` eingebunden; jedes `<main>` trägt `id="inhalt"` (Regeltest `src/pages.rules.test.ts`).
- Raumnamen tragen die Klasse `room-name` (Serif), Zeiten und Zahlen `mono`; Fehler laufen über `ErrorMessage`, Feldfehler des Servers über `fieldErrorsOf` (`API/client.ts`).
- Der `Strip` braucht für eine laufende "Jetzt"-Marke eine von der Seite aktualisierte `now`-Prop.
