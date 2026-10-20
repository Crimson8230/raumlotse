# Quickstart: Farbtemplates prüfen

## Voraussetzungen

- Java 21, Node.js/npm und Docker für die vorhandenen Backend-Integrationstests.
- Ein bestehendes Konto (vor der Migration) und zwei nach der Migration angelegte Testkonten mit unterschiedlichen Rollen. Die Konten können über bestehende Test-Fixtures bereitgestellt werden; die Funktion selbst fügt keine Registrierung hinzu.
- Backend und Frontend gemäß Projekt-README gestartet. Für lokale Datenbank und Backend kann `docker compose up -d db backend` im Repository ausgeführt werden. Für das Frontend im Verzeichnis `frontend` `npm install` und `npm run dev` ausführen.

## Test-First-Nachweis

Für jede Implementierungseinheit zuerst einen zielgerichteten Test für das gewünschte Verhalten hinzufügen und sein Fehlschlagen festhalten. Danach die kleinste Änderung vornehmen, bis derselbe Test besteht. Wesentliche Einheiten: Migration bestehender/neuer Konten, persönliche GET/PUT/Skip-Schnittstelle und Rollenfreigabe, Erstlogin-Gate, `/settings`, Kontowechsel, Farbkontrast. Der Red-Green-Nachweis gehört in den späteren Pull Request.

## Automatisierte Prüfung nach der Umsetzung

Im Verzeichnis `backend`:

```powershell
.\mvnw.cmd test
```

Im Verzeichnis `frontend`:

```powershell
npm test
npm run lint
npm run build
```

Die geplanten Tests müssen die Daten- und Antwortregeln aus [data-model.md](data-model.md) und [contracts/theme-preference.md](contracts/theme-preference.md) prüfen. Ergänzend in beiden Schemen die vorhandene axe-core-Prüfung und eine messbare Kontrastprüfung für Text, Steuerelemente und Fokus ausführen. Alle Befehle müssen erfolgreich enden, bevor das Feature als fertig gilt.

## End-to-End-Szenarien

1. **Bestehendes Konto**: Anmelden. Erwartung: kein Erstlogin-Dialog, helles Standardschema. „Persönliche Einstellungen“ ist unabhängig von Rolle und Administrationsmodus erreichbar; `/admin/settings` bleibt als „Systemeinstellungen“ getrennt.
2. **Neues Konto, dunkle Auswahl**: Erstmals anmelden, „Dunkel“ auswählen und bestätigen. Erwartung: reguläre Seite sofort dunkel; nach Navigation, Neuladen und Anmeldung auf einem zweiten Gerät bleibt „Dunkel“ aktiv. Die Erstauswahl erscheint nicht erneut.
3. **Neues Konto, Überspringen**: Erstmals anmelden und „Später auswählen“ wählen. Erwartung: helle Oberfläche, kein erneuter Dialog bei späterer Anmeldung; Wechsel zu „Dunkel“ über `/settings` funktioniert.
4. **Kontowechsel**: Auf demselben Browser von einem Konto mit „Dunkel“ zu einem Konto mit „Hell“ wechseln. Erwartung: Auf regulären Seiten erscheint zu keinem Zeitpunkt die vorherige Kontofarbe als bestätigte Auswahl.
5. **Fehler und Sicherheit**: Einen Speicherfehler simulieren. Erwartung: verständliche Fehlermeldung, bisheriges Schema bleibt gültig, Wiederholung ist möglich. Ungültige Codes und Anfragen ohne Sitzung oder gültigen CSRF-Token ändern nichts. Kein Benutzer kann die Präferenz eines anderen Kontos adressieren.
6. **Ansichten und Bedienung**: Erstauswahl und Einstellungen mit Tastatur und Vorlesehilfe bedienen; schmale Ansicht ab 320 px und vergrößerte Darstellung prüfen. Beide Schemata auf Navigation, Formulare, Listen, Fehler, Fokus und Status testen. `/login`, anonyme `/hilfe` und die eigenständige Raumanzeige behalten das bisherige Aussehen.

Die UI-Zustände und Ausnahmen sind in [contracts/ui.md](contracts/ui.md) festgelegt. Bei parallelen Sitzungen nach einem Wechsel neu laden und prüfen, dass der zuletzt erfolgreich gespeicherte Stand erscheint.

## Implementierungs- und Prüfergebnis (20. Oktober 2026)

- Die Flyway-Migration V18 setzt vorhandene Konten auf abgeschlossene Erstauswahl und lässt neue Konten mit offener Erstauswahl starten. Die Speicherung liegt am Benutzerkonto; die API leitet dessen ID ausschließlich aus der Sitzung ab.
- Neue Konten können Hell, Dunkel oder „Später auswählen“ wählen. `/settings` steht allen angemeldeten Rollen zur Verfügung. Der gespeicherte Wert wird bei jeder Anmeldung geladen und nach erfolgreichem Speichern sofort angewendet. Raumanzeige und anonyme Seiten bleiben hell.
- Die API-Integration prüft Migration, GET/PUT/Skip, ungültige Daten, fehlende Anmeldung und CSRF, Kontotrennung, alle fünf Rollen, gesperrte Methoden, unbekannte gespeicherte Codes und den strukturierten 503-Fall. Datenbank-Schreibvorgänge derselben Person werden per Zeilensperre serialisiert.
- Frontend-Tests prüfen Erstlogin, Überspringen, Abruffehler mit Wiederholung, Einstellungen, Fehler beim Speichern, Navigation, Raumanzeige, Tastaturauswahl und axe-core-Struktur. Die beiden Paletten werden rechnerisch auf WCAG-2.1-AA-Kontrast für Text, gedämpften Text, Links, Fehler und Belegungsstatus geprüft.

