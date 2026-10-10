# Research: Rollenrechte verwalten

## R1 – Persistenz und Versionskonflikte

**Decision**: Fünf fest benannte Rollen behalten ihre bestehenden `role_assignment`-Zeilen. Eine neue Rollenrechte-Zuordnung speichert die 14 festen Berechtigungscodes pro Rolle; ein Versionswert pro Rolle schützt vollständige Ersetzungen. Eine Änderung sperrt die betroffene Rollenrechte-Version, prüft danach Admin-Mitgliedschaft und `expectedVersion`, validiert die Auswahl und speichert sie atomar. Ein unveränderter aktueller Stand bleibt ohne Versionswechsel; ein veralteter Stand wird auch bei gleichem Inhalt abgelehnt.

**Rationale**: Entspricht dem vorhandenen Muster der versionierten Rollenzuordnung in `UserRoleService` und vermeidet verlorene Änderungen. Rechteänderungen können den festen Admin-Zugang nicht entfernen, sodass die bestehende globale Sperre `role_mutation_guard` für den letzten Admin bei Rollenzuordnungen bestehen bleibt; Rollenrechte können unabhängig je Rolle gesperrt werden.

**Alternatives considered**: Eine JSON-Konfiguration pro Rolle erschwert Integritätsprüfung und Abfragen; Einzeländerungen ohne Version könnten parallele Admin-Bearbeitungen überschreiben. Eine zusätzliche frei definierbare Rollentabelle würde die fünf festen Rollen unnötig aufweichen.

## R2 – Einführung und neue Konten

**Decision**: Die nächste Flyway-Migration legt genau fünf Rechte-Versionen an und setzt die in FR-007 definierten Anfangsrechte: Viewer nur Lesen; Staff, Student und Lecturer Lesen, Reservieren, eigene Reservierungen bearbeiten und eigene aktive Raumgeräte steuern; Admin zusätzlich Fremdbuchungen und alle Verwaltungsrechte. Bestehende Nutzerrollen bleiben unverändert. Ein datenbankseitiger Standard für neu eingefügte Nutzerkonten legt `user_role_state` und Viewer atomar an. Die lokale Bootstrap-Fixture ersetzt Viewer nur bei einem gerade von ihr neu erzeugten Konto gezielt durch Admin und verändert bestehende Konten bei Neustart nicht.

**Rationale**: Derzeit gibt es keinen regulären Produktions-Endpunkt zur Kontoanlage; die lokale Fixture ist der einzige Anwendungspfad. Der Standard am Konto-Datensatz deckt auch künftige unterstützte Import- und Provisionierungswege ab. Die Bootstrap-Ausnahme erhält einen ersten Admin, ohne neuen Alltagsnutzern Admin zu geben.

**Alternatives considered**: Nur die Fixture anzupassen lässt externe/neue Kontoanlagen ungeschützt; ein stets nachlaufender Reparaturlauf könnte kurzzeitig Konten ohne Rolle sichtbar machen; Admin für alle Fixture-Neustarts erneut zu setzen würde spätere Admin-Entzüge rückgängig machen.

## R3 – Berechtigungsentscheidung und unbekannte Aktionen

**Decision**: Eine zentrale, vollständige Zuordnung aus Anfrageart und Ressourcenpfad zu einem der 14 Rechte oder zu einer ausdrücklich festgeschützten Kategorie ersetzt die pauschale Admin-Prüfung in `RoleAccessFilter`. Öffentliche Authentifizierung und Health sind ausdrücklich ausgenommen; Rollen- und Rollenrechteverwaltung bleibt `ADMIN`-fest. Für alle weiteren geschützten Anfragen gilt ohne Zuordnung Ablehnung. Die effektiven Rechte werden aus aktuellen Rollenzuordnungen und Rollenrechten für jede Anfrage neu ermittelt; Speicherfehler führen zu keiner Freigabe. Ein Test vergleicht die vollständige Menge gemappter Handler mit dem Klassifikationskatalog.

