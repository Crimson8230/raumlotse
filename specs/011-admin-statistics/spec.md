# Feature Specification: Admin-Statistikbereich

**Feature Branch**: `011-admin-statistics`

**Created**: 2026-10-06

**Status**: Draft

**Input**: User description: "Ein Neuer Adminbereich muss in der App erstellt werden in dem mit diesem Feature der Statistikbereich erstellt wird. Es sollen Statistiken bereitgestellt werden die die Auslastung der einzelnen Räume darstellt. Weiters sollen Statistiken nach Raumfeatures bereitgestellt werden damit man ablesen kann welche Ausstattung am meisten benutzt wird. Es sollen Statistiken dargestellt werden wieviele Personen im Durchschnitt pro Buchung einen Raum nutzen. Es soll auch eine Statistik geben aus der klar wird wie oft Räume storniert werden. Alle hier als Statistiken genannten Metriken können durchaus auf einer übersichtlichen Seite dargestellt werden sofern dies nicht die Übersichtlichkeit behindert."

## Clarifications

### Session 2026-10-06

- Q: Wie soll die Raumauslastung als Prozentwert berechnet werden? → A: Option A – Die Auslastung bezieht sich auf die gesamte Kalenderzeit des ausgewählten Zeitraums.
- Q: Soll die Stornierungsstatistik nur stornierte Buchungen zählen oder zusätzlich anzeigen, wie viele unterschiedliche Räume mindestens einmal betroffen waren? → A: Option A – Es werden stornierte Buchungen und die Stornierungsquote dargestellt.
- Q: Soll der Personendurchschnitt auf der bei der Buchung angegebenen Teilnehmerzahl basieren? → A: Option A – Der Durchschnitt basiert auf der bei der Buchung angegebenen Teilnehmerzahl.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Statistikübersicht als Administrator öffnen (Priority: P1)

Ein authentifizierter Administrator möchte einen eigenen Statistikbereich öffnen, um die Nutzung der Raumressourcen zentral zu beurteilen. Die Seite soll die wichtigsten Kennzahlen auf einen Blick zeigen und den betrachteten Zeitraum eindeutig ausweisen.

**Why this priority**: Ohne einen geschützten und verständlichen Einstiegspunkt können die nachfolgenden Statistiken nicht verlässlich für die Raumplanung genutzt werden.

**Independent Test**: Ein Administrator öffnet den Adminbereich und kann die Statistikübersicht mit einem definierten Zeitraum laden. Ein nicht administrativer Benutzer versucht denselben Bereich aufzurufen und erhält keinen Zugriff.

**Acceptance Scenarios**:

1. **Given** ein authentifizierter Administrator, **When** er den Adminbereich öffnet, **Then** kann er den Statistikbereich auswählen und die Übersichtsseite mit dem Standardzeitraum sehen.
2. **Given** ein nicht administrativer oder nicht authentifizierter Benutzer, **When** er den Statistikbereich direkt aufruft, **Then** werden keine Statistikdaten angezeigt und der Zugriff wird entsprechend dem bestehenden Zugriffskonzept verweigert.
3. **Given** die Statistikübersicht ist geöffnet, **When** der Administrator den Zeitraum ändert, **Then** werden alle dargestellten Kennzahlen für denselben ausgewählten Zeitraum aktualisiert.
4. **Given** ein Zeitraum ohne Buchungen, **When** der Administrator die Statistikübersicht lädt, **Then** zeigt die Seite verständliche Null- beziehungsweise Leerwerte und keine irreführenden Prozent- oder Durchschnittswerte an.

### User Story 2 - Raumauslastung vergleichen (Priority: P1)

Ein Administrator möchte erkennen, welche Räume stark oder wenig genutzt werden, um Raumkapazitäten besser zu planen und mögliche Fehlverteilungen zu identifizieren.

**Why this priority**: Die Auslastung einzelner Räume ist die zentrale Entscheidungsgrundlage für die Belegung und Weiterentwicklung des Raumangebots.

**Independent Test**: Für einen Zeitraum mit bekannten Buchungen wird die Raumstatistik geöffnet. Die Anzeige enthält je Raum die Buchungsanzahl und die gebuchte Zeit beziehungsweise Auslastung und ordnet die Räume nachvollziehbar nach der Auslastung.

