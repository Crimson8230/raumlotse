# Raumlotse – Designumstellung „Papier & Tinte“

Stand: 2026-10-06 · `dev` auf `9e0d6f2` (Feature 013 gemergt, PR #34)

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
| **2 – Gemeinsame Bausteine** | `src/components/ui/`: Strip/StatusDot, Chip, Field, ErrorMessage, SkipLink, DataList, DataTable, Wegweiser, je mit Vitest-Test | offen |
| **3 – Seiten umbauen** | siehe Abschnitt 3 | teilweise |
| **4 – Neue Funktionen** | siehe Abschnitt 4 | offen, je ein eigener Spec-Kit-Durchlauf |

Reihenfolge-Empfehlung: Schicht 2 → Rest von Schicht 3 → Schicht 4 pro Funktion (Veranstaltungstitel und Stockwerk-Reihenfolge zuerst).

## 3. Offene Punkte Schicht 3 (Seiten)

- Sprunglink „Zum Inhalt springen“ auf allen Seiten und `id="inhalt"` an den `<main>`-Elementen (CSS-Klasse `.skip-link` existiert, ist aber nirgends eingebunden).
- `.btn-primary` ist angelegt, wird noch nirgends verwendet (z. B. „Buchen“, „Reservierung bestätigen“, „Speichern“).
- Raumliste als Listenzeile nach Design (Nummer, Name in Serif, Streifen mit Jetzt-Marke, genau eine Aktion); Streifen-Bauteil `.strip` ist nur als CSS-Klasse vorhanden.
- Raumdetail/Buchen nach Lektorat-Board: Abschnitt „Raum auf einen Blick“ (Sitzordnungen, Ausstattung, Zugang) **unter** „Meine Buchungen“, Belegungen des Tages mit Titeln, Formular rechts.
- Studierenden-Ansicht: bisher gemeinsame Ansicht mit dem Lektorat; Design sieht Veranstaltung + Ort statt Plätze/Ausstattung vor (hängt an Abschnitt 4).
- Standorte/Admin: Master-Detail (Häuser links, Stockwerke rechts mit Pfeil-Buttons zum Sortieren), Formulare „Haus bearbeiten“ / „Stockwerk bearbeiten“, Raumtabelle mit `caption`, `scope` und Spalte „Zugang“.
- Datums-/Zeitwerte überall in Mono; Raumnamen überall in Serif (nur in der Raumliste umgesetzt). Grund: Die Null von Atkinson Hyperlegible ist durchgestrichen und nicht abschaltbar.
- Navigation: Im Administrationsmodus bricht der Indikator bei 1280 px in eine zweite Zeile um (sechs Links plus Indikator).
- Barrierefreiheit in der App noch nicht umgesetzt: `aria-label` der Streifen mit allen Zeitfenstern und „Jetzt“, Fehlermuster (Symbol + „Fehler:“ + Text) in den Formularen, `table`/`caption`/`scope`, echte `ul`/`li` in den Listen, native Datums-/Zeitfelder und Pflichtfeld-Markierung im Buchungsformular, Pflichtfeld-Hinweis.
- Frontend-Feldnamen in Validierungsfehlern erscheinen technisch („startTime: darf nicht null sein“): Zuordnung Feldname → Label im Frontend.

## 4. Offene Punkte Schicht 4 (neue Funktionen, noch nicht im Backend/Spec)

| Im Design | Fehlt | Nötig |
|---|---|---|
| Veranstaltungstitel (Studierende, Türschild, Etagenanzeige, Buchungsformular „Veranstaltung“) | Feld und öffentliche Anzeige | **Spec-Änderung** (widerspricht Annahme A3 aus Feature 013: Fremde sehen nur Zeitfenster), neues Feld an `Reservation`, Redaktionsregel, Formularfeld mit Hinweis „öffentlich, keine Personennamen“ |
| Stockwerk-Reihenfolge | Positionsfeld pro Stockwerk | neue Spalte + Migration, Verschiebe-Endpunkt, Sortierung überall |
| Haus-/Stockwerk-Editor | nur Katalog-Komponente | Master-Detail im Frontend (Aufzug und `groundFloor` gibt es bereits) |
| Etagenanzeige, Türschild | Displays, Einmalcode-Anmeldung (aus Spec 012 verschoben), Richtung/Entfernung/Laufzeit | eigene Features; Entfernungen aus Kartendaten (012) oder manueller Pflege |
| Studierenden-Suche nach Veranstaltung | Suche über den Titel | hängt am Veranstaltungstitel |

## 5. Offene Entwurfs- und Barrierefreiheits-Punkte (aus dem Review)

Zum Stand: Der Review zu WCAG 2.2 wurde teilweise umgesetzt (A1–A10, C1, B3, B5, C5 im Canvas und in Teilen im Frontend). Offen:

- **B1** Konsistenter Hilfe-/Kontakt-Link im Footer (WCAG 3.2.6).
- **B2** Anmeldung nach Barrierefreiheits-Regeln entwerfen (Paste erlaubt, Passwortmanager, kein Rätsel; 3.3.8). Hinweis: Das Login existiert, ist aber nicht als Board entworfen.
- **B4** Karten-Entwurf mit sichtbarer Alternative zum Ziehen (2.5.7); im Frontend gibt es Pfeiltasten.
- **C2** 12-px-Beschriftungen in Großbuchstaben mit Sperrung auf ≥ 14 px ohne starke Sperrung.
- **C3** Schalter „Muster aus / dezent“ gegen Mustermüdigkeit (flächige Alternative).
- **C4** `prefers-reduced-motion`: im Canvas und in `index.css` dokumentiert/umgesetzt, aber nicht getestet.
- Wirkung im Windows-Kontrastmodus (`forced-colors`) ist nur theoretisch abgesichert (`.strip`-Klasse), nicht am echten System geprüft.

## 6. Offene fachliche Entscheidungen

1. **Rollen:** Das Design trennt Studierende (ansehen) und Lehrende (buchen). Im Backend wird nur `ADMIN` ausgewertet; alle angemeldeten Nutzer dürfen buchen (Annahme A1/A9 in Feature 013). Entweder gemeinsame Ansicht oder eigene Spec, die Buchen auf Lehrende beschränkt.
2. **Veranstaltungstitel öffentlich?** (siehe Abschnitt 4) – muss vor dem Umbau der Studierenden-Ansicht entschieden werden.
3. **Ausstattungsnamen:** „Projector“ und „Whiteboard“ stammen aus der Datenbank (Migration V3) und sind englisch; umbenennen oder Migration ändern.
4. **Kurze sichtbare Button-Texte** mit langem `aria-label` wurden in den Katalogen umgesetzt; die Begriffe („Einchecken“, „Verfallen lassen“, …) sind ein Vorschlag und leicht änderbar.

## 7. Offene Punkte aus Feature 013 und Systemdokumentation

- Spec/Plan/Systemdokument erwähnen die Statistikseite (PR #32) nicht: Routenübersicht, Navigation, Matrix.
- Nicht umgesetzt: Warnung vor dem Verwerfen eines offenen Admin-Formulars beim Ausschalten des Modus (T044/T048; `BrowserRouter` unterstützt `useBlocker` nicht).
- Nicht gemessen: optionaler Index auf `reservation.created_by_user_id` (T052).
- Manuelle Quickstart-Szenarien 1–9 aus `specs/013-user-view-admin-mode/quickstart.md` nicht vollständig durchgespielt (Teile per Screenshot geprüft).
- `Problem.title` im Backend ist weiterhin englisch (wird nicht angezeigt).
- Weitere Befunde der Systemdokumentation (`docs/SYSTEMDOKUMENTATION.md`): u. a. S4 (Rollenmodell nur teilweise wirksam, Autorisierung als Pfadfilter), F2 (doppelte Identität in `Reservation` teilweise), F4 (kein DB-Constraint gegen Überschneidungen), F5–F9 (Scheduler/Mail-Retry/Gerätesteuerung), D1–D12 (Stil, Paketstruktur, DTO/Entity, Validierung, Paging, Frontend-Struktur), P1–P5 (**keine wirksame CI**: Workflow liegt in `workflows/` statt `.github/workflows/`; Spec-Nummern inkonsistent; Ruleset-Exporte im Root).

## 8. Technische Hinweise zur Umsetzung

- Neue Abhängigkeiten: `@fontsource/literata`, `@fontsource/atkinson-hyperlegible`, `@fontsource/ibm-plex-mono` (lokal gebündelt; Begründung im PR laut Constitution V).
- Veraltete Token-Aliase in `index.css` (`--color-success`, `--color-warning`, `--color-info`) bis alle Verwendungen umgestellt sind; nicht für neuen Code.
- Test-First gilt auch für die Bausteine (Constitution I).
- Beim Umbau der Seiten brechen Tests, die Texte und Rollen prüfen; Zeit zum Anpassen einplanen.
