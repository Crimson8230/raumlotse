# Feature Specification: Admin-Belegungsübersicht

**Feature Branch**: `016-admin-occupancy-overview`

**Created**: 2026-10-11

**Status**: Draft

**Input**: User description: "Als Admin möchte ich eine Übersicht über alle Buchungen haben, damit ich weiß, wie viele Personen sich gerade im Gebäude aufhalten werden. Akzeptanzkriterien: Für Admins gibt es eine Gesamtübersicht zur aktuellen Situation; Anzahl der aktuell eingecheckten Personen; Anzahl der aktuell belegten Räume"

## Clarifications

### Session 2026-10-11

- Q: Wie wird „eingecheckte Personen“ gezählt, wer darf die Übersicht sehen, und was gilt als belegter Raum? → A: Wie spezifiziert bestätigt: Personen = Summe der erwarteten Teilnehmenden aller `ACTIVE`-Buchungen; belegt = Raum mit mindestens einer `ACTIVE`-Buchung; Zugriff mit dem Recht „Statistiken lesen“.
- Q: Was soll die Prognose für einen späteren Zeitpunkt zählen? → A: Alle `RESERVED`- und `ACTIVE`-Buchungen, die den gewählten Zeitpunkt überschneiden, mit ihren erwarteten Teilnehmenden (erwartete Personen) und der Anzahl unterschiedlicher Räume (gebuchte Räume); keine Abschläge für erwartete Nichterscheinen.
- Q: Wie wählt der Admin die Prognose aus? → A: Er wählt einen Tag und sieht je Stunde (07:00–22:00) erwartete Personen und gebuchte Räume mit markierter Spitze; bei Auswahl einer Stunde werden die in dieser Stunde gebuchten Räume aufgelistet.
- Q: Sind in der Prognose auch reservierte, noch nicht eingecheckte Buchungen enthalten, und wie wird sie gegliedert? → A: Ja, reservierte (`RESERVED`) Buchungen zählen in der Prognose voll mit, ebenso bereits eingecheckte (`ACTIVE`). Die Prognose zeigt für einen gewählten Tag die erwarteten Personen je Stunde und je Gebäude, zusätzlich die Summe aller Gebäude je Stunde.
- Q: Wie zeigt die Prognose für eine gewählte Stunde, wo sich die erwarteten Personen aufhalten werden? → A: Als Liste der Buchungen dieser Stunde, gruppiert nach Gebäude und Stockwerk, mit der Zahl erwarteter Personen je Stockwerk; die Stockwerke mit den meisten Personen stehen oben. Eine Darstellung auf den Etagenplänen (Feature 012) ist nicht Teil dieses Features und wird als eigenes Feature vorgesehen.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Aktuelle Gesamtsituation auf einen Blick (Priority: P1)

Als Admin möchte ich eine Gesamtübersicht öffnen, die mir sofort zeigt, wie viele Personen aktuell eingecheckt sind und wie viele Räume aktuell belegt sind, damit ich weiß, wie viele Personen sich gerade im Gebäude aufhalten.

**Why this priority**: Die beiden Kennzahlen sind die Akzeptanzkriterien der Anforderung und für sich allein bereits nutzbar, etwa für Sicherheits-, Reinigungs- oder Hausdienstplanung.

**Independent Test**: Es werden drei Buchungen in unterschiedlichen Räumen angelegt, zwei davon werden eingecheckt (erwartete Teilnehmerzahl 4 und 10). Ein Admin öffnet die Übersicht und sieht „14 eingecheckte Personen“ und „2 belegte Räume“. Ein Nutzer ohne Berechtigung erhält keinen Zugriff.

**Acceptance Scenarios**:

1. **Given** zwei eingecheckte (`ACTIVE`) Buchungen mit 4 und 10 erwarteten Teilnehmenden in zwei verschiedenen Räumen und eine nicht eingecheckte (`RESERVED`) Buchung, **When** ein Admin die Belegungsübersicht öffnet, **Then** zeigt sie 14 aktuell eingecheckte Personen und 2 aktuell belegte Räume.
2. **Given** keine Buchung ist aktuell eingecheckt, **When** ein Admin die Übersicht öffnet, **Then** zeigt sie 0 eingecheckte Personen und 0 belegte Räume sowie einen verständlichen Hinweis, dass derzeit kein Raum in Nutzung ist.
3. **Given** die Übersicht ist geöffnet, **When** eine weitere Buchung eingecheckt wird oder eine eingecheckte Buchung endet, **Then** werden beide Kennzahlen ohne manuelles Neuladen innerhalb von 60 Sekunden aktualisiert.
4. **Given** die Übersicht ist geöffnet, **When** der Admin auf die Kennzahlen blickt, **Then** ist der Zeitpunkt des angezeigten Datenstands sichtbar.
5. **Given** ein Nutzer ohne das Recht „Statistiken lesen“ oder ein nicht angemeldeter Besucher, **When** er die Übersicht aufruft, auch über einen direkten Aufruf, **Then** werden keine Belegungsdaten angezeigt und der Zugriff wird verweigert.

