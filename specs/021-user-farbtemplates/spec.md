# Feature Specification: Persönliche Farbtemplates

**Feature Branch**: `021-user-farbtemplates`
**Created**: 2026-10-19
**Status**: Draft
**Input**: User description: "Es soll die Möglichkeit geben verschiedene Farbtemplates für die Oberfläche auswählen zu können. Ein erstellter User soll beim ersten login die möglichkeit haben aus verschiedenen vordefinierten farbtemplates auszuwählen (zb. darkmode, light etc) Weiters soll der User die Möglickheit haben in seinen Einstellungen das Farbschema auszuwählen."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Farbschema beim ersten Login auswählen (Priority: P1)

Als neu angelegter Benutzer möchte ich nach meiner ersten erfolgreichen Anmeldung zwischen vordefinierten Farbschemata wählen, damit die Oberfläche von Anfang an angenehm lesbar ist.

**Why this priority**: Die erste Auswahl ist der ausdrücklich gewünschte Einstieg in die Personalisierung.

**Independent Test**: Einen neu angelegten Benutzer erstmals anmelden, die angebotenen Schemata ansehen, eines wählen und anschließend mehrere geschützte Seiten öffnen. Die gewählte Darstellung bleibt erhalten.

**Acceptance Scenarios**:

1. **Given** ein neu angelegter Benutzer ohne gespeicherte Farbpräferenz, **When** er sich erstmals erfolgreich anmeldet, **Then** wird ihm vor der regulären Startseite eine Auswahl mit mindestens „Hell“ und „Dunkel“ sowie verständlichen Vorschauen angeboten.
2. **Given** die Erstauswahl, **When** der Benutzer „Dunkel“ auswählt und bestätigt, **Then** erscheint die reguläre Oberfläche im dunklen Farbschema und die Auswahl wird für sein Konto gespeichert.
3. **Given** die Erstauswahl, **When** der Benutzer „Hell“ auswählt und bestätigt, **Then** erscheint die reguläre Oberfläche im hellen Farbschema und die Auswahl wird für sein Konto gespeichert.
4. **Given** die Erstauswahl, **When** der Benutzer sie mit „Später auswählen“ überspringt, **Then** gelangt er mit dem hellen Standardschema in die Anwendung; der Hinweis erscheint bei späteren Anmeldungen nicht erneut und die Auswahl bleibt in den persönlichen Einstellungen möglich.
5. **Given** ein Benutzer mit bereits abgeschlossener Erstauswahl oder mit bestehendem Konto vor Einführung dieser Funktion, **When** er sich anmeldet, **Then** gelangt er ohne erneute Erstauswahl in die Anwendung.

---

### User Story 2 - Farbschema in persönlichen Einstellungen ändern (Priority: P1)

Als angemeldeter Benutzer möchte ich mein Farbschema jederzeit in meinen persönlichen Einstellungen ändern, damit ich die Darstellung an meine Vorlieben oder Lichtverhältnisse anpassen kann.

**Why this priority**: Die dauerhafte Änderungsmöglichkeit ist der zweite Kernwunsch und muss für alle Benutzerrollen erreichbar sein.

**Independent Test**: Mit einem beliebigen bestehenden Benutzer anmelden, die persönlichen Einstellungen öffnen, zwischen „Hell“ und „Dunkel“ wechseln und die Anwendung neu laden. Das gewählte Schema bleibt aktiv.

**Acceptance Scenarios**:

1. **Given** ein angemeldeter Benutzer, unabhängig von seiner Rolle, **When** er die Navigation öffnet, **Then** findet er einen Zugang zu seinen persönlichen Einstellungen mit der Farbschema-Auswahl.
2. **Given** die persönlichen Einstellungen, **When** sie geöffnet werden, **Then** ist das aktuell wirksame Farbschema eindeutig gekennzeichnet.
3. **Given** die persönlichen Einstellungen, **When** der Benutzer ein anderes Farbschema wählt und die Änderung erfolgreich speichert, **Then** wird es ohne erneute Anmeldung auf allen regulären Anwendungsseiten wirksam.
4. **Given** ein gespeichertes Farbschema, **When** derselbe Benutzer die Seite neu lädt oder sich auf einem anderen Gerät anmeldet, **Then** wird wieder sein gespeichertes Schema angezeigt.
5. **Given** zwei Benutzer mit unterschiedlichen Präferenzen, **When** sie sich nacheinander am selben Gerät anmelden, **Then** sieht jeder ausschließlich sein eigenes Farbschema.

---

### User Story 3 - Auswahl sicher und verständlich nutzen (Priority: P2)

