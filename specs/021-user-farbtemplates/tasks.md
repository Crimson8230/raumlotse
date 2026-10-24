# Tasks: Persönliche Farbtemplates

**Input**: Design documents from `specs/021-user-farbtemplates/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/theme-preference.md](contracts/theme-preference.md), [contracts/ui.md](contracts/ui.md)

**Tests**: Pflicht gemäß Projektverfassung: Für jede Änderung zuerst einen fehlschlagenden Test schreiben und den Red-Green-Verlauf festhalten. Bestehende Tests, Lint und Build bleiben Freigabekriterien.

**Organization**: Setup und gemeinsame Grundlage gehen den unabhängig prüfbaren User Stories voraus. `[P]` bedeutet unterschiedliche Dateien und keine Abhängigkeit zwischen den so markierten Aufgaben innerhalb des Schritts.

## Phase 1: Setup (gemeinsame Vorbereitung)

**Purpose**: Die vorhandenen Projektstrukturen prüfen; keine neue Projektinitialisierung oder Bibliothek erforderlich.

- [X] T001 Prüfe die aktuell höchste Flyway-Version unter `backend/src/main/resources/db/migration/` und aktualisiere die für diese Funktion reservierte Version in `specs/021-user-farbtemplates/plan.md`, falls `V18` inzwischen belegt ist.
- [X] T002 Erfasse feste Farbwerte und betroffene Seitenstile in `specs/021-user-farbtemplates/research.md`; prüfe dafür `frontend/src/index.css` sowie die CSS-Dateien unter `frontend/src/pages/` und `frontend/src/components/`, einschließlich Raumanzeige.

---

## Phase 2: Foundational (blockiert alle User Stories)

**Purpose**: Kontobezogene Speicherung, persönliche API, typisierter Client und beide grundlegenden Farbpaletten. Erst alle zugehörigen Tests fehlschlagen lassen, dann Produktionscode ändern.

- [X] T003 [P] Schreibe zunächst fehlschlagende Migrationstests für vorhandene Konten (`theme_onboarding_completed=true`, `theme_code=NULL`), neue Konten (`theme_onboarding_completed=false`) und eindeutige Flyway-Version in `backend/src/test/java/at/mci/igp/raumlotse/ThemePreferenceMigrationIntegrationTest.java` und `backend/src/test/java/at/mci/igp/raumlotse/MigrationVersionTest.java`.
- [X] T004 [P] Schreibe zunächst fehlschlagende Integrationstests für `GET`/`PUT /api/me/theme-preference` und `POST /api/me/theme-preference/skip` einschließlich `LIGHT`/`DARK`, idempotentem Skip ohne Löschen einer Auswahl, Fremdkonto-Isolation, fehlender Sitzung/CSRF, ungültigem Body, Speicherfehler `503`, unbekanntem gespeicherten Code, parallelen Sitzungen, allen Rollen und Logs ohne personenbezogene Werte in `backend/src/test/java/at/mci/igp/raumlotse/ThemePreferenceIntegrationTest.java`.
- [X] T005 [P] Schreibe zunächst fehlschlagende Clienttests für Antwortvalidierung, GET, PUT, Skip und Fehlerweitergabe gemäß `contracts/theme-preference.md` in `frontend/src/API/themePreference.test.ts`.
- [X] T006 [P] Schreibe zunächst fehlschlagende Kontrasttests für die semantischen Hell-/Dunkel-Paare (Text, gedämpfter Text, Links/Fokus, Fehler, Bedienelemente) in `frontend/src/theme/themeContrast.test.ts`; prüfe die in `frontend/src/index.css` definierten Werte gegen WCAG 2.1 AA.
- [X] T007 Ergänze die nächste freie Migration unter `backend/src/main/resources/db/migration/V18__user_theme_preference.sql` (Dateiname gemäß T001 anpassen): `theme_code` als „optionale kurze Zeichenfolge“, „Bei neuen Schreibvorgängen nur `LIGHT` oder `DARK`; `NULL` heißt keine eigene Auswahl“; `theme_onboarding_completed` als „boolesch, nicht null“, „Bestehende Konten werden auf `true` gesetzt; neue Konten starten mit `false`“. Erfülle T003, ohne bestehende Konten in die Erstauswahl zu schicken.
- [X] T008 Erweitere `backend/src/main/java/at/mci/igp/raumlotse/domain/UserAccount.java` um `theme_code` und `theme_onboarding_completed` mit den Nullbarkeits- und Standardregeln aus `specs/021-user-farbtemplates/data-model.md`; behalte die bestehende Benutzer-ID als alleinigen Präferenzschlüssel.
- [X] T009 Definiere validierte Anfrage und Antwort (`theme` nur `LIGHT`/`DARK`; Antwort mit `effectiveTheme`, `selectedTheme`, `onboardingPending`) in `backend/src/main/java/at/mci/igp/raumlotse/dto/ThemePreferenceRequest.java` und `backend/src/main/java/at/mci/igp/raumlotse/dto/ThemePreferenceResponse.java`; `null`, fehlende oder zusätzliche Eingabefelder müssen den Vertrag einhalten.
- [X] T010 Implementiere kontogebundenes Lesen, atomares Auswählen und idempotentes Überspringen mit `LIGHT`-Fallback für unbekannte gespeicherte Codes in `backend/src/main/java/at/mci/igp/raumlotse/service/ThemePreferenceService.java`; leite die Konto-ID nur aus der Sitzung ab und erfülle T004.
- [X] T011 Implementiere GET, PUT und POST-Skip mit `200`/`400`/`401`/`403`/`503`, `Cache-Control: no-store` und vorhandener Problem-Antwort in `backend/src/main/java/at/mci/igp/raumlotse/controller/ThemePreferenceController.java`; logge keine Identität oder Anfragewerte.
- [X] T012 Ergänze für die drei persönlichen Methoden/Pfade ausdrückliche `IDENTITY`-Regeln unabhängig von `READ` und Administrationsmodus in `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java`; bestätige Ablehnung aller anderen Methoden durch T004.
- [X] T013 [P] Implementiere den typisierten und auf Antworten validierenden GET-/PUT-/Skip-Client mit bestehender CSRF-Behandlung in `frontend/src/API/themePreference.ts`; erfülle T005.
- [X] T014 [P] Ergänze in `frontend/src/index.css` einen dunklen Satz für alle vorhandenen semantischen Farbvariablen unter explizitem Theme-Attribut; erhalte das bisherige helle Schema als Standard und erfülle T006.

**Checkpoint**: Migration, persönliche API, Client und Basispaletten sind geprüft. Die beiden P1-Oberflächen können auf dieser Grundlage getrennt umgesetzt werden.

---

## Phase 3: User Story 1 – Farbschema beim ersten Login auswählen (Priority: P1) 🎯 MVP

**Goal**: Neue Konten sehen einmalig die Wahl zwischen „Hell“ und „Dunkel“ oder „Später auswählen“; bestehende Konten sehen sie nicht.

**Independent Test**: Ein neues Konto erstmals anmelden, wählen oder überspringen, Zielseite aufrufen, neu laden; Dialog erscheint genau einmal und das bestätigte Schema bleibt aktiv.

### Tests for User Story 1 – zuerst fehlschlagen lassen

- [X] T015 [P] [US1] Schreibe fehlschlagende Komponententests für benannte Vorschauen, Auswahl, Bestätigung, Skip und Tastaturbedienung in `frontend/src/pages/AppearanceOnboardingPage.test.tsx`.
- [X] T016 [P] [US1] Schreibe fehlschlagende Routentests für neues/bestehendes Konto, Präferenz-Ladestatus und Wiederholung bei Abruffehler, Direktadresse samt Suchparametern, genau einmalige Erstauswahl und sofortiges Theme nach Erfolg in `frontend/src/App.appearance-onboarding.test.tsx`.

### Implementation for User Story 1

- [X] T017 [US1] Lade die persönliche Präferenz nach Authentifizierung vor regulärem Inhalt, halte bestätigten Stand und Ladestatus pro `userId`, entferne das Theme-Attribut bei Sitzungsende/Kontowechsel und verhindere einen Zwischenzustand mit alter Kontofarbe in `frontend/src/auth/AppearanceProvider.tsx`.
- [X] T018 [US1] Sperre reguläre geschützte Inhalte bei `onboardingPending=true` zugunsten der Erstauswahl, behalte die ursprüngliche Adresse einschließlich Suchparametern und zeige bei Abruffehler eine Wiederholungsmöglichkeit in `frontend/src/auth/RequireAppearance.tsx`.
- [X] T019 [US1] Erstelle die Erstauswahl mit „Hell“, „Dunkel“, benannten Vorschauen, „Auswahl speichern“ und „Später auswählen“ in `frontend/src/pages/AppearanceOnboardingPage.tsx`; bestätigte PUT-/Skip-Antworten schließen die Auswahl ab, fehlgeschlagene Aktionen nicht.
- [X] T020 [US1] Gestalte Auswahl, Vorschau und Bestätigung responsiv ab 320 px und mit sichtbar markiertem Fokus in `frontend/src/pages/AppearanceOnboardingPage.css`.
- [X] T021 [US1] Binde Präferenzkontext und Gate in den geschützten Routenbaum ein, ohne `/login` oder anonyme `/hilfe` zu verändern, in `frontend/src/App.tsx` und `frontend/src/auth/RequireAuth.tsx`; erfülle T015–T016.
- [X] T022 [US1] Führe die Tests aus T003–T006 und T015–T016 aus, dokumentiere ihr vorheriges Fehlschlagen und anschließendes Bestehen in `specs/021-user-farbtemplates/quickstart.md`.

**Checkpoint**: User Story 1 ist allein vorführbar; persönliche Einstellungen sind für dieses MVP noch nicht erforderlich.

---

## Phase 4: User Story 2 – Farbschema in persönlichen Einstellungen ändern (Priority: P1)

**Goal**: Alle angemeldeten Benutzer erreichen `/settings` und können ihre kontobezogene Farbe jederzeit ändern.

**Independent Test**: Mit einem bestehenden Konto jeder Rolle `/settings` öffnen, Farbe ändern, neu laden, auf zweitem Gerät anmelden und danach am selben Browser das Konto wechseln.

### Tests for User Story 2 – zuerst fehlschlagen lassen

- [X] T023 [P] [US2] Schreibe fehlschlagende Navigationstests für einen stets erreichbaren Link „Persönliche Einstellungen“, auch ohne `READ`, sowie die eindeutige Beschriftung „Systemeinstellungen“ für `/admin/settings` in `frontend/src/components/Navigation/Navigation.test.tsx`.
- [X] T024 [P] [US2] Schreibe fehlschlagende Seiten-/Routentests für wirksames und gewähltes Schema, bestätigten Wechsel ohne Neuanmeldung, Neuladen, zweites Gerät und Kontowechsel in `frontend/src/pages/PersonalSettingsPage.test.tsx` und `frontend/src/App.personal-settings.test.tsx`.

### Implementation for User Story 2

- [X] T025 [US2] Ergänze den rollenunabhängigen Navigationslink und benenne den administrativen Einstellungslink um in `frontend/src/components/Navigation/Navigation.tsx`; erfülle T023.
- [X] T026 [P] [US2] Erstelle die persönliche Seite mit angezeigtem `effectiveTheme`, kenntlicher `selectedTheme`-Auswahl, bewusster Bestätigung und unmittelbarer Anwendung der erfolgreich gespeicherten Antwort in `frontend/src/pages/PersonalSettingsPage.tsx` und `frontend/src/pages/PersonalSettingsPage.css`; erfülle die Seitentests aus T024.
- [X] T027 [US2] Registriere `/settings` als authentifizierte Route außerhalb des Administrationsmodus in `frontend/src/App.tsx`; erfülle die Routentests aus T024.
- [X] T028 [US2] Führe T023–T024 sowie die persönlichen API-Integrationstests erneut aus und dokumentiere den Red-Green-Nachweis für US2 in `specs/021-user-farbtemplates/quickstart.md`.

**Checkpoint**: User Story 2 ist mit einem bestehenden Konto unabhängig vom Erstlogin-Dialog prüfbar.

---

## Phase 5: User Story 3 – Auswahl sicher und verständlich nutzen (Priority: P2)

**Goal**: Beide Schemata sind barrierearm, Fehler lassen den bestätigten Stand erkennbar, und ausgenommene Seiten bleiben im bisherigen Erscheinungsbild.

**Independent Test**: PUT-/Skip-Fehler auslösen, mit Tastatur/Vorlesehilfe bedienen, Kontraste in beiden Schemata messen und `/login`, anonyme `/hilfe` und Raumanzeige prüfen.

### Tests for User Story 3 – zuerst fehlschlagen lassen

- [X] T029 [P] [US3] Schreibe fehlschlagende Fehler- und Wiederholungstests für PUT-/Skip-Fehler sowie den zuletzt bestätigten Stand in `frontend/src/pages/AppearanceOnboardingPage.test.tsx` und `frontend/src/pages/PersonalSettingsPage.test.tsx`.
- [X] T030 [P] [US3] Ergänze fehlschlagende axe-core-/Tastaturtests für Namen, Auswahlzustand, Fokus und Fehlerrückmeldung beider Seiten in `frontend/src/a11y.test.tsx` sowie Kontrast-/Stilregressionen für betroffene Seitenstile in `frontend/src/theme/themeContrast.test.ts`; decke die festen Farbwerte aus T002 vor T033 ab.
- [X] T031 [P] [US3] Schreibe fehlschlagende Tests für feste helle Raumanzeige und für Rückkehr zum Standardschema auf anonymen Seiten bzw. nach Kontowechsel in `frontend/src/pages/RoomDisplayPage.test.tsx` und `frontend/src/App.appearance-onboarding.test.tsx`.

### Implementation for User Story 3

- [X] T032 [US3] Ergänze verständliche `role="alert"`-Fehler, Wiederholung und klare Kennzeichnung des zuletzt bestätigten Schemas in `frontend/src/pages/AppearanceOnboardingPage.tsx`, `frontend/src/pages/PersonalSettingsPage.tsx` und `frontend/src/auth/AppearanceProvider.tsx`; erfülle T029.
- [X] T033 [P] [US3] Ersetze für dunkle reguläre Ansichten relevante feste Farbwerte durch semantische Tokens in `frontend/src/pages/AdminOccupancyPage.css`, `frontend/src/pages/AdminStatisticsPage.css`, `frontend/src/pages/RolePermissionPage.css`, `frontend/src/components/RoomDeviceControls/RoomDeviceControlsDialog.css` und `frontend/src/components/CheckInQrCode/CheckInQrCode.css`; prüfe dabei Status weiterhin ohne Farbe.
- [X] T034 [P] [US3] Isoliere den bisherigen hellen Farbkontext der eigenständigen Raumanzeige trotz geschützter Route über die Routensteuerung in `frontend/src/auth/AppearanceProvider.tsx`; erfülle T031.
- [X] T035 [US3] Korrigiere erkannte Fokus-, Kontrast- und 320-px-Probleme der beiden Auswahloberflächen in `frontend/src/pages/AppearanceOnboardingPage.css` und `frontend/src/pages/PersonalSettingsPage.css`; erfülle T030 und die Kontrasttests aus T006.
- [ ] T036 [US3] Prüfe beide Schemata mit Tastatur, Vorlesehilfe, axe-core und messbaren WCAG-2.1-AA-Kontrasten auf zentralen Seiten und dokumentiere Ergebnisse sowie Fehler-/Wiederholungsverhalten in `specs/021-user-farbtemplates/quickstart.md`.

**Checkpoint**: Alle drei User Stories erfüllen die Erfolgs- und Barrierefreiheitskriterien der Spezifikation.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Vollständige Regression und nachweisbare Freigabekriterien.

- [X] T037 Führe alle Backendtests mit `backend/mvnw.cmd test` aus und behebe konkrete Regressionen in den betroffenen Dateien unter `backend/src/`; erhalte die dokumentierten API-Status und Datenschutzregeln.
- [X] T038 Führe `npm test`, `npm run lint` und `npm run build` in `frontend/` aus und behebe konkrete Regressionen in den betroffenen Dateien unter `frontend/src/`.
- [X] T039 Gehe die End-to-End-Szenarien und Erfolgsmaße aus `specs/021-user-farbtemplates/quickstart.md` und `specs/021-user-farbtemplates/spec.md` durch; trage Ergebnisse, Restbefunde und den Red-Green-Nachweis in `specs/021-user-farbtemplates/quickstart.md` ein.

---

## Dependencies & Execution Order

### Phase Dependencies

1. Phase 1 klärt Migrationsnummer und Stilumfang.
2. Phase 2 liefert die für alle Geschichten nötige Kontopräferenz, API, Client und Grundpaletten. Die Tests T003–T006 müssen vor T007–T014 fehlschlagen.
3. US1 und US2 können nach Phase 2 getrennt umgesetzt und geprüft werden; für das MVP zuerst US1 abschließen.
4. US3 prüft und vervollständigt die in US1 und US2 entstandenen Oberflächen.
5. Phase 6 folgt nach allen gewünschten Geschichten.

### User Story Dependencies

- **US1 (P1)**: Phase 2; kein Bedarf an US2 oder US3.
- **US2 (P1)**: Phase 2; nutzt dieselbe Präferenzschnittstelle, ist aber mit einem bestehenden Konto ohne US1-Dialog unabhängig testbar.
- **US3 (P2)**: Phase 2 und die Oberflächen aus US1/US2; ihre Fehler- und Zugänglichkeitsprüfungen beziehen sich auf beide.

### Parallel Opportunities

- **Grundlage**: T003, T004, T005 und T006 sind getrennte Testdateien. Nach deren Red-Nachweis können Frontend-Client T013 und Basispalette T014 parallel zur Backend-Implementierung entstehen.
- **US1**: T015 und T016 sind getrennte Tests. Nach T017 können Gate T018 und Erstauswahl T019 in verschiedenen Dateien bearbeitet werden; die Integration T021 folgt danach.
- **US2**: T023 und T024 sind getrennte Tests. Navigation T025 und persönliche Seite T026 betreffen unterschiedliche Dateien; die Route T027 integriert anschließend beides.
- **US3**: T029, T030 und T031 prüfen verschiedene Schwerpunkte; Stilprüfung T033 und Raumanzeige T034 betreffen unterschiedliche Dateien.

## Implementation Strategy

### MVP First

1. T001–T014: Grundlage mit nachweislich zunächst fehlschlagenden Tests.
2. T015–T022: Erstlogin-Auswahl als erster vorführbarer Nutzwert.
3. US1 unabhängig mit neuem, bestehendem und überspringendem Konto prüfen.

### Incremental Delivery

1. T023–T028 ergänzen persönliche Einstellungen für alle Rollen und testen sie unabhängig mit einem bestehenden Konto.
2. T029–T036 schließen Fehlerrückmeldung, Zugänglichkeit und Farbprüfung ab.
3. T037–T039 liefern Vollregression und dokumentierte End-to-End-Ergebnisse.

## Notes

- `[P]` gilt nur für voneinander unabhängige Dateien; gemeinsames Bearbeiten von `App.tsx` oder denselben Seitenstilen erfolgt nacheinander.
- Alle Produktionsänderungen erfolgen nach einem passenden fehlschlagenden Test; ein nur erfolgreich durchlaufender Bestandstest ersetzt diesen Nachweis nicht.
- Die Migrationsnummer und die dunkle Palette werden anhand des tatsächlichen Stands zu Implementierungsbeginn bestätigt.


## Phase 7: Convergence

- [X] T040 Verhindere in `frontend/src/auth/AppearanceProvider.tsx`, dass nach Sitzungsende und erneuter Anmeldung derselben `userId` ein zwischengespeichertes Farbschema vor dem erneuten GET als aktueller Stand erscheint; ergänze einen zunächst fehlschlagenden Routentest für eine zwischenzeitlich auf einem anderen Gerät geänderte Präferenz und prüfe erneute Anmeldung, Ladegrenze und unmittelbar korrektes Theme per FR-007 und US2/AC4 (partial).
- [X] T041 Ergänze für die sichtbaren Hell-/Dunkel-Vorschauen in `frontend/src/pages/AppearanceOnboardingPage.tsx` zugängliche Beschreibungen, die Vorlesehilfen über die jeweilige Option erreichen können; prüfe Namen, Beschreibung und Auswahlzustand zuerst mit einem fehlschlagenden Test in `frontend/src/pages/AppearanceOnboardingPage.test.tsx` oder `frontend/src/a11y.test.tsx` per FR-011 und US3/AC1 (contradicts).
- [X] T042 Sorge in `frontend/src/components/Navigation/Navigation.css` dafür, dass die Benutzernavigation einschließlich „Persönliche Einstellungen“ bei 320 px ohne horizontales Scrollen erreichbar bleibt; ergänze vor der CSS-Korrektur einen passenden schmalen Layout-Regressionstest und prüfe beide Farbschemata per FR-005 und Edge Case „schmale Bildschirme“ (partial).