**Acceptance Scenarios**:

1. **Given** ein Zeitraum mit Buchungen für mehrere Räume, **When** der Administrator die Raumauslastung betrachtet, **Then** sieht er pro aktivem Raum mindestens die Anzahl der gültigen Buchungen, die gebuchten Stunden und einen vergleichbaren Auslastungswert.
2. **Given** zwei Räume mit unterschiedlich langer gebuchter Zeit, **When** der Administrator nach Auslastung sortiert, **Then** erscheint der Raum mit der höheren Auslastung an der entsprechenden Position.
3. **Given** eine stornierte Buchung, **When** die Raumauslastung berechnet wird, **Then** wird die stornierte Buchung nicht als tatsächliche Raumnutzung gezählt.
4. **Given** ein Raum wurde im ausgewählten Zeitraum nicht genutzt, **When** die Raumstatistik geladen wird, **Then** wird der Raum mit nachvollziehbaren Nullwerten dargestellt.

### User Story 3 - Nutzung von Raumfeatures auswerten (Priority: P2)

Ein Administrator möchte sehen, welche Ausstattungsmerkmale am häufigsten in gebuchten Räumen vorkommen, um Entscheidungen über Ausstattung und Beschaffung zu unterstützen.

**Why this priority**: Die Feature-Nutzung verbindet Buchungsdaten mit der vorhandenen Ausstattung und macht sichtbar, welche Raummerkmale besonders relevant sind.

**Independent Test**: Für einen Zeitraum mit Buchungen in Räumen mit unterschiedlicher Ausstattung wird die Feature-Auswertung geöffnet. Die Features werden mit einer nachvollziehbaren Nutzungskennzahl angezeigt und sortierbar gemacht.

**Acceptance Scenarios**:

1. **Given** aktive Räume mit unterschiedlichen Ausstattungsmerkmalen und gültige Buchungen, **When** der Administrator die Feature-Statistik öffnet, **Then** sieht er je Feature mindestens die Anzahl der berücksichtigten Buchungen und die gebuchte Zeit.
2. **Given** ein Raum besitzt mehrere Features, **When** eine Buchung dieses Raums ausgewertet wird, **Then** wird diese Buchung jedem zugeordneten Feature zugerechnet und nicht nur einem einzelnen Feature.
3. **Given** ein Feature ist keinem gebuchten Raum zugeordnet, **When** die Feature-Statistik geladen wird, **Then** wird es mit einem Nullwert oder in einem klar gekennzeichneten Bereich für ungenutzte Features dargestellt.
4. **Given** eine stornierte Buchung eines ausgestatteten Raums, **When** die Feature-Nutzung berechnet wird, **Then** wird diese Buchung nicht als Feature-Nutzung gezählt.

### User Story 4 - Personenanzahl pro Buchung beurteilen (Priority: P2)

Ein Administrator möchte erkennen, wie viele Personen einen Raum durchschnittlich pro Buchung nutzen, um die Passung zwischen Raumkapazität und tatsächlicher Nachfrage zu beurteilen.

**Why this priority**: Der Durchschnitt unterstützt die Planung geeigneter Raumgrößen und ergänzt die reine Zeit- und Buchungsanzahl um die tatsächliche Nutzung.

**Independent Test**: Für Buchungen mit bekannten Teilnehmerzahlen wird die Statistik geladen. Der dargestellte Durchschnitt entspricht der Summe der Personen geteilt durch die Anzahl der berücksichtigten gültigen Buchungen.

**Acceptance Scenarios**:

1. **Given** mehrere gültige Buchungen mit erfasster Personenanzahl, **When** der Administrator die Kennzahl betrachtet, **Then** wird die durchschnittliche Personenanzahl pro Buchung für den ausgewählten Zeitraum angezeigt.
2. **Given** Buchungen ohne nutzbare Personenanzahl, **When** der Durchschnitt berechnet wird, **Then** werden diese Buchungen nicht stillschweigend als null Personen behandelt; die Anzeige weist auf die unvollständige Datengrundlage hin.
3. **Given** ein Zeitraum ohne auswertbare gültige Buchungen, **When** der Administrator die Kennzahl lädt, **Then** wird kein künstlicher Durchschnitt angezeigt.
4. **Given** eine stornierte Buchung mit erfasster Personenanzahl, **When** der Durchschnitt berechnet wird, **Then** wird sie nicht in Zähler oder Nenner einbezogen.