---

### User Story 2 - Tagesprognose je Stunde und Gebäude (Priority: P2)

Als Admin möchte ich einen Tag auswählen und je Stunde und je Gebäude sehen, wie viele Personen sich laut Buchungen voraussichtlich aufhalten werden und welche Räume gebucht sind, damit ich Personal, Reinigung oder Sicherheit vorausschauend planen und die Spitzenzeit erkennen kann.

**Why this priority**: Die Prognose deckt das „aufhalten werden“ der Anforderung ab und ist für die Planung sehr nützlich, baut aber auf denselben Kennzahlen wie US1 auf.

**Independent Test**: Gebäude A hat die Räume R1 und R2, Gebäude B den Raum R3. Für morgen existieren zwei `RESERVED`-Buchungen 10:00–12:00 in R1 (5 Teilnehmende) und R2 (12), eine `RESERVED`-Buchung 10:00–11:00 in R3 (7), eine Buchung 14:00–15:00 in R1 (3) und eine stornierte Buchung 11:00–12:00 in R3 (20). Ein Admin wählt morgen und sieht: 10:00 → A 17, B 7, gesamt 24 Personen in 3 Räumen (Spitze); 11:00 → A 17, B 0, gesamt 17; 14:00 → A 3, B 0, gesamt 3. Nach Auswahl der Stunde 10:00 sieht er R1, R2 und R3; die stornierte Buchung erscheint nirgends.

**Acceptance Scenarios**:

1. **Given** Buchungen an einem gewählten Tag, **When** ein Admin diesen Tag auswählt, **Then** zeigt die Übersicht für jede Stunde von 07:00 bis 22:00 die erwarteten Personen je Gebäude sowie die Summe über alle Gebäude mit der Anzahl gebuchter Räume, und die Stunde(n) mit den meisten erwarteten Personen sind als Spitze markiert, sowohl für die Summe als auch je Gebäude.
2. **Given** eine reservierte, noch nicht eingecheckte Buchung und eine eingecheckte Buchung überschneiden eine Stunde des gewählten Tages, **When** die Prognose berechnet wird, **Then** zählen die erwarteten Teilnehmenden beider Buchungen voll in die Stundenwerte.
3. **Given** die Tagesansicht ist geöffnet, **When** der Admin eine Stunde auswählt, **Then** sieht er die in dieser Stunde gebuchten Räume, gruppiert nach Gebäude und Stockwerk (z. B. „MCI III · 1. OG – 100 Personen“ mit darunter „Raum 301, 14:00–16:00, 100 Personen, reserviert“). Jede Gruppe zeigt die erwarteten Personen dieses Stockwerks, die Gruppen sind absteigend nach erwarteten Personen sortiert, und es werden keine personenbezogenen Daten der Buchenden angezeigt.
4. **Given** eine stornierte, verfallene oder abgeschlossene Buchung am gewählten Tag, **When** die Prognose berechnet wird, **Then** fließt sie weder in die Stundenwerte noch in die Raumliste ein.
5. **Given** zwei aufeinanderfolgende Buchungen im selben Raum innerhalb einer Stunde (10:00–10:30 mit 8 und 10:30–11:00 mit 6 Personen), **When** die Stunde 10:00 berechnet wird, **Then** werden 8 erwartete Personen (höchste gleichzeitige Zahl) und 1 gebuchter Raum angezeigt, auch in der Spalte des Gebäudes dieses Raums, und die Raumliste enthält beide Buchungen.
6. **Given** eine Prognose wird angezeigt, **When** der Admin auf die Werte blickt, **Then** ist eindeutig erkennbar, dass es sich um geplante Werte und nicht um eingecheckte Personen handelt, und er kann mit einer Aktion zur aktuellen Situation zurückkehren.
7. **Given** der Admin wählt einen Tag in der Vergangenheit, **When** er die Auswahl bestätigt, **Then** wird keine Prognose angezeigt und ein Hinweis verweist auf den Statistikbereich (Feature 011). Für den heutigen Tag werden bereits vergangene Stunden als vergangen gekennzeichnet und nicht als Prognose ausgewiesen.
8. **Given** eine Gebäudeeingrenzung ist aktiv (US4), **When** der Admin die Tagesansicht öffnet, **Then** zeigen Stundenwerte und Raumliste nur dieses Gebäude, und die Summe entspricht den Werten dieses Gebäudes.

