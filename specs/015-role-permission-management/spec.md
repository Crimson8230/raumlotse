# Feature Specification: Rollenrechte verwalten

**Feature Branch**: `015-role-permission-management`
**Created**: 2026-10-08
**Status**: Draft
**Input**: Ausgehend von Feature 003 und 013 sollen Admins Nutzern mehrere der fünf festen Rollen zuweisen und die Berechtigungen jeder Rolle für vorhandene Funktionen getrennt verwalten. Neue Nutzer erhalten Viewer. Vereinigte Rechte gelten sofort; der letzte Admin bleibt geschützt. Änderungen werden dauerhaft gespeichert.

## Clarifications

### Session 2026-10-08

- Q: Welche Berechtigungen soll Viewer bei der erstmaligen Einführung der Rollenrechte standardmäßig erhalten? → A: Nur Lesen; Viewer können nicht buchen.
- Q: Was soll passieren, wenn eine Rolle „Reservieren“, aber nicht „Lesen“ erhält? → A: Das Speichern wird abgelehnt; Reservieren erfordert auch Lesen in derselben Rolle.
- Q: Sollen auch die Rechte zum Bearbeiten von Reservierungen und zum Verwalten einzelner Bereiche „Lesen“ in derselben Rolle voraussetzen? → A: Ja; jedes weitere konfigurierbare Recht setzt Lesen in derselben Rolle voraus.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rechte je Rolle verwalten (Priority: P1)

Als Admin möchte ich für jede Rolle alle Funktionsrechte sehen und ändern, damit ich Zuständigkeiten gezielt vergeben kann.

**Why this priority**: Die konfigurierbare Zuordnung von Funktionen zu Rollen ist der Kern der Erweiterung.

**Independent Test**: Ein Admin ändert zwei Rechte von University Staff, speichert sie, öffnet die Rolle erneut und prüft Anzeige sowie Zugriff mit einem betroffenen Nutzer.

**Acceptance Scenarios**:

1. **Given** ein Admin öffnet die Rollenrechteverwaltung, **When** er eine Rolle auswählt, **Then** sieht er alle fünf Rollen und für die gewählte Rolle jedes Recht aus dem Katalog mit gespeichertem Zustand.
2. **Given** ein Admin ändert mehrere Rechte, **When** er speichert, **Then** werden alle gültigen Änderungen gemeinsam übernommen, bestätigt und nach erneutem Öffnen angezeigt.
3. **Given** ein Admin verwirft eine Bearbeitung, **When** er die Rolle erneut öffnet, **Then** gelten die zuletzt gespeicherten Rechte.
4. **Given** zwei Admins bearbeiten dieselbe Rolle, **When** der zweite eine veraltete Auswahl speichert, **Then** wird sie ohne Überschreiben zurückgewiesen; er muss die aktuellen Rechte prüfen.
5. **Given** ein Nicht-Admin versucht Rollenrechte zu lesen oder zu ändern, auch über einen direkten Aufruf, **When** die Anfrage geprüft wird, **Then** wird sie abgelehnt und nichts verändert.
6. **Given** ein Admin wählt für eine Rolle ein anderes Recht ohne Lesen oder entfernt Lesen bei weiteren aktiven Rechten, **When** er speichert, **Then** wird die gesamte Änderung mit Hinweis auf die Abhängigkeit abgelehnt.

---

### User Story 2 - Mehrere Rollen zuweisen und gemeinsam nutzen (Priority: P1)

Als Admin möchte ich Nutzern mehrere Rollen zuweisen, damit sich deren erlaubte Funktionen ergänzen.

**Why this priority**: Rollenrechte müssen beim Nutzer zuverlässig wirksam werden.

**Independent Test**: Ein Nutzer erhält Student und University Staff mit unterschiedlichen Rechten. Funktionen beider Rollen sind erlaubt, alle übrigen gesperrt.

**Acceptance Scenarios**:

