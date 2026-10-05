# Feature Specification: Optionale Buchungsbestätigung per E-Mail

**Feature Branch**: `010-mail-notification`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "Das System soll bei erfolgreicher Raumbuchung dem buchenden Benutzer eine Bestätigungsemail mit Start und Endzeit der Buchung und dem gebuchten Raum übermitteln. Im Booking Form bestimmt die Checkbox `Email Notification`, ob eine solche Benachrichtigung versendet wird."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - E-Mail-Bestätigung bewusst anfordern (Priority: P1)

Ein authentifizierter Benutzer möchte im Buchungsformular selbst entscheiden, ob er nach einer erfolgreichen Raumbuchung eine E-Mail-Bestätigung erhält. Dazu aktiviert er vor `Confirm Reservation` die Checkbox `Email Notification`. Ohne diese Auswahl wird die Buchung normal angelegt, aber keine gesonderte Bestätigungsmail versendet.

**Why this priority**: Die ausdrückliche Auswahl schützt Benutzer vor unerwünschten Nachrichten und macht den E-Mail-Versand zu einer bewussten Entscheidung pro Buchung.

**Independent Test**: Derselbe Benutzer führt zwei gültige Buchungen durch. Bei der ersten aktiviert er `Email Notification` und erhält genau eine passende Bestätigung. Bei der zweiten lässt er die Checkbox deaktiviert; die Reservierung wird erfolgreich angelegt und es wird keine Bestätigungsmail ausgelöst.

**Acceptance Scenarios**:

1. **Given** ein Benutzer öffnet das Buchungsformular, **When** das Formular angezeigt wird, **Then** sieht er vor der Aktion `Confirm Reservation` eine standardmäßig nicht ausgewählte Checkbox mit der Bezeichnung `Email Notification`.
2. **Given** ein authentifizierter Benutzer mit gültiger E-Mail-Adresse hat `Email Notification` ausgewählt und der E-Mail-Dienst ist verfügbar, **When** er eine gültige Raumbuchung erfolgreich abschließt, **Then** nimmt der E-Mail-Dienst genau eine Bestätigungsmail für diese Reservierung zur Zustellung an.
3. **Given** ein authentifizierter Benutzer hat `Email Notification` nicht ausgewählt, **When** er eine gültige Raumbuchung erfolgreich abschließt, **Then** wird die Reservierung angelegt und keine gesonderte Bestätigungsmail ausgelöst.
4. **Given** eine angeforderte Bestätigung für eine erfolgreich angelegte Reservierung, **When** die Bestätigungsmail erstellt wird, **Then** enthält sie die eindeutige Bezeichnung des gebuchten Raums sowie Start- und Enddatum mit Uhrzeit.
5. **Given** eine angeforderte Bestätigung mit Start- und Endzeit, **When** der Benutzer die Bestätigungsmail liest, **Then** sind Datum, Uhrzeit und geltende Zeitzone eindeutig erkennbar.
6. **Given** ein Buchungsversuch wird wegen eines Konflikts oder ungültiger Eingaben abgelehnt, **When** keine Reservierung angelegt wird, **Then** wird unabhängig von der Checkbox-Auswahl keine Buchungsbestätigung versendet.
7. **Given** eine einzelne erfolgreich angelegte Reservierung mit angeforderter Bestätigung, **When** die Buchungsantwort wiederholt abgerufen oder die Oberfläche neu geladen wird, **Then** wird keine weitere Bestätigungsmail für dieselbe Buchung ausgelöst.

---

### User Story 2 - Buchung bei Versandproblemen beibehalten (Priority: P2)

Ein Benutzer möchte darauf vertrauen können, dass seine erfolgreich angelegte Reservierung bestehen bleibt, auch wenn er eine E-Mail-Bestätigung angefordert hat und diese nicht zugestellt werden kann.

**Why this priority**: Der optionale E-Mail-Versand darf den zentralen Buchungsvorgang nicht nachträglich ungültig machen oder zu Doppelbuchungen durch erneute Versuche führen.

**Independent Test**: Ein Benutzer aktiviert `Email Notification` und legt bei nicht verfügbarem E-Mail-Versand eine gültige Reservierung an. Die Reservierung bleibt erfolgreich bestehen und der fehlgeschlagene Benachrichtigungsversand ist betrieblich erkennbar.

**Acceptance Scenarios**:

1. **Given** eine erfolgreich angelegte Reservierung mit angeforderter Bestätigung und ein Versandproblem, **When** die Bestätigung nicht zugestellt werden kann, **Then** bleibt die Reservierung unverändert gültig.
2. **Given** eine nicht zustellbare angeforderte Bestätigung, **When** der Versand scheitert, **Then** kann der fehlgeschlagene Benachrichtigungsstatus für Support und Betrieb festgestellt werden, ohne vertrauliche Buchungs- oder Benutzerdaten offenzulegen.