---

### User Story 3 - Liste der aktuell laufenden Buchungen (Priority: P2)

Als Admin möchte ich unter den Kennzahlen alle aktuell laufenden Buchungen sehen, damit ich nachvollziehen kann, wo sich die gezählten Personen befinden und welche Buchungen noch nicht eingecheckt sind.

**Why this priority**: Die Liste macht die Kennzahlen nachvollziehbar und entspricht dem Wunsch nach einer „Übersicht über alle Buchungen“, ist aber ohne die Kennzahlen aus US1 weniger wertvoll.

**Independent Test**: Bei bekannten laufenden Buchungen öffnet ein Admin die Übersicht und prüft, dass jede laufende Buchung mit Raum, Gebäude, Stockwerk, Zeitraum, erwarteter Teilnehmerzahl und Status (eingecheckt / noch nicht eingecheckt) genau einmal aufgeführt ist.

**Acceptance Scenarios**:

1. **Given** laufende Buchungen in mehreren Räumen, **When** ein Admin die Übersicht öffnet, **Then** sieht er je laufender Buchung Raum, Gebäude, Stockwerk, Start- und Endzeit, erwartete Teilnehmerzahl und ob sie eingecheckt ist, sowie bei eingecheckten Buchungen die Check-in-Zeit.
2. **Given** eine Buchung, deren Startzeit erreicht, die aber noch nicht eingecheckt ist, **When** die Liste angezeigt wird, **Then** erscheint sie als „noch nicht eingecheckt“ und fließt nicht in die Kennzahlen eingecheckter Personen oder belegter Räume ein.
3. **Given** die Liste enthält Buchungen, **When** sie angezeigt wird, **Then** enthält sie keine Namen oder sonstigen personenbezogenen Daten der Buchenden.
4. **Given** keine Buchung läuft gerade, **When** die Übersicht geöffnet wird, **Then** zeigt die Liste einen verständlichen Leerzustand.

---

### User Story 4 - Aufschlüsselung nach Gebäude (Priority: P3)

Als Admin möchte ich die Kennzahlen je Gebäude sehen und die Übersicht auf ein Gebäude eingrenzen, damit ich weiß, wie viele Personen sich in einem bestimmten Gebäude aufhalten.

**Why this priority**: Das System verwaltet mehrere Gebäude; die Frage „wie viele Personen sind im Gebäude“ betrifft daher oft ein bestimmtes Gebäude. Die Gesamtsicht aus US1 bleibt aber auch ohne diese Aufschlüsselung nutzbar.

**Independent Test**: Bei eingecheckten Buchungen in zwei Gebäuden wählt ein Admin ein Gebäude aus und prüft, dass Kennzahlen und Liste nur dieses Gebäude betreffen und die Summen aller Gebäude der Gesamtsicht entsprechen.

**Acceptance Scenarios**:

1. **Given** eingecheckte Buchungen in Gebäude A (8 Personen, 1 Raum) und Gebäude B (6 Personen, 2 Räume), **When** ein Admin die Übersicht öffnet, **Then** sieht er die Kennzahlen je Gebäude sowie die Gesamtsumme von 14 Personen und 3 Räumen.
2. **Given** die Übersicht zeigt alle Gebäude, **When** der Admin Gebäude A auswählt, **Then** zeigen Kennzahlen und Buchungsliste nur Gebäude A, und die Auswahl ist sichtbar.
3. **Given** ein aktives Gebäude ohne eingecheckte Buchungen, **When** die Aufschlüsselung angezeigt wird, **Then** erscheint es mit 0 Personen und 0 belegten Räumen.

### Edge Cases