1. **Given** ein Admin bearbeitet einen Nutzer, **When** er dessen Rollen öffnet, **Then** sieht er die vollständige aktuelle Auswahl und alle fünf möglichen Rollen und kann jede nichtleere Kombination speichern.
2. **Given** Rolle A erlaubt Lesen und Reservieren und Rolle B erlaubt Lesen und Gebäude verwalten, **When** ein Nutzer beide Rollen besitzt, **Then** darf er lesen, reservieren und Gebäude verwalten; ein in beiden Rollen fehlendes Recht bleibt gesperrt.
3. **Given** eine Rolle oder ein Rollenrecht wurde geändert, **When** der betroffene Nutzer die nächste geschützte Aktion ausführt, **Then** gelten die aktuellen vereinigten Rechte ohne erneute Anmeldung.
4. **Given** ein Nutzer wird über einen unterstützten Weg neu angelegt, **When** die Anlage abgeschlossen ist, **Then** besitzt er ausschließlich Viewer und dessen aktuell gespeicherte Rechte; bei Einführung dieser Funktion ist das nur Lesen.
5. **Given** ein Admin versucht alle Rollen eines Nutzers zu entfernen, **When** er speichert, **Then** wird die Änderung erklärt und abgelehnt; die bisherige Auswahl bleibt bestehen.

---

### User Story 3 - Reservierungsaktionen unterscheiden (Priority: P1)

Als Betreiber möchte ich Lesen, Anlegen, Bearbeiten eigener und Bearbeiten fremder Reservierungen getrennt vergeben, damit Buchungsdaten und Aktionen nur für Berechtigte zugänglich sind.

**Why this priority**: Änderungen fremder Reservierungen und ihre Details benötigen eine ausdrückliche Freigabe.

**Independent Test**: Ein Nutzer mit Lesen und Reservieren kann einen Raum finden und buchen, aber ohne weiteres Recht keine Buchung ändern. Die gezielte Vergabe eines Bearbeitungsrechts erlaubt nur die passende Eigentumskategorie.

**Acceptance Scenarios**:

1. **Given** ein Nutzer besitzt nur Lesen, **When** er Räume, Suche, Status, Karten und Belegungszeiten öffnet, **Then** sieht er diese ohne fremde Personendaten, kann aber keine Reservierung anlegen oder ändern.
2. **Given** ein Nutzer besitzt Reservieren, **When** er eine gültige Buchung anlegt, **Then** wird sie seiner stabilen Identität zugeordnet; ohne dieses Recht wird die Anlage verweigert.
3. **Given** ein Nutzer besitzt Eigene Reservierungen bearbeiten, **When** er eine eigene Buchung im zulässigen Status ändert, aktiviert, abschließt, verfallen lässt oder storniert, **Then** ist dies erlaubt; an einer fremden Buchung ohne entsprechendes Recht nicht.
4. **Given** ein Nutzer besitzt Fremde Reservierungen bearbeiten, **When** er fremde Buchungsdetails liest oder die Buchung im zulässigen Status ändert, aktiviert, abschließt, verfallen lässt oder storniert, **Then** ist dies erlaubt.
5. **Given** ein Recht fehlt, **When** die Aktion direkt statt über die Oberfläche angefordert wird, **Then** wird sie ohne Datenänderung abgelehnt.

---

### User Story 4 - Verwaltungsbereiche gezielt freigeben (Priority: P2)

Als Admin möchte ich Gebäude, Räume, Karten und weitere Verwaltungsbereiche getrennt vergeben, damit Fachverantwortliche nur ihre Bereiche nutzen.

**Why this priority**: Die bisherige pauschale Admin-Freigabe verhindert gezielte Zuständigkeiten.

**Independent Test**: University Staff erhält Lesen und Räume verwalten. Ein betroffener Nutzer kann nach Aktivieren des Administrationsmodus Räume pflegen, aber keine Gebäude, Karten, Statistiken oder Rollen.

**Acceptance Scenarios**:

1. **Given** ein Nutzer hat mindestens ein Verwaltungsrecht, **When** er sich anmeldet, **Then** kann er den Administrationsmodus einschalten; darin erscheinen nur erlaubte Bereiche und Aktionen.
2. **Given** ein Nutzer hat kein Verwaltungsrecht und ist kein Admin, **When** er den Modus oder eine Verwaltungsadresse direkt aufruft, **Then** wird dies verweigert.
3. **Given** ein berechtigter Nutzer schaltet den Modus aus, **When** er eine erlaubte Verwaltungsaktion direkt anfordert, **Then** entscheidet weiterhin sein Rollenrecht über die Zulässigkeit.
4. **Given** ein Verwaltungsrecht wird entzogen, **When** der betroffene Nutzer die nächste Aktion ausführt, **Then** ist der Bereich nicht mehr angeboten und ein direkter Aufruf wird abgelehnt.

---

### User Story 5 - Admin-Zugang sichern (Priority: P1)

Als Betreiber möchte ich sicherstellen, dass immer ein Admin Rollen und Rollenrechte verwalten kann.

**Why this priority**: Der Verlust des letzten Admin-Zugangs wäre nicht über die Anwendung behebbar.

**Independent Test**: Mit genau einem Admin werden das Entfernen seiner Admin-Rolle und die Deaktivierung der geschützten Rollenverwaltung versucht. Beide Versuche scheitern; mit zwei Admins kann einer Admin abgeben.

**Acceptance Scenarios**:

1. **Given** genau ein Nutzer besitzt Admin, **When** Admin von ihm entfernt werden soll, auch durch ihn selbst oder bei konkurrierenden Änderungen, **Then** wird der Vorgang vollständig abgelehnt und sein Zugang bleibt bestehen.
2. **Given** mehrere Nutzer besitzen Admin, **When** einem davon Admin entzogen wird und er mindestens eine andere Rolle behält, **Then** gelingt die Änderung und mindestens ein Admin verbleibt.
3. **Given** ein Admin bearbeitet die Rechte von Admin, **When** er die geschützte Rollen- und Rollenrechteverwaltung deaktivieren will, **Then** ist dies unmöglich; andere Rechte von Admin bleiben änderbar.
4. **Given** ein Nicht-Admin besitzt alle konfigurierbaren Verwaltungsrechte, **When** er Rollen oder Rollenrechte aufruft, **Then** wird dies dennoch verweigert.

### Edge Cases

- Unbekannte Rollen oder Rechte und doppelte Rollenzuordnungen werden ohne Teiländerung zurückgewiesen.
- Eine Rolle mit einem anderen Recht, aber ohne Lesen, kann nicht gespeichert werden; auch das Entfernen von Lesen bei weiteren aktiven Rechten scheitert mit einer Erklärung.
- Veraltete Rollenzuordnungen werden wie in Feature 003 nicht über neuere Änderungen geschrieben; dasselbe gilt für Rollenrechte.
- Ein Admin darf seine eigene Admin-Rolle abgeben, wenn ein anderer Admin verbleibt. Der neue Zugang gilt bei der nächsten geschützten Aktion.
- Eine Reservierung ohne erfassten Ersteller ist für gewöhnliche Nutzer nicht „eigen“. Die Angabe „reserviert für“ begründet keine Eigentümerschaft.
- Gerätebedienung erfordert weiterhin eine eigene aktive Reservierung. Admin oder Fremdbuchungsrecht ersetzen diese Bedingung nicht.
- Ein bestehender Nutzer mit ausschließlich Viewer kann nach der Einführung eigene frühere Reservierungen lesen, aber erst nach Vergabe eines passenden Rechts wieder bearbeiten.
- Automatische Wartung, Anmeldung und öffentlich vorgesehene Systemfunktionen sind von der Rollenkonfiguration unabhängig; der manuelle Wartungslauf braucht sein eigenes Recht.
- Unbekannte oder keinem Recht zugeordnete geschützte Aktionen werden verweigert.

## Requirements *(mandatory)*