Als Benutzer möchte ich die angebotenen Farbschemata gut unterscheiden und auch bei einem Speicherfehler wissen, welche Auswahl tatsächlich gilt, damit ich die Anwendung zuverlässig bedienen kann.

**Why this priority**: Die Auswahl ist nur nützlich, wenn sie zugänglich ist und fehlgeschlagene Änderungen nicht als gespeichert erscheinen.

**Independent Test**: Beide Schemata mit Tastatur und Vorlesehilfe auswählen, zentrale Seiten prüfen und einen Speicherfehler auslösen. Auswahlzustand, Lesbarkeit und Fehlerrückmeldung sind eindeutig.

**Acceptance Scenarios**:

1. **Given** die Erstauswahl oder persönliche Einstellungen, **When** die Schemata mit Tastatur oder Vorlesehilfe bedient werden, **Then** sind Namen, Auswahlzustand und Bestätigung verständlich und vollständig zugänglich.
2. **Given** ein helles oder dunkles Schema, **When** die reguläre Oberfläche angezeigt wird, **Then** bleiben Texte, Steuerelemente, Fokusmarkierungen, Fehlermeldungen und Statusinformationen gut lesbar; Status wird nicht allein durch Farbe vermittelt.
3. **Given** eine Änderung des Farbschemas, **When** das Speichern fehlschlägt, **Then** wird eine verständliche Fehlermeldung angezeigt und das zuletzt erfolgreich gespeicherte Schema bleibt als gültige Auswahl erkennbar.

### Edge Cases

- Bestehende Konten ohne Farbpräferenz verwenden „Hell“ und erhalten keinen nachträglichen Erstlogin-Hinweis; sie können in den persönlichen Einstellungen wechseln.
- Ein neues Konto, das die Erstauswahl übersprungen hat, verwendet „Hell“ und sieht den Hinweis bei späteren Anmeldungen nicht erneut.
- Wird ein Benutzerkonto von zwei Sitzungen aus geändert, gilt nach dem nächsten Laden in beiden Sitzungen die zuletzt erfolgreich gespeicherte Auswahl.
- Ein unbekanntes oder nicht mehr angebotenes gespeichertes Farbschema führt zum hellen Standardschema, ohne die Benutzung zu blockieren; in den Einstellungen wird „Hell“ als wirksame Auswahl angezeigt.
- Auf schmalen Bildschirmen und bei vergrößerter Darstellung bleiben Auswahl, Vorschau und Bestätigung ohne horizontales Scrollen bedienbar.
- Nicht angemeldete Seiten und eigenständige Raumanzeigen verwenden weiterhin das bestehende Erscheinungsbild, weil noch keine persönliche Präferenz zugeordnet werden kann bzw. die Anzeige nicht an ein persönliches Konto gebunden ist.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Die Anwendung MUST mindestens die vordefinierten Farbschemata „Hell“ und „Dunkel“ zur Auswahl anbieten; „Hell“ MUST das Standardschema sein, wenn keine gültige Präferenz vorliegt.
- **FR-002**: Ein neu angelegter Benutzer ohne Farbpräferenz MUST nach seiner ersten erfolgreichen Anmeldung vor der regulären Startseite einmalig eine verständliche Farbschema-Auswahl mit benannten Vorschauen sehen.
- **FR-003**: In der Erstauswahl MUST der Benutzer ein angebotenes Schema bestätigen oder „Später auswählen“ wählen können. Beide Entscheidungen MUST den Erstlogin-Hinweis abschließen; Überspringen MUST „Hell“ als wirksames Standardschema verwenden.
- **FR-004**: Bereits vor Einführung dieser Funktion bestehende Konten MUST ohne nachträglichen Erstlogin-Hinweis starten und „Hell“ verwenden, solange sie keine eigene Auswahl gespeichert haben.
- **FR-005**: Jeder angemeldete Benutzer MUST unabhängig von Rolle oder Administrationsmodus persönliche Einstellungen mit einer Farbschema-Auswahl erreichen können. Diese MUST von administrativen Systemeinstellungen klar unterscheidbar sein.
- **FR-006**: Die persönlichen Einstellungen MUST das aktuell wirksame Farbschema anzeigen und einen Wechsel zu jedem angebotenen Schema ermöglichen.
- **FR-007**: Eine erfolgreich gespeicherte Auswahl MUST unmittelbar auf allen regulären Anwendungsseiten für diesen Benutzer gelten und nach Seitenwechsel, Neuladen, Abmeldung und erneuter Anmeldung sowie auf weiteren Geräten erhalten bleiben.
- **FR-008**: Farbpräferenzen MUST kontobezogen sein; bei einem Kontowechsel auf demselben Gerät MUST die Präferenz des neu angemeldeten Benutzers gelten.
- **FR-009**: Schlägt das Speichern fehl, MUST die Anwendung dies verständlich melden und die zuletzt erfolgreich gespeicherte Auswahl als gültigen Stand ausweisen. Der Benutzer MUST erneut speichern können.
- **FR-010**: Beide Farbschemata MUST für reguläre Seiten ausreichende Kontraste für Text, Bedienfelder und sichtbaren Tastaturfokus nach WCAG 2.1 AA bieten. Bedeutungstragende Zustände MUST zusätzlich durch Text, Form oder Muster erkennbar bleiben.
- **FR-011**: Auswahl und Vorschau MUST per Tastatur und Vorlesehilfe zugänglich sein; die gewählte Option MUST eindeutig benannt und erkennbar sein.
- **FR-012**: Ein unbekanntes oder entfallenes gespeichertes Schema MUST auf „Hell“ zurückfallen, ohne den Zugang zur Anwendung zu verhindern.
- **FR-013**: Nicht angemeldete Seiten und eigenständige Raumanzeigen MUST das vorhandene Erscheinungsbild beibehalten; persönliche Farbschemata gelten für die reguläre angemeldete Oberfläche.