- **Mehrere eingecheckte Buchungen im selben Raum**: Ein Raum zählt höchstens einmal als belegt. Durch die Check-in-Regeln aus Feature 014 sollte das nicht vorkommen; tritt es dennoch auf, werden die Personen beider Buchungen gezählt, der Raum aber nur einmal.
- **Frühes Einchecken**: Eine Buchung, die bis zu der eingestellten Frühzeit vor ihrem Start eingecheckt wurde (Feature 014), zählt ab dem Check-in als eingecheckt und ihr Raum als belegt.
- **Buchungsende**: Eine Buchung zählt ab ihrer Endzeit nicht mehr, auch wenn der regelmäßige Ablauf sie erst bis zu 60 Sekunden später auf „abgeschlossen“ setzt. Vorzeitig abgeschlossene oder stornierte Buchungen zählen ab diesem Moment nicht mehr.
- **Deaktivierte Räume oder Gebäude**: Eine eingecheckte Buchung in einem inzwischen deaktivierten Raum oder Gebäude wird weiterhin gezählt, weil sich die Personen tatsächlich dort aufhalten.
- **Erwartete Teilnehmerzahl über der Raumkapazität**: Die angegebene Zahl wird unverändert gezählt; eine Korrektur oder Warnung ist nicht Teil dieses Features.
- **Kurzzeitiger Ausfall der Aktualisierung**: Kann der aktuelle Stand nicht geladen werden, bleibt der letzte Stand mit seinem Zeitstempel sichtbar und ein Hinweis zeigt, dass die Daten veraltet sein können.
- **Entzug der Berechtigung während geöffneter Übersicht**: Bei der nächsten Aktualisierung entfernt die Übersicht alle angezeigten Belegungsdaten und zeigt den Hinweis, dass der Zugriff nicht mehr erlaubt ist; es wird kein veralteter Stand weiter angezeigt.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Das System MUSS im Adminbereich eine Belegungsübersicht bereitstellen, die die aktuelle Situation zum Zeitpunkt des Aufrufs darstellt.
- **FR-002**: Die Belegungsübersicht MUSS ausschließlich angemeldeten Nutzern mit dem Recht „Statistiken lesen“ (Feature 015) zugänglich sein. Direkte Aufrufe MÜSSEN ebenso geprüft und ohne Preisgabe von Daten verweigert werden.
- **FR-003**: Die Übersicht MUSS die Anzahl der aktuell eingecheckten Personen anzeigen. Sie ist definiert als Summe der erwarteten Teilnehmerzahlen aller Buchungen im Status `ACTIVE`, deren Check-in-Zeitpunkt erreicht und deren Endzeit noch nicht erreicht ist.
- **FR-004**: Die Übersicht MUSS die Anzahl der aktuell belegten Räume anzeigen. Sie ist definiert als Anzahl unterschiedlicher Räume mit mindestens einer solchen Buchung (Definition aus FR-003); dies entspricht dem Raumstatus „belegt“ aus Feature 014.
- **FR-005**: Buchungen in den Status `RESERVED`, `COMPLETED`, `EXPIRED` und `CANCELLED` DÜRFEN NICHT in die Kennzahlen aus FR-003 und FR-004 einfließen.
- **FR-006**: Die Übersicht MUSS die Kennzahlen und die Buchungsliste ohne manuelles Neuladen mindestens alle 60 Sekunden aktualisieren und den Zeitpunkt des angezeigten Datenstands sichtbar ausweisen.
- **FR-007**: Die Übersicht MUSS alle aktuell laufenden Buchungen auflisten: eingecheckte (`ACTIVE`) Buchungen sowie `RESERVED`-Buchungen, deren Startzeit erreicht und deren Endzeit noch nicht überschritten ist. Je Buchung MÜSSEN Raum, Gebäude, Stockwerk, Start- und Endzeit, erwartete Teilnehmerzahl, Check-in-Status und bei eingecheckten Buchungen die Check-in-Zeit angezeigt werden.
- **FR-008**: Die Buchungsliste und die Kennzahlen DÜRFEN keine Namen, E-Mail-Adressen, Notizen oder sonstigen personenbezogenen Daten der Buchenden enthalten.
- **FR-009**: Die Übersicht MUSS die Kennzahlen aus FR-003 und FR-004 zusätzlich je Gebäude ausweisen: für jedes aktive Gebäude, einschließlich Gebäuden mit Nullwerten, und für inaktive Gebäude nur dann, wenn sie aktuell Werte größer 0 haben. Die Summe über alle ausgewiesenen Gebäude MUSS der Gesamtsicht entsprechen.
- **FR-010**: Der Admin MUSS die Übersicht auf ein einzelnes Gebäude eingrenzen und zur Gesamtsicht zurückkehren können; die aktive Eingrenzung MUSS sichtbar sein.
- **FR-011**: Bei leerer Datengrundlage MUSS die Übersicht Nullwerte und einen verständlichen Leerzustand anzeigen, statt leerer oder fehlerhafter Felder.
- **FR-012**: Kann der aktuelle Stand nicht geladen werden, MUSS die Übersicht den zuletzt geladenen Stand mit Zeitstempel weiter anzeigen und deutlich darauf hinweisen, dass er veraltet sein kann. Dies gilt nicht für eine fehlende Anmeldung oder Berechtigung (Antwort 401/403): dann MÜSSEN die angezeigten Daten entfernt werden.
- **FR-013**: Die Übersicht DARF keine Daten verändern; sie ist eine reine Leseansicht.
- **FR-014**: Der Admin MUSS den heutigen oder einen späteren Tag auswählen können. Für diesen Tag MUSS die Übersicht je Stunde von 07:00 bis 22:00 Planwerte anzeigen, berechnet aus allen Buchungen im Status `RESERVED` (reserviert, noch nicht eingecheckt) oder `ACTIVE` (eingecheckt), deren Zeitraum die Stunde überschneidet (Beginn inklusive, Ende exklusive). Reservierte Buchungen zählen voll mit. Erwartete Personen = höchste Summe erwarteter Teilnehmender, die zu einem Zeitpunkt innerhalb der Stunde gleichzeitig gebucht ist; gebuchte Räume = Anzahl unterschiedlicher Räume mit mindestens einer solchen Buchung. `CANCELLED`-, `EXPIRED`- und `COMPLETED`-Buchungen DÜRFEN NICHT einfließen.
- **FR-014a**: Die Tagesansicht MUSS die erwarteten Personen und gebuchten Räume je Stunde zusätzlich je Gebäude ausweisen: für jedes aktive Gebäude (auch mit Nullwerten) und für inaktive Gebäude mit Buchungen an diesem Tag. Die Summe über alle Gebäude je Stunde MUSS angezeigt werden; sie wird nach derselben Regel wie FR-014 über alle Gebäude berechnet.
- **FR-015**: Die Tagesansicht MUSS die Stunde(n) mit den meisten erwarteten Personen als Spitze markieren, für die Summe und je Gebäude. Liegen Buchungen des Tages außerhalb von 07:00–22:00, MÜSSEN die betroffenen Stunden zusätzlich angezeigt werden.
- **FR-016**: Wählt der Admin eine Stunde aus, MUSS die Übersicht alle Buchungen auflisten, die diese Stunde überschneiden, gruppiert nach Gebäude und Stockwerk. Je Gruppe MÜSSEN Gebäude, Stockwerk und die erwarteten Personen des Stockwerks in dieser Stunde (Regel aus FR-014, angewendet auf die Buchungen des Stockwerks) angezeigt werden; je Buchung Raum, Buchungszeitraum, erwartete Teilnehmende und Status (eingecheckt / reserviert). Die Gruppen MÜSSEN absteigend nach erwarteten Personen sortiert sein, bei Gleichstand nach Gebäude- und Stockwerksname; innerhalb einer Gruppe nach erwarteten Teilnehmenden absteigend. FR-008 (keine personenbezogenen Daten) gilt auch hier.
- **FR-017**: Prognosewerte MÜSSEN sichtbar als geplante Werte gekennzeichnet und von den aktuellen Kennzahlen (eingecheckte Personen, belegte Räume) unterscheidbar sein. Der Admin MUSS mit einer Aktion zur aktuellen Situation zurückkehren können. Vergangene Tage MÜSSEN mit einem Hinweis auf den Statistikbereich abgelehnt werden; bereits vergangene Stunden des heutigen Tages MÜSSEN als vergangen gekennzeichnet werden.
- **FR-018**: Eine aktive Gebäudeeingrenzung (FR-010) MUSS auch für die Tagesansicht gelten.

