# Raumlotse – Designumstellung „Papier & Tinte“

Stand: 2026-10-14 · `dev` auf `639135d` (Features 012–017 gemergt)

Entscheidungen E1–E3 stehen in Abschnitt 6. Die Altlasten aus Feature 013 und der Systemdokumentation (früher Abschnitt 7) liegen in [`OFFENE-PUNKTE-013.md`](OFFENE-PUNKTE-013.md).

## 1. Design-Referenz

- **Design-Canvas (privat, nur Besitzer; Freigabe über das Share-Menü der Seite):**
  https://claude.ai/artifact/AhfkTxZQh4Qw3EEiSLaYs6
- **Inhalt des Canvas (7 Boards):** Designsystem (Prinzipien, Farben, Schrift, Zustandssprache, Bausteine, Wegweiser, Zugänglichkeit, Informationsrang) · Studierende (Desktop) · Telefon · Lektorat (Raum buchen) · Administration (Modus aktiv, Häuser & Stockwerke) · Türschild 800×480 · Etagenanzeige 1920×1080.
- **Grundidee:** E-Paper-Anmutung. Papiergrau, Tinte, Haarlinien; Zustände als *Muster + Wort* (frei = leer, belegt = Schraffur, meine = volle Tinte, jetzt = blaue Linie, gesperrt = gepunktet), zarte Tönungen nur als Stütze. Administrationsmodus = invertierte Leiste.
- **Wichtige Werte:** Papier `#E7E5DD`, Papier hell `#F0EEE8`, Tinte `#161614`, Tinte 2 `#47463F`, Haarlinie `#B4B1A5`, Lotsenblau `#1B3796`, Siegelrot `#862215`, Frei-Ton `#CFE1C6`, Belegt-Ton `#EBCB92`. Schrift: Literata (Titel, Raumnamen), Atkinson Hyperlegible (Oberfläche), IBM Plex Mono (Zeiten, Zahlen). Text ≥ 7 : 1 (WCAG AAA), Zielgröße 44 px.
- Die Boards sind statische Entwürfe: Werte, Muster und Struktur übernehmen, nicht das Markup kopieren.

## 2. Umstellungsplan

| Schicht | Inhalt | Status |
|---|---|---|
| **1 – Tokens, Schriften, globale Stile** | Tokens in `src/index.css`, lokale Schriften (`@fontsource`), eckige Elemente, 44-px-Ziele, invertierte Admin-Leiste, Badges als Muster + Wort, Komponenten-CSS ohne feste Farben | **erledigt** (Commit `style(frontend)`), im Browser per Screenshots geprüft |
| **Übersetzung ins Deutsche** | Frontend-Texte, Statuslabels (`utils/labels.ts`), Backend-Meldungen, Validierungs-Locale | **erledigt** |
| **2 – Gemeinsame Bausteine** | `src/components/ui/`: Strip/StatusDot, Chip, Field, ErrorMessage, SkipLink, DataList, DataTable, Wegweiser, je mit Vitest-Test | **erledigt** (Feature 018; Sichtprüfung im Browser offen, siehe `specs/018-shared-ui-building-blocks/checklists/visual-review.md`) |
| **3 – Seiten umbauen** | siehe Abschnitt 3 | Teil 1 erledigt (Feature 019: Sprunglink, Raumzeile mit Streifen, Fehlermuster, Tabellen, Typografie); Rest siehe Abschnitt 3 |
| **4 – Neue Funktionen** | siehe Abschnitt 4 | offen, je ein eigener Spec-Kit-Durchlauf |

Reihenfolge-Empfehlung: Schicht 2 → Rest von Schicht 3 → Schicht 4 pro Funktion (Veranstaltungstitel und Stockwerk-Reihenfolge zuerst).

## 3. Offene Punkte Schicht 3 (Seiten)

Erledigt mit Feature 019 (Teil 1): Sprunglink und `id="inhalt"` an allen `<main>`; Raumliste als Zeile mit Streifen, „Jetzt“-Marke, einer Hauptaktion und Gruppe „Verwalten“; Fehlermuster (Symbol + „Fehler:“ + Text) und Feldlabels statt technischer Feldnamen; Tabellen über `DataTable`; Zeiten in Mono, Raumnamen in Serif (soweit nicht in zusammengesetzten Textzeilen); `.btn-primary` für die Hauptaktionen; Navigation im Administrationsmodus mit nicht umbrechendem Indikator.

Noch offen:

- Raumdetail/Buchen nach Lektorat-Board: Abschnitt „Raum auf einen Blick“ (Sitzordnungen, Ausstattung, Zugang) **unter** „Meine Buchungen“, Belegungen des Tages mit Titeln, Formular rechts.
- Studierenden-Ansicht: bisher gemeinsame Ansicht mit dem Lektorat; Design sieht Veranstaltung + Ort statt Plätze/Ausstattung vor (hängt an Abschnitt 4).
- Standorte/Admin: Master-Detail (Häuser links, Stockwerke rechts mit Pfeil-Buttons zum Sortieren), Formulare „Haus bearbeiten“ / „Stockwerk bearbeiten“, Spalte „Zugang“ in der Raumtabelle.
- Raumnummer als eigenes Feld der Raumzeile: `Room` hat keine Nummer, der Name trägt sie.
- Mono für Zeiten in zusammengesetzten Textzeilen (Belegungslisten der Auslastungsseite) und Serif für Raumnamen darin.
- Navigation: Ob die Leiste im Administrationsmodus bei 1280 px mit allen Rechten in **eine** Zeile passt, ist im Browser zu prüfen (zehn Links plus Indikator und Schalter sind rechnerisch zu breit; der Block bricht kontrolliert in eine zweite Zeile).
- Barrierefreiheit: axe-Lauf im Browser; Streifen-`aria-label` mit „Jetzt“ ist umgesetzt, Pflichtfeld-Hinweis in allen Formularen.
- Echte `ul`/`li`-Listen: bestehende Katalog- und Rollenlisten sind bereits Listen; ein Wechsel auf `DataList` ändert deren Panel-Layout und ist Teil 2.