### Key Entities *(include if feature involves data)*

- **Farbschema**: Vordefinierte, benannte Auswahl für die visuelle Darstellung; mindestens „Hell“ und „Dunkel“.
- **Persönliche Farbpräferenz**: Das zuletzt erfolgreich gewählte Farbschema eines Benutzerkontos oder das Fehlen einer eigenen Auswahl.
- **Erstlogin-Status**: Kennzeichnung, ob einem neu angelegten Benutzer die einmalige Farbauswahl bereits angeboten und von ihm abgeschlossen wurde.
- **Benutzerkonto**: Bereits vorhandene persönliche Identität, zu der Farbpräferenz und Erstlogin-Status gehören.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In Akzeptanztests können mindestens 90 % der Testpersonen beim ersten Login ohne Anleitung innerhalb von 60 Sekunden ein Farbschema auswählen oder die Auswahl überspringen und die Startseite erreichen.
- **SC-002**: In 100 % der geprüften neuen Konten erscheint die Erstauswahl genau einmal; in 100 % der geprüften bestehenden Konten erscheint sie nicht.
- **SC-003**: In 100 % der geprüften Benutzerrollen ist die persönliche Farbschema-Auswahl innerhalb von drei Navigationsschritten nach der Anmeldung erreichbar.
- **SC-004**: In 100 % der geprüften Wechsel bleibt das gespeicherte Schema nach Neuladen, erneuter Anmeldung und Anmeldung auf einem zweiten Gerät erhalten; bei Kontowechseln erscheint jeweils die richtige persönliche Auswahl.
- **SC-005**: In 100 % der geprüften regulären Ansichten erfüllen beide Schemata WCAG 2.1 AA für Text, Bedienelemente und Fokus; alle geprüften Statusinformationen sind ohne Farbwahrnehmung verständlich.
- **SC-006**: In 100 % der geprüften Speicherfehler erhalten Benutzer eine verständliche Rückmeldung und sehen ihre zuletzt erfolgreich gespeicherte Auswahl als gültigen Stand.

## Assumptions

- Konten werden bereits durch bestehende Abläufe angelegt; diese Funktion umfasst weder Kontoanlage noch Anmeldung selbst.
- „Verschiedene Farbtemplates“ bedeutet für die erste Version die festen Optionen „Hell“ und „Dunkel“. Eigene Farben, zusätzliche Themes und ein automatischer Wechsel nach System- oder Tageszeit sind nicht Teil dieser Spezifikation.
- Die gegenwärtige helle Gestaltung „Papier & Tinte“ dient als helles Standardschema. Das dunkle Schema soll ihre klare, zurückhaltende Gestaltung sinngemäß fortsetzen.
- Der Erstlogin-Hinweis ist freiwillig und wird nach einer Auswahl oder „Später auswählen“ nicht wiederholt. Ein Benutzer ohne eigene Auswahl kann jederzeit über die Einstellungen wechseln.
- Persönliche Einstellungen sind für alle angemeldeten Benutzer zugänglich. Die bestehenden administrativen Einstellungen bleiben fachlich getrennt.
- Die bestehende Benutzeridentität und Anmeldung sind Voraussetzung für die kontobezogene Speicherung. Nicht angemeldete Seiten und eigenständige Raumanzeigen erhalten keine persönliche Farbpräferenz.