### Key Entities

- **Reservierung (bestehend)**: Buchung eines Raums mit Start- und Endzeit, erwarteter Teilnehmerzahl, Status und Check-in-Zeitpunkt (Feature 014). Quelle aller Kennzahlen; dieses Feature ändert sie nicht.
- **Belegungsstand**: Momentaufnahme zu einem Zeitpunkt mit Anzahl eingecheckter Personen, Anzahl belegter Räume, Zeitstempel und der Liste laufender Buchungen; optional auf ein Gebäude eingegrenzt.
- **Tagesprognose**: Planwerte für einen gewählten Tag aus allen `RESERVED`- und `ACTIVE`-Buchungen, aufgeteilt in Stunden; je Stunde die erwarteten Personen und gebuchten Räume je Gebäude und als Summe, mit markierter Spitze, sowie die Liste der gebuchten Räume, gruppiert nach Gebäude und Stockwerk mit Personen je Stockwerk.
- **Gebäudebelegung**: Anzahl eingecheckter Personen und belegter Räume je Gebäude als Teil des Belegungsstands.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein Admin kann die Anzahl der aktuell eingecheckten Personen und der belegten Räume innerhalb von 10 Sekunden nach dem Öffnen des Adminbereichs ablesen.
- **SC-002**: In 100 % der Testfälle mit bekannter Datenbasis stimmen die angezeigten Kennzahlen (gesamt und je Gebäude) mit der Definition aus FR-003 und FR-004 überein.
- **SC-003**: Nach einem Check-in oder Buchungsende zeigt eine geöffnete Übersicht in 100 % der Fälle spätestens nach 60 Sekunden den neuen Stand.
- **SC-004**: 100 % der geprüften Zugriffsversuche ohne das Recht „Statistiken lesen“ erhalten keine Belegungsdaten.
- **SC-005**: 0 % der angezeigten Buchungseinträge enthalten Namen oder andere personenbezogene Daten der Buchenden.
- **SC-006**: Die Übersicht bleibt bei mindestens 100 Räumen und 500 gleichzeitig laufenden Buchungen nutzbar; die erste vollständige Darstellung ist innerhalb von 3 Sekunden sichtbar.
- **SC-007**: In 100 % der Testfälle mit bekannter Datenbasis stimmen die Stundenwerte je Gebäude und als Summe, die markierten Spitzen und die nach Stockwerk gruppierte Raumliste je Stunde (Personen je Stockwerk, Sortierung) der Tagesprognose mit der Definition aus FR-014 bis FR-016 überein.
- **SC-008**: Ein Admin kann die Spitzenzeit eines gewählten Tages sowie das Gebäude, Stockwerk und den Raum mit den meisten erwarteten Personen zu dieser Zeit innerhalb von 30 Sekunden ablesen.