## 4. Offene Punkte Schicht 4 (neue Funktionen, noch nicht im Backend/Spec)

| Im Design | Fehlt | Nötig |
|---|---|---|
| Veranstaltungstitel (Studierende, Türschild, Etagenanzeige, Buchungsformular „Veranstaltung“) | Feld und öffentliche Anzeige | **Spec-Änderung** (widerspricht Annahme A3 aus Feature 013: Fremde sehen nur Zeitfenster; **entschieden: E1, öffentlich**), neues Feld an `Reservation`, Redaktionsregel, Formularfeld mit Hinweis „öffentlich, keine Personennamen“ |
| Stockwerk-Reihenfolge | Positionsfeld pro Stockwerk | neue Spalte + Migration, Verschiebe-Endpunkt, Sortierung überall |
| Haus-/Stockwerk-Editor | nur Katalog-Komponente | Master-Detail im Frontend (Aufzug und `groundFloor` gibt es bereits) |
| Etagenanzeige, Türschild | Etagenanzeige und Einmalcode-Anmeldung (aus Spec 012 verschoben), Richtung/Entfernung/Laufzeit. Das Türschild-Display existiert (`/rooms/:roomId/display`, Feature 005) und muss nur umgestellt werden. | eigene Features; Entfernungen aus Kartendaten (012, gemergt) oder manueller Pflege |
| Studierenden-Suche nach Veranstaltung | Suche über den Titel | hängt am Veranstaltungstitel |

## 5. Offene Entwurfs- und Barrierefreiheits-Punkte (aus dem Review)

Zum Stand: Der Review zu WCAG 2.2 wurde teilweise umgesetzt (A1–A10, C1, B3, B5, C5 im Canvas und in Teilen im Frontend). Offen:

- **B1** Konsistenter Hilfe-/Kontakt-Link im Footer (WCAG 3.2.6).
- **B2** Anmeldung nach Barrierefreiheits-Regeln entwerfen (Paste erlaubt, Passwortmanager, kein Rätsel; 3.3.8). Hinweis: Das Login existiert, ist aber nicht als Board entworfen.
- **B4** Karten-Entwurf mit sichtbarer Alternative zum Ziehen (2.5.7); im Frontend gibt es Pfeiltasten. Die Karte (Feature 012) ist gemergt.
- **C2** 12-px-Beschriftungen in Großbuchstaben mit Sperrung auf ≥ 14 px ohne starke Sperrung.
- **C3** Schalter „Muster aus / dezent“ gegen Mustermüdigkeit (flächige Alternative).
- **C4** `prefers-reduced-motion`: im Canvas und in `index.css` dokumentiert/umgesetzt, aber nicht getestet.
- Wirkung im Windows-Kontrastmodus (`forced-colors`) ist nur theoretisch abgesichert (`.strip`-Klasse), nicht am echten System geprüft.

## 6. Fachliche Entscheidungen

Entschieden am 2026-10-14:

1. **Rollen (E2): Buchen nur für Lehrende.** Das Design trennt Studierende (ansehen) und Lehrende (buchen). Bisher darf im Backend jeder angemeldete Nutzer buchen (Annahme A1/A9 in Feature 013). Es braucht daher eine eigene Spec, die Buchen auf Lehrende beschränkt. Zu klären bleibt, wie `ADMIN` und die Rollen aus Feature 015 dazu passen und welche Rolle Lehrende technisch sind.
2. **Veranstaltungstitel (E1): öffentlich.** Fremde sehen künftig den Titel zusätzlich zum Zeitfenster. Das hebt Annahme A3 aus Feature 013 auf und braucht eine Spec-Änderung samt Redaktionsregel. Das Buchungsformular erhält den Hinweis „öffentlich, keine Personennamen“.
3. **Ausstattungsnamen (E3): neue Migration.** „Projector“ und „Whiteboard“ stammen aus `V3__create_equipment_type_tables.sql` und sind englisch. `V3` wird nicht geändert, die Umbenennung erfolgt in einer neuen Migration.

Noch offen:

4. **Kurze sichtbare Button-Texte** mit langem `aria-label` wurden in den Katalogen umgesetzt; die Begriffe („Einchecken“, „Verfallen lassen“, …) sind ein Vorschlag und leicht änderbar.

## 7. Altlasten aus Feature 013 und Systemdokumentation

Ausgelagert nach [`OFFENE-PUNKTE-013.md`](OFFENE-PUNKTE-013.md), weil sie nicht zur Designumstellung gehören.

## 8. Technische Hinweise zur Umsetzung

- Neue Abhängigkeiten: `@fontsource/literata`, `@fontsource/atkinson-hyperlegible`, `@fontsource/ibm-plex-mono` (lokal gebündelt; Begründung im PR laut Constitution V).
- Veraltete Token-Aliase in `index.css` (`--color-success`, `--color-warning`, `--color-info`) bis alle Verwendungen umgestellt sind; nicht für neuen Code.
- Test-First gilt auch für die Bausteine (Constitution I).
- Beim Umbau der Seiten brechen Tests, die Texte und Rollen prüfen; Zeit zum Anpassen einplanen.