### Edge Cases

- Ändert der Benutzer die Checkbox unmittelbar vor `Confirm Reservation`, gilt der beim Absenden sichtbare Auswahlzustand.
- Hat das Benutzerkonto trotz ausgewählter Checkbox keine nutzbare E-Mail-Adresse, wird die Buchung nicht nachträglich aufgehoben; der Versand wird als fehlgeschlagen behandelt.
- Eine Checkbox-Auswahl gilt nur für die aktuelle Buchung und wird nicht automatisch auf eine spätere Buchung übertragen.
- Enthält die Raumbezeichnung Sonderzeichen oder Umlaute, erscheinen diese lesbar und unverändert in der Bestätigung.
- Liegen Start und Ende an unterschiedlichen Kalendertagen, werden Datum und Uhrzeit für beide Zeitpunkte vollständig angegeben.
- Erfolgt eine Buchung rund um eine Zeitumstellung, werden die Zeitpunkte mit einer eindeutigen Zeitzonenangabe dargestellt.
- Mehrere unterschiedliche erfolgreiche Buchungen desselben Benutzers mit jeweils ausgewählter Checkbox erzeugen jeweils genau eine eigene Bestätigung.
- Ein abgelehnter, abgebrochener oder technisch nicht abgeschlossener Buchungsversuch erzeugt auch bei ausgewählter Checkbox keine Bestätigung.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Das Buchungsformular MUSS vor der Aktion `Confirm Reservation` eine Checkbox mit der sichtbaren Bezeichnung `Email Notification` anzeigen.
- **FR-002**: Die Checkbox `Email Notification` MUSS beim Öffnen eines neuen Buchungsformulars standardmäßig nicht ausgewählt sein.
- **FR-003**: Der Benutzer MUSS die Checkbox vor dem Absenden der Buchung auswählen oder wieder abwählen können.
- **FR-004**: Eine nicht ausgewählte Checkbox DARF die erfolgreiche Anlage einer ansonsten gültigen Reservierung nicht verhindern.
- **FR-005**: Das System MUSS nach einer erfolgreich und dauerhaft angelegten Raumbuchung genau dann eine Bestätigungsmail für den buchenden Benutzer auslösen, wenn `Email Notification` beim Absenden ausgewählt war.
- **FR-006**: Das System DARF für eine erfolgreiche Buchung keine gesonderte Bestätigungsmail auslösen, wenn `Email Notification` beim Absenden nicht ausgewählt war.
- **FR-007**: Das System MUSS eine angeforderte Bestätigung an die im Benutzerkonto des authentifizierten Buchenden hinterlegte E-Mail-Adresse adressieren.
- **FR-008**: Die Bestätigungsmail MUSS die eindeutige Bezeichnung des gebuchten Raums enthalten.
- **FR-009**: Die Bestätigungsmail MUSS Start und Ende der Buchung jeweils mit Datum und Uhrzeit enthalten.
- **FR-010**: Die Bestätigungsmail MUSS die für die dargestellten Buchungszeiten geltende Zeitzone eindeutig benennen.
- **FR-011**: Die in der Bestätigung dargestellten Daten MÜSSEN mit der dauerhaft angelegten Reservierung übereinstimmen.
- **FR-012**: Das System DARF für abgelehnte, abgebrochene oder nicht dauerhaft angelegte Buchungsversuche keine Bestätigungsmail versenden, auch wenn `Email Notification` ausgewählt war.
- **FR-013**: Das System MUSS je erfolgreich angelegter Reservierung mit angeforderter Bestätigung höchstens eine Buchungsbestätigung erzeugen, auch wenn Antworten erneut abgerufen oder Seiten neu geladen werden.
- **FR-014**: Ein fehlgeschlagener E-Mail-Versand DARF die erfolgreich angelegte Reservierung weder löschen noch stornieren oder verändern; der Fehlschlag MUSS betrieblich erkennbar sein, ohne E-Mail-Adresse oder weitere personenbezogene Buchungsdaten in Diagnoseinformationen offenzulegen.
- **FR-015**: Die Bestätigungsmail MUSS einen verständlichen Betreff enthalten, aus dem hervorgeht, dass die Raumbuchung bestätigt wurde.
- **FR-016**: Dieses Feature DARF keine zusätzlichen E-Mails bei Änderung, Stornierung, Aktivierung, Abschluss oder Ablauf einer bestehenden Reservierung erzeugen.

### Key Entities *(include if feature involves data)*