## Assumptions

- „Eingecheckte Personen“ wird über die bei der Buchung angegebene erwartete Teilnehmerzahl der eingecheckten Buchungen ermittelt. Das System erfasst beim Check-in nur die Buchung, nicht jede einzelne anwesende Person (Feature 014); eine Zählung tatsächlich anwesender Personen, etwa per Sensor oder Einzel-Check-in, ist nicht Teil dieses Features. Die Übersicht weist darauf hin, dass es sich um erwartete Teilnehmende eingecheckter Buchungen handelt.
- Personen außerhalb gebuchter Räume (Flure, Büros ohne Buchung, Besucher) werden nicht erfasst; die Kennzahl ist eine Untergrenze auf Basis der Buchungen.
- „Belegt“ entspricht dem bestehenden Raumstatus „occupied“ aus Feature 009/014 und wird aus dem Reservierungsstatus `ACTIVE` abgeleitet; es wird kein neuer Status eingeführt.
- Mit „Admin“ sind Nutzer mit dem Recht „Statistiken lesen“ gemeint, das standardmäßig nur die Admin-Rolle besitzt (Feature 015). Ein eigenes neues Recht wird nicht eingeführt.
- Die Übersicht wird im bestehenden Adminbereich neben dem Statistikbereich (Feature 011) angeboten. Historische Auswertungen vergangener Zeitpunkte sind nicht Teil dieses Features; dafür ist der Statistikbereich zuständig.
- Die Prognose beruht ausschließlich auf vorhandenen Buchungen und ihren erwarteten Teilnehmenden. Abschläge für erwartetes Nichterscheinen oder statistische Vorhersagen sind nicht Teil dieses Features.
- Eine Darstellung der erwarteten Personen auf den Etagenplänen (Feature 012) ist nicht Teil dieses Features; sie ist als eigenes, späteres Feature vorgesehen. Bis dahin zeigt die nach Stockwerk gruppierte Liste (FR-016), wo sich die Personen aufhalten werden.
- Ein Export, Benachrichtigungen bei Schwellwerten (z. B. Höchstbelegung eines Gebäudes) sowie ein Hinweis auf Kapazitätsüberschreitungen sind nicht Teil dieses Features.
- Die Übersicht ist für Desktop und Tablet vorgesehen und nutzt die bestehenden Gestaltungsrichtlinien (Feature 002).
