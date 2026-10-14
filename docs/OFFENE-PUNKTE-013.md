# Raumlotse – Offene Punkte aus Feature 013 und Systemdokumentation

Stand: 2026-10-14 · `dev` auf `639135d`

Ausgelagert aus `docs/DESIGN-UMSTELLUNG.md` (dort Abschnitt 7), weil diese Punkte nicht zur Designumstellung gehören. Die Systembefunde (S, F, D, P) stammen aus `docs/SYSTEMDOKUMENTATION.md`.

## 1. Offene Punkte aus Feature 013

| Punkt | Status | Quelle |
|---|---|---|
| Warnung vor dem Verwerfen eines offenen Admin-Formulars beim Ausschalten des Modus. `BrowserRouter` unterstützt `useBlocker` nicht, daher zurückgestellt. | offen (deferred) | `specs/013-user-view-admin-mode/tasks.md` T044/T048 |
| Optionaler Index auf `reservation.created_by_user_id`: Query „my upcoming“ mit `EXPLAIN` auf einer befüllten Tabelle messen und entscheiden. | offen (nicht gemessen) | T052 |
| Manuelle Quickstart-Szenarien 1–9 nicht vollständig durchgespielt (Teile per Screenshot geprüft). | offen | `specs/013-user-view-admin-mode/quickstart.md` |
| Spec, Plan und Systemdokument erwähnen die Statistikseite (PR #32) nicht: Routenübersicht, Navigation, Matrix. | offen | – |
| `Problem.title` im Backend ist weiterhin englisch (wird nicht angezeigt). | offen, niedrige Priorität | `dto/Problem.java` |

## 2. Befunde der Systemdokumentation

Die Befunde sind nur dann als erledigt markiert, wenn der Stand im Code geprüft wurde. Alle übrigen sind unverändert aus dem Dokument vom 06.10. übernommen und müssen vor der Bearbeitung neu bewertet werden (seitdem kamen die Features 014–017 hinzu, u. a. Rollen-/Berechtigungsverwaltung).

| ID | Thema | Status |
|---|---|---|
| S4 | Rollenmodell nur teilweise wirksam, Autorisierung als Pfadfilter | neu bewerten (Feature 015 Rollen-/Berechtigungsverwaltung) |
| F2 | Doppelte Identität in `Reservation` teilweise | offen |
| F4 | Kein DB-Constraint gegen Überschneidungen | offen |
| F5–F9 | Scheduler, Mail-Retry, Gerätesteuerung | offen |
| D1–D12 | Stil, Paketstruktur, DTO/Entity, Validierung, Paging, Frontend-Struktur | offen |
| P1–P5 | Projektstruktur. Die Quelle nennt: keine wirksame CI (Workflow lag in `workflows/` statt `.github/workflows/`), Spec-Nummern inkonsistent, Ruleset-Exporte im Root. Eine genaue ID-Zuordnung steht in `SYSTEMDOKUMENTATION.md`. | teils erledigt, siehe unten |

Stand der drei genannten P-Punkte:

- **Keine wirksame CI:** erledigt, `.github/workflows/validate-main-source.yml` existiert.
- **Spec-Nummern inkonsistent:** offen, `004`, `005` und `009` sind je doppelt vergeben.
- **Ruleset-Exporte im Root:** nicht geprüft.

## 3. Vorschlag zur Weiterverfolgung

- Die Punkte aus Abschnitt 1 und den Befund F4 sowie die doppelten Spec-Nummern als Issues anlegen.
- S4 zuerst gegen Feature 015 prüfen, bevor daraus ein Issue wird.