- **Reservierung**: Die erfolgreich angelegte Raumbuchung mit eindeutiger Identität, gebuchtem Raum, Startzeit, Endzeit, Status und dem Benutzerkonto des Buchenden.
- **Benachrichtigungswahl**: Die ausdrückliche Auswahl des Benutzers für die aktuelle Buchung, ob nach erfolgreichem Abschluss eine E-Mail-Bestätigung ausgelöst werden soll.
- **Benutzerkonto**: Der authentifizierte Buchende mit einer hinterlegten E-Mail-Adresse, an welche eine angeforderte Bestätigung adressiert wird.
- **Buchungsbestätigung**: Die nur bei ausdrücklicher Auswahl erzeugte, einer Reservierung eindeutig zugeordnete Benachrichtigung mit Empfänger, Raumbezeichnung, Start, Ende und Versandstatus.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Bei 100 % der neu geöffneten Buchungsformulare ist `Email Notification` sichtbar und zunächst nicht ausgewählt.
- **SC-002**: 100 % der erfolgreichen Buchungen ohne ausgewählte Checkbox erzeugen keine Bestätigungsmail.
- **SC-003**: Bei mindestens 95 % der erfolgreichen Buchungen mit ausgewählter Checkbox wird die Bestätigung innerhalb von zwei Minuten nach Abschluss der Buchung vom E-Mail-Dienst zur Zustellung angenommen.
- **SC-004**: 100 % der geprüften Bestätigungsmails enthalten Raumbezeichnung, Startdatum und -uhrzeit, Enddatum und -uhrzeit sowie eine eindeutige Zeitzonenangabe, jeweils übereinstimmend mit der Reservierung.
- **SC-005**: 100 % der abgelehnten oder nicht abgeschlossenen Buchungsversuche erzeugen unabhängig von der Checkbox-Auswahl keine Bestätigungsmail.
- **SC-006**: Pro erfolgreich angelegter Reservierung mit ausgewählter Checkbox wird höchstens eine Bestätigungsmail erzeugt.
- **SC-007**: In 100 % der simulierten Versandfehler bleibt die zugehörige Reservierung unverändert gültig und der Fehlschlag ist für den Betrieb feststellbar.
- **SC-008**: In einem moderierten Abnahmetest mit mindestens 10 repräsentativen Benutzern können mindestens 9 Teilnehmer innerhalb von 15 Sekunden korrekt erklären, dass eine E-Mail nur bei ausgewählter Checkbox versendet wird.
- **SC-009**: In einem moderierten Abnahmetest mit mindestens 10 repräsentativen Benutzern können mindestens 9 Teilnehmer ohne zusätzliche Erklärung den gebuchten Raum sowie Start und Ende der Buchung innerhalb von 30 Sekunden korrekt aus der Bestätigungsmail ablesen.

## Assumptions

- `Email Notification` ist ein freiwilliges Opt-in. Benutzer dürfen `Confirm Reservation` auch mit nicht ausgewählter Checkbox ausführen.
- Die Checkbox ist bei jedem neu geöffneten Buchungsformular standardmäßig deaktiviert; ihre Auswahl wird weder als Benutzerpräferenz gespeichert noch auf spätere Buchungen übertragen.
- Eine Buchung gilt als erfolgreich, sobald die Reservierung dauerhaft im initialen Buchungsstatus angelegt wurde.
- Raumbuchungen können nur von authentifizierten Benutzern vorgenommen werden; deren Benutzerkonto enthält grundsätzlich eine für Benachrichtigungen vorgesehene E-Mail-Adresse.
- Start- und Endzeit werden in der für die Anwendung maßgeblichen lokalen Zeitzone `Europe/Berlin` dargestellt und ausdrücklich als solche gekennzeichnet.
- Die Bestätigung wird in deutscher Sprache versendet; weitere Sprachen und benutzerspezifische Sprachpräferenzen sind nicht Teil dieses Features.
- Die vorhandene Raumbezeichnung ist innerhalb des Nutzungskontexts ausreichend eindeutig. Verfügbare Standortangaben dürfen ergänzend dargestellt werden.
- Abhängigkeiten dieses Features sind das bestehende Buchungsformular, die erfolgreiche Reservierungserstellung, die Zuordnung einer Reservierung zum authentifizierten Benutzer und dessen hinterlegte E-Mail-Adresse.
- SC-003 wird mit 100 erfolgreichen Testbuchungen mit ausgewählter Checkbox geprüft, davon höchstens 20 gleichzeitig. Mindestens 95 Bestätigungen müssen innerhalb von 120 Sekunden nach erfolgreichem Abschluss ihrer jeweiligen Buchung vom E-Mail-Dienst angenommen werden.
- Benachrichtigungen über Änderungen, Stornierungen, Aktivierung, Abschluss oder Ablauf einer Reservierung sowie Kalenderdateien sind ausdrücklich außerhalb des Umfangs dieses Features.
