# Research: Admin-Statistikbereich

## Decision: Bestehende Reservierungsdaten direkt aggregieren

**Rationale**: Das Feature benötigt ausschließlich Auswertungen bestehender Buchungen, Räume und Raum-Ausstattung. Eine Statistik-Tabelle oder ein periodischer Snapshot würde zusätzliche Synchronisation, Backfill und Aktualisierungsfehler einführen, ohne für die spezifizierte Größenordnung notwendig zu sein.

**Alternatives considered**:

- Voraggregierte Statistik-Tabelle: verworfen, da keine historische Rekonstruktion oder Offline-Auswertung gefordert ist.
- Externer Reporting-Dienst: verworfen, da die Anwendung bereits alle benötigten Daten besitzt und keine externe Integration erforderlich ist.

## Decision: Ein gemeinsamer Zeitraum und ein gemeinsamer Statistik-Snapshot

**Rationale**: Die UI soll vermeiden, dass Kennzahlen aus unterschiedlichen Zeiträumen miteinander verglichen werden. Der Endpunkt akzeptiert ein lokales Startdatum und ein lokales exklusives Enddatum; das Backend wandelt beide in `Europe/Berlin`-Instants um und berechnet alle Metriken aus denselben Grenzen.

**Alternatives considered**:

- Je Metrik eigene Endpunkte: verworfen, weil mehrere Requests inkonsistente Zeitpunkte und unnötige UI-Komplexität erzeugen.
- UTC-Datumswerte als Benutzereingabe: verworfen, weil die Oberfläche kalendarische lokale Tage anzeigen soll.

## Decision: Halb-offene Zeitintervalle mit Überlappungsdauer

**Rationale**: Für eine Buchung `[start, end)` im Zeitraum `[from, to)` wird die Auswertungsdauer als `max(0, min(end, to) - max(start, from))` berechnet. Dadurch werden Buchungen an den Zeitraumgrenzen nicht doppelt gezählt und Teilüberschneidungen korrekt berücksichtigt.

**Alternatives considered**:

- Ganze Buchung nur bei vollständiger Einschließung zählen: verworfen, weil Grenzbuchungen und lange Buchungen falsch ausgewertet würden.
- Ganze Buchung bei jeder Überschneidung zählen: verworfen, weil die gebuchte Zeit und Auslastung dadurch überhöht würden.

## Decision: Gültige Nutzung anhand der Reservierungsstatus

**Rationale**: `RESERVED`, `ACTIVE` und `COMPLETED` repräsentieren vorhandene beziehungsweise stattgefundene Buchungen. `CANCELLED` und `EXPIRED` werden für tatsächliche Nutzung, gebuchte Zeit, Feature-Nutzung und Teilnehmerdurchschnitt ausgeschlossen. Die Stornierungsquote zählt `CANCELLED` separat.

**Alternatives considered**:

- Alle nicht gelöschten Datensätze als Nutzung werten: verworfen, weil stornierte und wegen Nichtantritt abgelaufene Buchungen die tatsächliche Nutzung verfälschen.
- Nur `COMPLETED` werten: verworfen, weil zukünftige gültige Reservierungen in der Auswertung fehlen würden.

## Decision: Raum-Ausstattung aus der Raumzuordnung auswerten

**Rationale**: Die Anforderung bezieht sich auf Raumfeatures beziehungsweise die Ausstattung eines Raums. Deshalb werden die `room_equipment`-Zuordnungen ausgewertet. Eine Buchung zählt einmal je zugeordnetem Feature; Gesamtbuchungen bleiben dedupliziert.

**Alternatives considered**:

- Nur zusätzlich bei der Buchung angeforderte Ausstattung auswerten: verworfen, weil dadurch die katalogisierte Ausstattung des Raums nicht sichtbar wäre.
- Beide Quellen vermischen: verworfen, weil eine klare Bedeutung der Kennzahl fehlen würde.

## Decision: Stornierungsquote auf zeitlich betroffene Buchungen beziehen

**Rationale**: Stornierte Buchungen werden anhand ihrer geplanten Zeitüberschneidung mit dem ausgewählten Zeitraum gezählt. Der Nenner umfasst alle Reservierungen mit Überschneidung im Zeitraum; die Quote ist bei leerem Nenner nicht anwendbar. Das entspricht der Spezifikation und bleibt unabhängig vom Zeitpunkt der Statusänderung.

**Alternatives considered**:

- Nach `created_at` gruppieren: verworfen, weil dadurch Buchungen außerhalb des betrachteten Nutzungszeitraums in die Raumstatistik gelangen könnten.
- Nur nicht stornierte Buchungen im Nenner: verworfen, weil dies keine Stornierungsquote aller angelegten Buchungen wäre.

## Decision: Admin-Schutz über vorhandene Rollenmechanismen

**Rationale**: Die Anwendung besitzt bereits `RequireAdmin` im Frontend, `RoleAccessFilter` im Backend und `Role.ADMIN`. Der neue Pfad `/api/admin/statistics` wird in denselben Schutz aufgenommen; zusätzliche Authentifizierungslogik wäre redundant und riskanter.

**Alternatives considered**:

- Frontend-only-Schutz: verworfen, weil direkte API-Aufrufe weiterhin Daten offenlegen könnten.
- Neue Statistik-spezifische Berechtigung: verworfen, da die Spezifikation ausschließlich Administratorzugriff fordert.

## Decision: Read-only API ohne Persistenzmigration

**Rationale**: Der Endpunkt liefert einen aggregierten Snapshot. Es werden keine Daten verändert, keine personenbezogenen Buchungsdetails übertragen und keine Flyway-Migration benötigt.

**Alternatives considered**:

- Neue Statistikentität: verworfen, siehe direkte Aggregation.
- Export-/Downloadformat: explizit außerhalb des Feature-Umfangs.