### User Story 5 - Stornierungen nachvollziehen (Priority: P2)

Ein Administrator möchte erkennen, wie häufig Raumreservierungen storniert werden, um Probleme bei der Planung und ungenutzte Kapazitäten zu bewerten.

**Why this priority**: Eine transparente Stornierungsquote zeigt, wie viel der geplanten Nutzung tatsächlich entfällt, und kann als Grundlage für organisatorische Verbesserungen dienen.

**Independent Test**: Für einen Zeitraum mit bekannten bestätigten und stornierten Buchungen wird die Stornierungsstatistik geladen. Anzahl und Quote stimmen mit der definierten Berechnung überein.

**Acceptance Scenarios**:

1. **Given** bestätigte und stornierte Buchungen im ausgewählten Zeitraum, **When** der Administrator die Stornierungsstatistik betrachtet, **Then** sieht er mindestens die Anzahl der Stornierungen und die Stornierungsquote.
2. **Given** ein Zeitraum mit Buchungen, aber ohne Stornierungen, **When** die Statistik geladen wird, **Then** wird eine Stornierungsanzahl von null und eine Quote von 0 % angezeigt.
3. **Given** ein Zeitraum ohne Buchungen, **When** die Stornierungsquote berechnet wird, **Then** wird keine irreführende Quote von 0 % aus einem leeren Nenner abgeleitet; die Datengrundlage wird als nicht vorhanden gekennzeichnet.
4. **Given** ein Administrator betrachtet die Statistikübersicht, **When** er die Stornierungskennzahl liest, **Then** ist erkennbar, ob die Quote sich auf alle im Zeitraum angelegten Buchungen oder auf einen anderen definierten Buchungsbestand bezieht.

### Edge Cases