### Red-Green-Nachweis

Die Tests für Migrationsreihenfolge, API, Farbschemata, Erstlogin, `/settings`, feste Farbcodes und Raumanzeige wurden vor dem jeweiligen Produktionscode eingeführt und schlugen zunächst fehl. Nach der Umsetzung bestanden sie. Der nachträglich ergänzte 503-Test schlug zunächst mit `AUTH_UNAVAILABLE` fehl, weil der simulierte Fehler schon im Authentifizierungsfilter auftrat; mit einer gezielten Simulation im Präferenzdienst bestätigt er jetzt `THEME_PREFERENCE_UNAVAILABLE`. Die Frontend-Gesamtsuite schlug nach Einführung des Preference-Gates zunächst in bestehenden Routentests fehl; die betroffenen Tests erhielten passende API-Mocks und wurden wieder grün.

### Automatisierte Freigabe

| Prüfung | Ergebnis |
|---|---|
| Backend `mvnw.cmd test` | 758 Tests, 0 Fehler (vor drei zusätzlichen API-Tests); diese separat: 8 Tests, 0 Fehler |
| Frontend `npm test` | 650 Tests, 0 Fehler (vor zusätzlichem Tastaturtest); dieser mit axe/Kontrast separat: 15 Tests, 0 Fehler |
| Frontend `npm run lint` | Erfolgreich nach Korrektur einer synchronen Zustandsänderung im Effekt |
| Frontend `npm run build` | Erfolgreich nach der Korrektur; bestehende Warnung zur Größe eines JavaScript-Bundles |

Die Szenarien 1–6 sind durch die genannten automatisierten Tests auf API-, Komponenten- und Routenebene abgedeckt. Eine Sichtprüfung im echten Browser bei 320 px, 200 % Zoom und mit einer Vorlesehilfe bleibt für die Abnahme offen; jsdom und axe-core können Rendering und tatsächliche Ansage nicht vollständig prüfen. Ein Test über zwei echte Geräte wurde nicht ausgeführt; die kontobezogene Speicherung und ein erneuter GET aus separater Anfrage sind integriert geprüft.

| Erfolgsmaß | Automatisierter Nachweis | Für die Abnahme offen |
|---|---|---|
| SC-001 | Auswahl und Überspringen erreichen die Zielseite in Routentests. | Nutzertest mit mindestens 90 % der Testpersonen und 60-Sekunden-Messung. |
| SC-002 | Migration und Erstlogin-Gate prüfen neue und bestehende Konten. | Stichprobe mit produktionsnah angelegten Konten. |
| SC-003 | Navigations- und API-Tests prüfen alle Rollen. | Bedienung mit realen Rollen im Browser. |
| SC-004 | Dauerhafte Speicherung, erneuter GET und Kontotrennung sind integriert geprüft. | Zwei reale Geräte und sichtbarer Kontowechsel. |
| SC-005 | Kontrastrechnung und axe-core-Struktur bestehen. | Visuelle Prüfung aller zentralen Seiten bei 320 px/200 % und Vorlesehilfe. |
| SC-006 | Fehlgeschlagene PUT-/Skip-Aktionen behalten den bestätigten Stand und zeigen einen Alert. | Sichtprüfung der Fehlerrückmeldung im Browser. |

### Konvergenzaufgaben T040–T042 (20. Oktober 2026)

- **T040:** Eine erneute Anmeldung desselben Kontos verwendet eine neue Authentifizierungsidentität. Der Präferenzkontext zeigt bis zum neuen GET nur den neutralen Ladestatus und übernimmt den zuletzt gespeicherten Serverstand. Der neue Routentest schlug vor der Änderung fehl, weil er sofort die alte geschützte Seite sah; danach bestand er. Ein erster Korrekturversuch mit einem Schlüssel am gesamten Routenbaum unterbrach Login-Formulare. Die endgültige Lösung bindet nur den bestätigten Präferenzstand an die aktuelle Identität und lässt die Routen montiert.
- **T041:** Die sichtbaren Vorschauen behalten ihre dekorativen Farbflächen, während die zugehörigen Radio-Optionen über `aria-describedby` eine verständliche Beschreibung für Vorlesehilfen erhalten. Beide Beschreibungsprüfungen schlugen zunächst fehl und bestanden nach der Ergänzung.
- **T042:** Die Benutzernavigation darf am schmalen Viewport umbrechen und ist auf die verfügbare Breite begrenzt. Der CSSOM-Test für den Umbruch schlug zunächst fehl und bestand anschließend. Die tatsächliche Darstellung bei 320 px bleibt Teil der manuellen Abnahme aus T036.
- **Abschlussprüfung:** `npm test` besteht mit 654 Tests in 94 Dateien; `npm run lint` und `npm run build` bestehen. Ein paralleler erster Gesamtlauf hatte zeitabhängige Fehler und eine echte Login-Regression des ersten Korrekturversuchs gezeigt. Nach dessen Korrektur bestanden die betroffenen Tests und der erneute Gesamtlauf ohne gleichzeitige Werkzeuge.