### Functional Requirements

**Rollen und Wirkung**

- **FR-001**: Genau Admin, University Staff, Student, Lecturer und Viewer MÜSSEN zuweisbar sein. Ein Nutzer MUSS mindestens eine und DARF mehrere unterschiedliche Rollen besitzen. Neue Nutzer MÜSSEN ausschließlich Viewer erhalten.
- **FR-002**: Admins MÜSSEN die vollständige Rollenzuordnung eines Nutzers sehen und jede gültige nichtleere Kombination speichern können. Nur aktuelle Admins DÜRFEN Zuordnungen lesen oder ändern. Änderungen an nicht vorhandenen Nutzern und veraltete Bearbeitungen MÜSSEN ohne Datenänderung scheitern.
- **FR-003**: Die wirksamen Rechte MÜSSEN die Vereinigung der Rechte aller aktuell zugewiesenen Rollen sein. Ein Recht gilt genau dann, wenn mindestens eine Rolle es gewährt; es gibt keine Verbots- oder Vorrangregel zwischen Rollen.
- **FR-004**: Rollen- und Rechteänderungen MÜSSEN dauerhaft gespeichert werden und spätestens bei der nächsten geschützten Aktion in einer bestehenden Sitzung wirken.

**Berechtigungskatalog**

- **FR-005**: Der Katalog MUSS die folgenden 14 Rechte für vorhandene Funktionen einzeln ausweisen. „Verwalten“ umfasst im jeweiligen Bereich alle dort vorhandenen Anlege-, Änderungs-, Deaktivierungs-, Reaktivierungs- und Löschaktionen.

| Bereich | Einzeln konfigurierbares Recht | Umfang |
| --- | --- | --- |
| Nutzeransicht | Lesen | Raumliste, Suche, Raumdetails und Status, Kartenansicht, Stammdaten zum Finden von Räumen, Belegungszeiten ohne fremde Personendaten, eigene Reservierungsübersicht und -details |
| Reservierung | Reservieren | Neue eigene Reservierung anlegen |
| Reservierung | Eigene Reservierungen bearbeiten | Eigene Reservierungen ändern, aktivieren, abschließen, verfallen lassen und stornieren |
| Reservierung | Fremde Reservierungen bearbeiten | Fremde Reservierungsdetails lesen und fremde Reservierungen ändern, aktivieren, abschließen, verfallen lassen und stornieren |
| Raumgeräte | Eigene aktive Raumgeräte steuern | Verfügbare Geräte bei eigener aktiver Reservierung anzeigen und bedienen |
| Verwaltung | Gebäude verwalten | Vorhandene Gebäudeaktionen |
| Verwaltung | Stockwerke verwalten | Vorhandene Stockwerkaktionen |
| Verwaltung | Ausstattungsarten verwalten | Vorhandene Ausstattungsartenaktionen |
| Verwaltung | Räume verwalten | Vorhandene Raumaktionen einschließlich Raumausstattung |
| Verwaltung | Karten verwalten | Karten hochladen, ersetzen und entfernen |
| Verwaltung | Raumpositionen verwalten | Räume auf Karten platzieren, verschieben und entfernen |
| Verwaltung | Verbindungen verwalten | Kartenverbindungen und ihre Punkte pflegen |
| Verwaltung | Statistiken lesen | Bestehende Verwaltungsstatistiken ansehen |
| Verwaltung | Reservierungswartung ausführen | Bestehenden manuellen Ablauf für überfällige Reservierungen auslösen |