- Start- oder Enddatum des gewählten Zeitraums fehlen oder bilden keinen gültigen Zeitraum; die Statistik wird nicht geladen und der Administrator erhält eine verständliche Eingabeaufforderung.
- Eine Buchung überschneidet die Grenzen des ausgewählten Zeitraums; sie wird entsprechend der festgelegten Zeitraumbetrachtung konsistent berücksichtigt und nicht doppelt gezählt.
- Ein Raum, Gebäude oder Feature wurde deaktiviert oder umbenannt; historische Statistikdaten bleiben dem damaligen beziehungsweise eindeutig identifizierbaren Objekt zugeordnet.
- Ein Raum besitzt mehrere Features; die Buchung darf in der Feature-Auswertung mehrfach erscheinen, aber in der Gesamtbuchungszahl und Raumstatistik nur einmal.
- Bei mehreren Buchungen mit identischen Werten muss die Sortierung stabil und die Darstellung weiterhin verständlich bleiben.
- Bei sehr großen Datenmengen muss die Seite weiterhin nutzbar bleiben; der Administrator erhält entweder die vollständige Auswertung oder eine klare Kennzeichnung, dass die Daten noch geladen werden.
- Zahlen, Prozente, Stunden und Durchschnittswerte werden mit einheitlicher Rundung und verständlichen Einheiten dargestellt.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Das System MUSS einen eigenen Statistikbereich innerhalb des geschützten Adminbereichs bereitstellen.
- **FR-002**: Das System MUSS den Statistikbereich ausschließlich für authentifizierte Benutzer mit Administratorrolle zugänglich machen.
- **FR-003**: Die Statistikübersicht MUSS einen auswählbaren Zeitraum mit Start- und Enddatum anbieten und den aktuell angewendeten Zeitraum sichtbar anzeigen.
- **FR-004**: Das System MUSS alle Kennzahlen der Übersichtsseite auf denselben ausgewählten Zeitraum beziehen.
- **FR-005**: Die Statistikübersicht MUSS die Auslastung für jeden auswertbaren Raum einzeln darstellen.
- **FR-006**: Die Raumstatistik MUSS pro Raum mindestens die Anzahl gültiger Buchungen und die gesamte gebuchte Zeit ausweisen.
- **FR-007**: Die Raumstatistik MUSS einen vergleichbaren Auslastungswert pro Raum darstellen und dessen Berechnungsgrundlage verständlich erklären.
- **FR-008**: Das System MUSS stornierte Buchungen bei der Berechnung tatsächlicher Raumnutzung, gebuchter Zeit und Feature-Nutzung ausschließen.
- **FR-009**: Das System MUSS eine Statistik nach Raumfeatures bereitstellen.
- **FR-010**: Die Feature-Statistik MUSS pro Feature mindestens die Anzahl zugeordneter gültiger Buchungen und die dadurch gebuchte Zeit ausweisen.
- **FR-011**: Eine Buchung eines Raums mit mehreren Features MUSS jedem zugeordneten Feature zugerechnet werden, ohne die Gesamtzahl der Raum- oder Systembuchungen zu vervielfachen.
- **FR-012**: Das System MUSS die durchschnittliche Personenanzahl pro gültiger Buchung für den ausgewählten Zeitraum darstellen.
- **FR-013**: Die Berechnung der durchschnittlichen Personenanzahl MUSS ihre einbezogene Buchungsanzahl und den Umgang mit fehlenden Personenangaben transparent machen.
- **FR-014**: Das System MUSS die Anzahl stornierter Buchungen für den ausgewählten Zeitraum darstellen.
- **FR-015**: Das System MUSS eine Stornierungsquote mit eindeutig beschriebener Bezugsgröße darstellen.
- **FR-016**: Das System MUSS bei einer leeren Datengrundlage Nullwerte, nicht anwendbare Werte und fehlende Daten unterscheidbar darstellen.
- **FR-017**: Der Administrator MUSS Raum-, Feature- und Stornierungsstatistiken innerhalb derselben Statistikübersicht vergleichen können, ohne den ausgewählten Zeitraum erneut eingeben zu müssen.
- **FR-018**: Die Statistikübersicht MUSS die Ergebnisse in einer für die Anzahl der Räume und Features übersichtlichen Form darstellen und eine nachvollziehbare Sortierung ermöglichen.
- **FR-019**: Das System MUSS bei ungültigen Zeitraumeingaben eine verständliche Fehlermeldung anzeigen und darf keine Ergebnisse für einen anderen Zeitraum vortäuschen.
- **FR-020**: Statistikdaten DÜRFEN nur für die Berechtigten sichtbar sein und DÜRFEN nicht über eine ungeschützte Darstellung zugänglich werden.
- **FR-021**: Die Darstellung MUSS Zahlen, Zeitangaben, Prozente und Durchschnittswerte mit konsistenten Einheiten und Rundungsregeln ausweisen.
- **FR-022**: Die Statistikübersicht MUSS historische Buchungen auch dann sinnvoll auswerten können, wenn der zugehörige Raum oder das zugehörige Feature aktuell deaktiviert oder umbenannt ist.

### Key Entities *(include if feature involves data)*