**Rationale**: Die bestehende Filterposition nach CSRF und vor Controller-Deserialisierung bietet einen gemeinsamen Kontrollpunkt. Ein enumerierter Katalog verhindert vergessene Prüfrouten und erfüllt FR-013. Aktuelle Daten pro Anfrage erfüllen FR-004 ohne Sitzungs-Cache.

**Alternatives considered**: Autorisierung nur in React wäre umgehbar; ein Rollenrecht-Snapshot in der Sitzung würde Entzüge verzögern; verstreute Annotationen könnten bei neuen Endpunkten fehlen.

## R4 – Reservierungseigentum und fremde Details

**Decision**: Die bestehende `ReservationAccessPolicy` unterscheidet künftig Eigentümer mit `OWN_RESERVATION_MANAGE`, Fremde mit `OTHER_RESERVATION_MANAGE` und Lesezugriff. Der Admin-Rollenname ist kein automatischer Buchungs-Override mehr; die initialen Admin-Rechte bewahren sein bisheriges Verhalten, können aber geändert werden. Fremde Einzel-Details und Aktionen ohne Recht liefern denselben 404-Ausgang wie ein unbekannter Datensatz. Raum-Belegungslisten bleiben bei `READ` sichtbar und für fremde Buchungen stets auf Zeitfenster und Status beschränkt; berechtigte Bearbeiter rufen benötigte fremde Details einzeln ab. Eigene Details benötigen `READ`; Eigentum folgt weiterhin `createdByUserId`.

**Rationale**: Setzt die neue Trennung eigener und fremder Reservierungen um und bewahrt die Datenschutzregel aus Feature 013. Ein ungeklärter Ersteller ist für niemanden „eigen“.

**Alternatives considered**: Admin immer als Override behalten widerspräche dem konfigurierbaren Recht der Admin-Rolle; 403 für fremde Buchungen würde ihre Existenz offenlegen.

## R5 – Administrationsmodus und geteilte Oberflächen

**Decision**: Der bestehende Sitzungs-Schalter bleibt eine reine Sichtbarkeitshilfe. Er ist für Admin oder für Nutzer mit mindestens einem Verwaltungsrecht nutzbar und wird bei Verlust dieser Voraussetzung wirksam ausgeschaltet. `/api/auth/roles` liefert zusätzlich die aktuellen effektiven Berechtigungscodes. `/admin/locations` und `/admin/maps` behalten ihre gemeinsamen Routen, zeigen darin aber jeweils nur die erlaubten Teilbereiche und Aktionen. `/admin/users` und die neue Rollenrechte-Seite bleiben Admin-only. Navigation, direkte Seitenzugriffe und Aktionsschaltflächen nutzen dieselben effektiven Rechte; Serverprüfungen bleiben maßgeblich.

**Rationale**: Minimiert Route- und Komponentenumbau, erhält Feature 013 und verhindert, dass eine Sammelseite unberechtigte Teilaktionen offenbart.

**Alternatives considered**: Eine neue Route für jedes Formular erzeugt viele Weiterleitungen; ein einziges Recht für die Sammelseite würde die geforderte Granularität verlieren.

## R6 – Bedienbare Kombinationen und Fehler

**Decision**: Jede Rolle darf `READ` allein oder mit beliebigen der übrigen 13 Rechte haben; ohne `READ` darf sie kein anderes konfigurierbares Recht haben. Die Rechteverwaltung meldet eine ungültige Kombination, statt Rechte implizit zu ergänzen oder zu entfernen. Ablehnungen werden mit Kategorie, HTTP-Methode, normalisiertem Routenmuster und Nutzer-ID strukturiert protokolliert, ohne Buchungsinhalt oder sonstige personenbezogene Details.

**Rationale**: Entspricht den drei Klärungsantworten und der Sicherheits- und Beobachtbarkeitsregel der Projektverfassung.

**Alternatives considered**: Implizites Lesen würde die sichtbare Konfiguration von wirksamen Rechten abkoppeln; automatisches Abschalten weiterer Rechte könnte unbeabsichtigte Rechteverluste auslösen.