- **FR-006**: Jedes Recht aus FR-005 MUSS je Rolle einzeln wählbar sein. Jedes andere konfigurierbare Recht setzt Lesen in derselben Rolle voraus: Eine Rolle mit einem weiteren Recht ohne Lesen DARF nicht gespeichert werden, und Lesen DARF nicht entfernt werden, solange ein anderes Recht aktiv ist. Die Ablehnung MUSS die Abhängigkeit erklären und alle gespeicherten Rechte unverändert lassen. Abgesehen von dieser Abhängigkeit eröffnet eine Freigabe nur die genannten Aktionen; bestehende Status-, Eigentums- und Eingaberegeln bleiben wirksam.
- **FR-007**: Beim erstmaligen Bereitstellen der Rollenrechte MUSS Viewer ausschließlich Lesen erhalten. University Staff, Student und Lecturer MÜSSEN Lesen, Reservieren, Eigene Reservierungen bearbeiten und Eigene aktive Raumgeräte steuern erhalten. Admin MUSS diese vier Rechte sowie Fremde Reservierungen bearbeiten und alle Verwaltungsrechte erhalten. Bestehende gültige Rollenzuordnungen bleiben erhalten.

**Rollenrechteverwaltung und Admin-Schutz**

- **FR-008**: Die Administrationsoberfläche MUSS je Rolle den vollständigen Katalog mit gespeicherten Ja/Nein-Zuständen zeigen, mehrere Änderungen ermöglichen und Erfolg, Fehler oder Konflikt verständlich melden.
- **FR-009**: Ausschließlich Nutzer mit aktueller Admin-Rolle DÜRFEN Rollenzuordnungen und Rollenrechte ansehen oder ändern, unabhängig von anderen Rechten oder vom Administrationsmodus. Direkte Aufrufe MÜSSEN ebenso geprüft werden.
- **FR-010**: Die Rollen- und Rollenrechteverwaltung MUSS unveränderlich an Admin gebunden sein; sie DARF nicht aus dem Admin-Zugang entfernt oder anderen Rollen erteilt werden. Die Rechteansicht MUSS diese geschützte Regel erkennen lassen.
- **FR-011**: Das Entfernen der Admin-Rolle vom letzten Admin MUSS bei Selbstbearbeitung und gleichzeitigen Änderungen abgelehnt werden, ohne bisherige Zuordnungen zu ändern, und der Grund MUSS genannt werden.
- **FR-012**: Das Speichern veralteter Rollenrechte MUSS ohne Überschreiben abgelehnt werden. Vor einem neuen Versuch MUSS der Admin die aktuelle Konfiguration prüfen können. Ungültige oder unbekannte Rechte MÜSSEN ohne Teiländerung zurückgewiesen werden.

**Durchsetzung und Oberfläche**

- **FR-013**: Jede geschützte Nutzeraktion MUSS anhand aktuell gespeicherter, vereinigter Rechte geprüft werden. Fehlende Rechte MÜSSEN auch bei direktem Aufruf ohne Datenänderung und ohne Preisgabe fremder Buchungsdetails verweigert werden. Nicht zugeordnete geschützte Aktionen MÜSSEN standardmäßig verweigert werden.
- **FR-014**: Die Oberfläche MUSS nur aktuell erlaubte Funktionen und Verwaltungsbereiche anbieten. Das Recht zum Bearbeiten einer Reservierung ersetzt weder das Recht zum Anlegen noch das Recht zum Bearbeiten der anderen Eigentumskategorie.
- **FR-015**: Der Administrationsmodus MUSS für Nutzer mit mindestens einem Verwaltungsrecht aus FR-005 oder mit Admin-Rolle einschaltbar sein. Er steuert nur die Sichtbarkeit; bei ausgeschaltetem Modus entscheidet weiterhin das Rollenrecht über direkte Aktionen. Nach Anmeldung ist der Modus aus; ohne entsprechendes Recht endet er.
- **FR-016**: Reservierungseigentum MUSS weiterhin auf der stabilen Identität des Erstellers beruhen. Fremde Belegungszeiten DÜRFEN keine Personendaten preisgeben. Raumgeräte DÜRFEN nur mit gesondertem Geräterecht und einer eigenen aktiven Reservierung gesteuert werden.

### Key Entities