- **Statistikzeitraum**: Start- und Enddatum, auf den alle dargestellten Kennzahlen bezogen werden.
- **Raumstatistik**: Auswertbare Kennzahlen eines einzelnen Raums, insbesondere gültige Buchungsanzahl, gebuchte Zeit und Auslastungswert.
- **Featurestatistik**: Auswertbare Kennzahlen eines Raumfeatures, insbesondere Zuordnung gültiger Buchungen und gebuchte Zeit.
- **Personennutzungsstatistik**: Summe und Durchschnitt der in gültigen Buchungen erfassten Personen sowie die zugrunde liegende Buchungsanzahl.
- **Stornierungsstatistik**: Anzahl stornierter Buchungen und die daraus berechnete Quote mit ihrer Bezugsgröße.
- **Raumfeature**: Ein Ausstattungsmerkmal, das einem oder mehreren Räumen zugeordnet sein kann und in der Nutzungsstatistik ausgewertet wird.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % der geprüften Zugriffsversuche von Benutzern ohne Administratorrolle erhalten keine Statistikdaten.
- **SC-002**: Ein Administrator kann die Statistikübersicht einschließlich aller vier geforderten Metrikgruppen in höchstens 30 Sekunden öffnen und den angewendeten Zeitraum erkennen.
- **SC-003**: In 100 % der Testfälle mit einer bekannten Datenbasis stimmen Raumanzahl, gebuchte Zeit, Feature-Nutzung, Personendurchschnitt und Stornierungswerte mit der definierten Berechnung überein.
- **SC-004**: 100 % der geprüften Kennzahlen zeigen den ausgewählten Zeitraum sichtbar oder sind eindeutig an die gemeinsame Zeitraumsauswahl gebunden.
- **SC-005**: 100 % der geprüften stornierten Buchungen werden aus den Kennzahlen zur tatsächlichen Raumnutzung und aus der Feature-Nutzung ausgeschlossen.
- **SC-006**: Bei mindestens 9 von 10 repräsentativen Administratoren werden die am stärksten ausgelasteten Räume und am häufigsten genutzten Features innerhalb von zwei Minuten korrekt identifiziert.
- **SC-007**: Bei leeren oder unvollständigen Daten können mindestens 9 von 10 repräsentativen Administratoren unterscheiden, ob ein Wert null, nicht anwendbar oder wegen fehlender Daten nicht bestimmbar ist.
- **SC-008**: Die Statistikseite verarbeitet einen Zeitraum mit mindestens 100 Räumen und 10.000 Buchungen so, dass die erste nutzbare Darstellung innerhalb von 5 Sekunden sichtbar ist.
- **SC-009**: In einer Abnahme mit mindestens 10 repräsentativen Administratoren können mindestens 9 Personen die Stornierungsanzahl und ihre Bezugsgröße ohne zusätzliche Erklärung korrekt benennen.

## Assumptions

- Der Statistikbereich ist ausschließlich für Benutzer mit der bereits im Projekt vorgesehenen Administratorrolle bestimmt.
- Als Standardzeitraum wird der Zeitraum der letzten zwölf vollständigen Monate bis zum aktuellen Tag verwendet; der Administrator kann den Zeitraum ändern.
- Eine gültige Buchung ist eine dauerhaft angelegte, nicht stornierte Reservierung. Die genaue Statusbezeichnung des bestehenden Buchungssystems wird dabei sinngemäß verwendet.
- Die Raumauslastung wird als Anteil der gebuchten Raumzeit an der gesamten Kalenderzeit des ausgewählten Zeitraums verstanden. Die Anzeige muss diese Berechnungsgrundlage kenntlich machen; individuelle Öffnungszeiten werden nicht vorausgesetzt.
- Buchungen werden nach ihrem tatsächlich in den ausgewählten Zeitraum fallenden Zeitanteil ausgewertet, damit Buchungen an den Zeitraumgrenzen nicht doppelt gezählt werden.
- Die durchschnittliche Personenanzahl wird aus der bei der Buchung angegebenen Teilnehmerzahl berechnet. Buchungen ohne nutzbare Personenangabe werden separat ausgewiesen und nicht als null Personen interpretiert; eine zusätzliche Erfassung tatsächlich anwesender Personen ist nicht Teil dieses Features.
- Die Stornierungsquote wird als Anzahl stornierter Buchungen geteilt durch alle im Zeitraum angelegten Buchungen berechnet; bei null angelegten Buchungen ist die Quote nicht anwendbar. Die Anzahl unterschiedlicher von Stornierungen betroffener Räume ist nicht Teil dieses Features.
- Eine Buchung eines Raums mit mehreren Features zählt in der Feature-Auswertung einmal je zugeordnetem Feature, aber nur einmal in Gesamt- und Raumkennzahlen.
- Die Statistikübersicht ist für Desktop- und Tablet-Nutzung vorgesehen; eine eigene Exportfunktion und historische Datenrekonstruktion außerhalb vorhandener Buchungsdaten sind nicht Teil dieses Features.
- Bestehende Buchungs-, Raum-, Feature- und Rolleninformationen stehen für die Auswertung zur Verfügung und werden nicht durch dieses Feature inhaltlich verändert.