- **Nutzer**: Identifizierbares Konto mit mindestens einer der fünf Rollen.
- **Rolle**: Fest vorgegebene Kategorie mit konfigurierbaren Funktionsrechten; Admin besitzt zusätzlich die unveränderliche Rollenverwaltungsbefugnis.
- **Berechtigung**: Benannte Erlaubnis für genau den in FR-005 beschriebenen Funktionsumfang.
- **Rollenzuordnung**: Eindeutige Verbindung zwischen Nutzer und Rolle.
- **Rollenrecht**: Gespeicherte Entscheidung, ob eine Rolle eine Berechtigung gewährt.
- **Wirksame Rechte**: Vereinigung der Rechte aller Rollen eines Nutzers zum Zeitpunkt einer Aktion.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein Admin kann einem bekannten Nutzer Rollen zuweisen und bei einer bekannten Rolle zwei Rechte ändern sowie beide Ergebnisse nach erneutem Öffnen in höchstens drei Minuten prüfen.
- **SC-002**: Für jedes der 14 konfigurierbaren Rechte wird jeweils eine erlaubte und eine verweigerte Aktion korrekt entschieden; verweigerte Änderungen verändern keine Daten.
- **SC-003**: Für alle 31 nichtleeren Kombinationen der fünf Rollen entsprechen die wirksamen Rechte genau der Vereinigung ihrer Rollenrechte.
- **SC-004**: In 100 % der geprüften Anlagewege erhält ein neuer Nutzer ausschließlich Viewer; bei der Einführung kann er damit lesen, aber nicht buchen. Rollen- und Rechteänderungen bleiben nach erneuter Anmeldung erhalten.
- **SC-005**: In 100 % der geprüften sichtbaren und direkten Zugriffsversuche ohne Admin-Rolle bleiben Rollenzuordnungen und Rollenrechte unlesbar und unverändert.
- **SC-006**: In 100 % der geprüften Einzel- und Konkurrenzfälle bleibt mindestens ein Nutzer mit Admin-Rolle und unverlierbarem Verwaltungszugang erhalten.
- **SC-007**: Mindestens 90 % der Akzeptanzteilnehmer geben genau einen Verwaltungsbereich für eine Rolle beim ersten Versuch ohne Hilfe frei; anschließend sieht der Testnutzer nur erlaubte Verwaltungsbereiche.
- **SC-008**: In 100 % der geprüften bestehenden Sitzungen greifen Rollen- und Rechteänderungen bei der nächsten geschützten Aktion ohne erneute Anmeldung.

## Assumptions

- Dieses Feature erweitert 003 um Rollenrechte und ersetzt aus 013 die pauschale Bindung aller Verwaltungsfunktionen und der Modusfreigabe an Admin. Die unveränderliche Admin-Befugnis zur Rollen- und Rollenrechteverwaltung bleibt bestehen.
- Rollen und Rechte gelten anwendungsweit, nicht pro Gebäude, Raum, Organisation oder Zeitraum. Eigene Rollen und individuelle Nutzerrechte gehören nicht zum Umfang.
- Jedes andere konfigurierbare Recht setzt innerhalb derselben Rolle Lesen voraus. Die Rechteverwaltung lehnt unzulässige Kombinationen ab; es entstehen keine impliziten Freigaben.
- Die anfängliche Einschränkung von Viewer auf Lesen gilt auch für bestehende Nutzer, die ausschließlich Viewer besitzen. Admins können der Rolle Viewer oder einzelnen Nutzern über zusätzliche Rollen später Buchungsrechte geben.
- Der bisherige Zugang zu eigenen Reservierungsdetails ist Teil von Lesen. Fremde Reservierungen bearbeiten beinhaltet den dafür nötigen Detailblick; allgemeine Belegungsansichten zeigen nur Zeitfenster.
- Automatische Systemaufgaben handeln nicht als Nutzer. Anmeldung und öffentlich vorgesehene Systemfunktionen sind keine konfigurierbaren Rollenrechte.
- Ein initiales Admin-Konto und authentifizierte Nutzeridentitäten werden wie in Feature 003 und 013 vorausgesetzt.
