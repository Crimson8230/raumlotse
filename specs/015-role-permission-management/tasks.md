# Tasks: Rollenrechte verwalten

**Input**: Design documents from `specs/015-role-permission-management/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: Verpflichtend gemäß `.specify/memory/constitution.md`: Für jede Änderung zuerst einen fehlschlagenden Test ausführen, danach Produktionscode schreiben und den Test erneut ausführen. Bestehende grüne Regressionstests ergänzen die neuen roten Tests.

**Organization**: Aufgaben sind nach User Story geordnet. `[P]` markiert nur Arbeiten an getrennten Dateien ohne gegenseitige Vorbedingung.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Kann parallel zu anderen so markierten Aufgaben derselben Stufe ausgeführt werden.
- **[Story]**: Entspricht US1 bis US5 in [spec.md](./spec.md).
- Alle Pfade sind relativ zum Repository-Stamm.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bestehende Tests und Endpunkte als Ausgangspunkt für die Änderung nutzbar machen.

- [X] T001 [P] Ergänze wiederverwendbare Testdaten für die fünf Rollen und 14 Berechtigungscodes in `backend/src/test/java/at/mci/igp/raumlotse/PermissionTestData.java`; keine produktive Rechteentscheidung in der Testhilfe nachbauen.
- [X] T002 [P] Ergänze typisierte Rollen-/Rechteantworten für Komponententests in `frontend/src/test/permissionFixtures.ts`, einschließlich Viewer-nur-Lesen, Admin und Mehrfachrollen.
- [X] T003 Gleiche alle aktuellen Controller-Mappings mit `specs/015-role-permission-management/contracts/authorization-matrix.md` ab und ergänze dort vor Implementierungsbeginn fehlende Methoden oder Pfade.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Dauerhafte Rechte, sichere Anfangsdaten und aktuelle Rechteabfrage als Grundlage aller Geschichten.

**CRITICAL**: Neue Story-Funktionen beginnen erst nach dieser Phase. T004 und T005 müssen vor T006 bis T009 rot ausgeführt werden.

- [X] T004 [P] Schreibe zunächst fehlschlagende Migrations-/Bootstrap-Integrationstests in `backend/src/test/java/at/mci/igp/raumlotse/RolePermissionMigrationIntegrationTest.java`: genau fünf feste `role_code`-Zeilen, `permissions_version` nichtnegativ und initial `0`, eindeutiges `(role_code, permission_code)`, ausschließlich 14 bekannte Codes, FR-007-Startrechte, unveränderte Bestandsrollen sowie atomar `UserRoleState(0)` und ausschließlich Viewer für neue Konten; prüfe die lokale Erst-Admin-Ausnahme und Neustart ohne erneute Rollenzuteilung.
- [X] T005 [P] Schreibe zunächst fehlschlagende Repository-/Sicherheitstests in `backend/src/test/java/at/mci/igp/raumlotse/RolePermissionSnapshotIntegrationTest.java`: Vereinigungsmenge für Mehrfachrollen, aktuell gespeicherter Snapshot bei der nächsten Anfrage, fehlende/ungültige Konfiguration ohne Freigabe und keine doppelte Rolle-Recht-Freigabe.
- [X] T006 Setze die neue Flyway-Migration in `backend/src/main/resources/db/migration/V16__create_role_permissions.sql` um: `role_code` genau `ADMIN`, `UNIVERSITY_STAFF`, `STUDENT`, `LECTURER`, `VIEWER`; `permission_code` genau `READ`, `RESERVE`, `OWN_RESERVATION_MANAGE`, `OTHER_RESERVATION_MANAGE`, `OWN_ACTIVE_DEVICE_CONTROL`, `BUILDING_MANAGE`, `FLOOR_MANAGE`, `EQUIPMENT_TYPE_MANAGE`, `ROOM_MANAGE`, `MAP_MANAGE`, `ROOM_PLACEMENT_MANAGE`, `CONNECTION_MANAGE`, `STATISTICS_READ`, `RESERVATION_MAINTENANCE`; Primärschlüssel `(role_code, permission_code)`; `permissions_version` nichtnegativ und initial `0`; Viewer nur `READ`, drei Fachrollen mit vier Grundrechten, Admin mit allen 14; vorhandene Rollenzuordnungen unverändert; neue `user_account`-Datensätze erhalten `UserRoleState(0)` und ausschließlich `VIEWER` atomar.
- [X] T007 Passe die lokale Erst-Admin-Provisionierung in `backend/src/main/java/at/mci/igp/raumlotse/config/LocalAuthFixtureConfiguration.java` an: nur ein gerade neu angelegtes Fixture-Konto darf den Viewer-Standard gezielt durch `ADMIN` ersetzen; vorhandene Konten und später geänderte Rollen bleiben bei Neustart unverändert.
- [X] T008 Implementiere den festen 14er-Katalog in `backend/src/main/java/at/mci/igp/raumlotse/domain/PermissionCode.java` und den versionierten Rollenrechte-Snapshot samt aktueller Vereinigungsmenge in `backend/src/main/java/at/mci/igp/raumlotse/repository/RolePermissionRepository.java`; `ROLE_MANAGEMENT` ist kein speicherbarer Code.
- [X] T009 Implementiere die anfragebezogene, bei Speicherfehlern verweigernde Rechteentscheidung in `backend/src/main/java/at/mci/igp/raumlotse/service/EffectivePermissionService.java` und ergänze Readiness-/Fehlerbehandlung in `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleReadinessCheck.java` und `backend/src/main/java/at/mci/igp/raumlotse/exception/UserRoleExceptionHandler.java`; prüfe danach T004 und T005 grün.

**Checkpoint**: Fünf Rollen und 14 Rechte sind gespeichert; aktuelle Rechte lassen sich sicher und ohne Sitzungs-Cache ermitteln.

---

## Phase 3: User Story 1 - Rechte je Rolle verwalten (Priority: P1) 🎯 MVP

**Goal**: Admins können alle Rechte je Rolle ansehen, eine gültige Gesamtauswahl speichern und eine beispielhafte Verwaltungsfreigabe tatsächlich nutzen.

**Independent Test**: `BUILDING_MANAGE` bei University Staff ändern, erneut lesen und Gebäudeänderung mit/ohne Recht prüfen; Nicht-Admin, ungültige Auswahl und veraltete Version werden verweigert.

### Tests for User Story 1

- [X] T010 [P] [US1] Schreibe zuerst fehlschlagende API-/Konkurrenztests in `backend/src/test/java/at/mci/igp/raumlotse/RolePermissionIntegrationTest.java`: fünf Rollen, 14 Optionen, Admin-only GET/PUT, vollständige Ersetzung, leere Auswahl, unveränderter Stand ohne Versionswechsel, veralteter Stand auch bei No-op als `409 STALE_ROLE_PERMISSIONS`, unbekannter/duplizierter Code und `ROLE_MANAGEMENT` als `400`, unbekannte Rolle als `404`, `READ`-Abhängigkeit in derselben Rolle und keine Teiländerung; prüfe `BUILDING_MANAGE` an einer Gebäudeänderung.
- [X] T011 [P] [US1] Schreibe zuerst fehlschlagende Seiten-/API-Tests in `frontend/src/pages/RolePermissionPage.test.tsx` und `frontend/src/API/rolePermissions.test.ts`: fünf Rollen, alle 14 Rechte, geschützter Hinweis, Laden/Fehler/Abbrechen/Speichern, ungültiges `READ`-Paar, Konflikt mit bewusstem Neuladen und kein stilles Überschreiben.

### Implementation for User Story 1

- [X] T012 [US1] Implementiere versionierte vollständige Rechteersetzung mit Sperre der betroffenen Rolle, aktuellem Admin-Check nach Sperre, Validierung von 0–14 eindeutigen Codes und `READ`-Abhängigkeit in `backend/src/main/java/at/mci/igp/raumlotse/service/RolePermissionService.java`; `expectedVersion` ist nichtnegativer kanonischer Dezimaltext und eine tatsächliche Änderung erhöht die Version genau um eins.
- [X] T013 [US1] Implementiere die Vertragsformen und GET/PUT-Endpunkte aus `contracts/role-permissions-api.md` in `backend/src/main/java/at/mci/igp/raumlotse/dto/RolePermissionsResponse.java`, `backend/src/main/java/at/mci/igp/raumlotse/dto/RolePermissionsUpdateRequest.java` und `backend/src/main/java/at/mci/igp/raumlotse/controller/RolePermissionController.java`; schütze alle Antworten gegen Caching und alle Aufrufe mit aktueller Admin-Rolle.
- [X] T014 [P] [US1] Ergänze feste Client-Typen und API-Aufrufe einschließlich versioniertem PUT in `frontend/src/types/permission.ts` und `frontend/src/API/rolePermissions.ts` gemäß `contracts/role-permissions-api.md`.
- [X] T015 [US1] Baue die Rollenrechte-Seite mit gespeicherten Zuständen, 14 einzeln wählbaren Rechten, festem Admin-Hinweis und Konfliktbehandlung in `frontend/src/pages/RolePermissionPage.tsx` und `frontend/src/pages/RolePermissionPage.css`.
- [X] T016 [US1] Registriere `/admin/roles` nur für Admins im Administrationsmodus in `frontend/src/App.tsx` und ergänze den Admin-Navigationslink in `frontend/src/components/Navigation/Navigation.tsx`.
- [X] T017 [US1] Ersetze die pauschale Gebäudefreigabe exemplarisch durch aktuelle `BUILDING_MANAGE`-Prüfung in `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java`; unbekannte neue Aktionspfade bleiben verweigert, die übrigen noch nicht umgestellten Verwaltungsaktionen behalten bis US4 ihre bisherige Admin-Schranke.
- [X] T018 [US1] Führe die neuen Backend- und Frontend-Tests aus T010/T011 sowie die bestehenden Nutzerrollen- und Gebäuderegressionstests aus `backend/src/test/java/at/mci/igp/raumlotse/UserRoleIntegrationTest.java` und `backend/src/test/java/at/mci/igp/raumlotse/MasterDataAuthorizationIntegrationTest.java` aus und behebe Abweichungen innerhalb der US1-Dateien.

**Checkpoint**: Die Rollenrechteverwaltung ist als begrenzter MVP bedienbar und `BUILDING_MANAGE` wirkt serverseitig.

---

## Phase 4: User Story 2 - Mehrere Rollen zuweisen und gemeinsam nutzen (Priority: P1)

**Goal**: Mehrfachrollen, aktuelle Vereinigungsmenge und Viewer als Standard werden für Nutzer und Oberfläche sichtbar.

**Independent Test**: Student und University Staff mit verschiedenen gespeicherten Rechten zuweisen; `/api/auth/roles` zeigt ihre Vereinigung, Entzug wirkt in derselben Sitzung, neues Konto startet ausschließlich mit Viewer/`READ`.

### Tests for User Story 2

- [X] T019 [P] [US2] Schreibe zuerst fehlschlagende Integrationstests in `backend/src/test/java/at/mci/igp/raumlotse/EffectivePermissionIntegrationTest.java`: alle 31 nichtleeren Rollenkombinationen, additiver `/api/auth/roles`-Vertrag mit `permissions` und `canUseAdminMode`, Entzug bei der nächsten Anfrage ohne Neuanmeldung, Viewer nur `READ`, keine impliziten Rechte und bisherige Rollenzuordnungs-Versionierung.
- [X] T020 [P] [US2] Schreibe zuerst fehlschlagende Frontend-Tests in `frontend/src/auth/useCurrentRoles.test.tsx` und `frontend/src/pages/HomePage.test.tsx` für effektive Rechte, Viewer-Leseansicht, leeren Funktionszustand, Fokus-/Navigationsaktualisierung und verweigernde Darstellung bei Ladefehlern.

### Implementation for User Story 2

- [X] T021 [US2] Erweitere `GET /api/auth/roles` in `backend/src/main/java/at/mci/igp/raumlotse/controller/CurrentRolesController.java` um die aktuelle `permissions`-Vereinigung und `canUseAdminMode`, ohne `roles`, `ready` oder `adminMode` zu entfernen; sichere einen konsistenten Rollen-/Rechte-Snapshot pro Anfrage.
- [X] T022 [P] [US2] Erweitere `frontend/src/API/userRoles.ts`, `frontend/src/auth/useCurrentRoles.ts` und `frontend/src/auth/useAdminMode.ts` um typisierte effektive Rechte, Modus-Eignung und Aktualisierung nach Rollen-/Rechteänderung; bei fehlgeschlagener Abfrage keine Funktion optimistisch freigeben.
- [X] T023 [US2] Passe die Nutzeransicht in `frontend/src/pages/HomePage.tsx`, `frontend/src/pages/RoomListPage.tsx` und `frontend/src/components/Navigation/Navigation.tsx` an: Viewer sieht nur Lesewege, ein Nutzer ohne Rechte erhält einen verständlichen Zustand, Rollenverwaltung bleibt nur für Admin sichtbar.
- [X] T024 [US2] Führe T019/T020 sowie `backend/src/test/java/at/mci/igp/raumlotse/UserRoleConcurrentUpdateIntegrationTest.java` und `frontend/src/pages/UserRolePage.test.tsx` aus; prüfe, dass Mehrfachrollen und die vorhandene Admin-Zuordnung unverändert funktionieren.

**Checkpoint**: Jede Sitzung nutzt die aktuelle Vereinigungsmenge; neue und bestehende Viewer erhalten die festgelegte Anfangswirkung.

---

## Phase 5: User Story 3 - Reservierungsaktionen unterscheiden (Priority: P1)

**Goal**: Lesen, Neuanlage, eigene/fremde Bearbeitung und eigene aktive Gerätebedienung werden getrennt und mit Besitz-/Statusregeln durchgesetzt.

**Independent Test**: Nutzer A erstellt eine Buchung. A, Nutzer B und ein Admin mit unterschiedlichen Rechten können jeweils nur ihre erlaubten Detail-, Änderungs- und Geräteaktionen ausführen; fremde Belegungsdaten bleiben anonym.

### Tests for User Story 3

- [X] T025 [P] [US3] Schreibe zuerst fehlschlagende Backend-Tests in `backend/src/test/java/at/mci/igp/raumlotse/ReservationPermissionIntegrationTest.java` und `backend/src/test/java/at/mci/igp/raumlotse/service/ReservationAccessPolicyTest.java`: `READ`, `RESERVE`, eigene/fremde Details und alle Änderungs-/Statusaktionen getrennt; bestehender Viewer kann eine frühere eigene Buchung lesen, aber ohne Bearbeitungsrecht nicht ändern; fehlendes Fremdrecht gibt für vorhandene und unbekannte ID denselben 404; Raum-Belegungslisten enthalten keine fremden Personendaten; fehlender Ersteller ist fremd; Admin ohne Fremdrecht hat keinen Override; Gerät nur mit eigenem aktivem Termin und gesondertem Recht.
- [X] T026 [P] [US3] Schreibe zuerst fehlschlagende UI-Tests in `frontend/src/pages/RoomDetailPage.test.tsx`, `frontend/src/components/ReservationList/ReservationList.test.tsx` und `frontend/src/pages/RoomDeviceControlPage.test.tsx` für getrennte Buchungs-, Eigen-, Fremd- und Geräteschaltflächen sowie gesperrte Direktansichten.

### Implementation for User Story 3

- [X] T027 [US3] Ersetze den Admin-Override und die bisherige gemeinsame Eigentümerprüfung in `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationAccessPolicy.java` und `backend/src/main/java/at/mci/igp/raumlotse/service/ReservationService.java` durch `OWN_RESERVATION_MANAGE` oder `OTHER_RESERVATION_MANAGE`; eigene Detailansicht braucht `READ`, fremde Einzel-Details das Fremdrecht, Belegungslisten bleiben für Fremde stets redigiert.
- [X] T028 [US3] Gib aktuelle Rechte an Reservierungsaktionen weiter und klassifiziere Lesen/Reservieren/Geräte in `backend/src/main/java/at/mci/igp/raumlotse/controller/ReservationController.java`, `backend/src/main/java/at/mci/igp/raumlotse/controller/RoomDeviceController.java` und `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java`; die spezielle manuelle Wartungsroute bleibt für US4 gesondert geschützt.
- [X] T029 [US3] Erhalte die bestehende eigene-aktive-Reservierung-Regel und ergänze das gesonderte Geräterecht für Lesen und Bedienung in `backend/src/main/java/at/mci/igp/raumlotse/service/RoomDeviceService.java`.
- [X] T030 [US3] Trenne Lese-, Neuanlage-, Eigen-, Fremd- und Gerätesichtbarkeit in `frontend/src/pages/RoomDetailPage.tsx`, `frontend/src/components/ReservationList/ReservationList.tsx`, `frontend/src/components/ReservationForm/ReservationForm.tsx` und `frontend/src/pages/RoomDeviceControlPage.tsx` anhand aktueller Rechte und serverseitigem `ownedByMe`.
- [X] T031 [US3] Führe T025/T026 sowie `backend/src/test/java/at/mci/igp/raumlotse/ReservationOwnershipIntegrationTest.java`, `backend/src/test/java/at/mci/igp/raumlotse/ReservationCreationIntegrationTest.java` und die Geräte-Autorisierungstests aus; beseitige Abweichungen in US3-Dateien.

**Checkpoint**: Buchungen und Raumgeräte folgen getrennten Rechten sowie unveränderten Besitz- und Statusregeln.

---

## Phase 6: User Story 5 - Admin-Zugang sichern (Priority: P1)

**Goal**: Die Rollenverwaltung bleibt unverlierbar an Admin gebunden und mindestens ein Admin bleibt erhalten.

**Independent Test**: Einziger Admin kann sich nicht selbst entziehen; mit zwei Admins ist Entzug bei einem zulässig; Admin ohne konfigurierbare Rechte erreicht weiterhin Rollenverwaltung, Nicht-Admin mit allen anderen Rechten nie.

### Tests for User Story 5

- [X] T032 [P] [US5] Schreibe zuerst fehlschlagende Sicherheits-/Konkurrenztests in `backend/src/test/java/at/mci/igp/raumlotse/LastAdminPermissionSafetyIntegrationTest.java`: Admin mit leerer 14er-Auswahl verwaltet weiterhin Nutzerrollen und Rollenrechte; Nicht-Admin mit allen 14 Rechten kann beides weder lesen noch ändern; Selbstentzug/gleichzeitiger Entzug des letzten Admins scheitern ohne Teiländerung; `ROLE_MANAGEMENT` kann keiner Rolle im Rechte-PUT zugewiesen oder entzogen werden.
- [X] T033 [P] [US5] Schreibe zuerst fehlschlagende UI-Tests in `frontend/src/pages/RolePermissionPage.test.tsx` und `frontend/src/pages/UserRolePage.test.tsx` für unveränderliche Admin-Befugnis, Nicht-Admin-Sperre trotz Verwaltungsrechten und verständlichen letzten-Admin-/Selbstentzug-Hinweis.

### Implementation for User Story 5

- [X] T034 [US5] Prüfe die durch T032 getestete letzte-Admin-Sperre unter `role_mutation_guard` in `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleSafety.java` und `backend/src/main/java/at/mci/igp/raumlotse/service/UserRoleService.java`; korrigiere nur nachgewiesene Lücken, damit der bestehende Schutz auch bei gleichzeitigen Rollen- und Rechteänderungen erhalten bleibt.
- [X] T035 [US5] Sichere `backend/src/main/java/at/mci/igp/raumlotse/service/RolePermissionService.java`, `frontend/src/pages/RolePermissionPage.tsx` und `frontend/src/auth/RequireAdminMode.tsx` so ab, dass die feste Admin-Befugnis nur angezeigt, niemals als 15. Recht gespeichert oder an Nicht-Admins durchgereicht wird.
- [X] T036 [US5] Führe T032/T033 sowie `backend/src/test/java/at/mci/igp/raumlotse/UserRoleConcurrentUpdateIntegrationTest.java` und `backend/src/test/java/at/mci/igp/raumlotse/UserRoleIntegrationTest.java` aus; prüfe Einzel- und Konkurrenzfälle mit genau einem verbleibenden Admin.

**Checkpoint**: Konfiguration kann den letzten Admin nicht aussperren; Rollenverwaltung bleibt ausschließlich Admin-Aufgabe.

---

## Phase 7: User Story 4 - Verwaltungsbereiche gezielt freigeben (Priority: P2)

**Goal**: Alle bestehenden Verwaltungsbereiche nutzen ihre eigenen Rechte; Modus und Sammelseiten zeigen nur erlaubte Aktionen.

**Independent Test**: Staff mit `READ` und `ROOM_MANAGE` kann Räume pflegen, aber keine Gebäude, Stockwerke, Ausstattung, Karten, Positionen, Verbindungen, Statistik, Wartung oder Rollen; direkter API-Zugriff folgt denselben Rechten auch bei ausgeschaltetem Modus.

### Tests for User Story 4

- [X] T037 [P] [US4] Schreibe zuerst fehlschlagende Matrix-/Integrationstests in `backend/src/test/java/at/mci/igp/raumlotse/security/AuthorizationMatrixTest.java` und `backend/src/test/java/at/mci/igp/raumlotse/ManagementPermissionIntegrationTest.java`: jedes Controller-Mapping aus `contracts/authorization-matrix.md` ist klassifiziert; je der neun Verwaltungsrechte mindestens eine erlaubte und verweigerte Aktion, auch GET-Statistik, GET-ungeplatzierte Räume und manueller Wartungslauf; unbekannte Aktion verweigert, `HEAD`/`OPTIONS` ohne Umgehung, keine Teiländerung oder privaten Logdaten.
- [X] T038 [P] [US4] Schreibe zuerst fehlschlagende Modus-/Navigations- und Routentests in `backend/src/test/java/at/mci/igp/raumlotse/controller/AdminModeControllerTest.java`, `frontend/src/components/Navigation/Navigation.test.tsx` und `frontend/src/auth/RequireAdminMode.test.tsx`: Modus für Nicht-Admin mit einem Verwaltungsrecht, Verlust der Eignung, nur erlaubte Links/Seiten, Serveraktion unabhängig vom Schalter.
- [X] T039 [P] [US4] Schreibe zuerst fehlschlagende Tests für getrennte Teilbereiche in `frontend/src/pages/MapPage.test.tsx`, `frontend/src/pages/LocationCatalogPage.test.tsx`, `frontend/src/pages/RoomListPage.test.tsx` und `frontend/src/pages/AdminStatisticsPage.test.tsx`.

### Implementation for User Story 4

- [X] T040 [US4] Ersetze in `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java` die übrige Admin-Pauschale durch die vollständige, spezifisch vor generisch geordnete Methoden-/Pfad-Zuordnung aus `contracts/authorization-matrix.md`; `STATISTICS_READ`, `ROOM_PLACEMENT_MANAGE` und `RESERVATION_MAINTENANCE` gesondert behandeln, unbekannte geschützte Aktion verweigern und Ablehnungen ohne Personen- oder Buchungsdaten protokollieren.
- [X] T041 [US4] Passe `backend/src/main/java/at/mci/igp/raumlotse/service/AdminModeService.java` und `backend/src/main/java/at/mci/igp/raumlotse/controller/AdminModeController.java` an: Einschalten nur bei Admin oder einem der neun Verwaltungsrechte, Ausschalten für Angemeldete, wirksames Ende bei Rechteverlust; Modus bleibt für API-Autorisierung unbeachtlich.
- [X] T042 [US4] Erweitere `frontend/src/auth/RequireAdminMode.tsx`, `frontend/src/App.tsx` und `frontend/src/components/Navigation/Navigation.tsx` um Rechte je Admin-Route, `canUseAdminMode`, nur erlaubte Links und die feste Admin-Sperre für `/admin/users` und `/admin/roles`.
- [X] T043 [US4] Schütze Gebäude, Stockwerke und Ausstattungsarten innerhalb der gemeinsamen Seite jeweils separat in `frontend/src/pages/LocationCatalogPage.tsx`, `frontend/src/components/BuildingCatalog/BuildingCatalog.tsx` und `frontend/src/components/EquipmentCatalog/EquipmentCatalog.tsx`.
- [X] T044 [US4] Schütze Karten, Raumpositionen und Verbindungen innerhalb der gemeinsamen Kartenseite separat in `frontend/src/pages/MapPage.tsx`, `frontend/src/components/FloorMap/MapUploadControl.tsx` und `frontend/src/components/ConnectionCatalog/ConnectionCatalog.tsx`; bestehende schreibgeschützte Karte bei `READ` bleibt nutzbar.
- [X] T045 [US4] Schütze Raumaktionen und Statistik getrennt in `frontend/src/pages/RoomListPage.tsx`, `frontend/src/pages/RoomFormPage.tsx` und `frontend/src/pages/AdminStatisticsPage.tsx`; zeige keine Verwaltungsaktion allein wegen aktivem Modus.
- [X] T046 [US4] Führe T037–T039 sowie bestehende Tests `backend/src/test/java/at/mci/igp/raumlotse/MasterDataAuthorizationIntegrationTest.java`, `backend/src/test/java/at/mci/igp/raumlotse/MapAuthorizationIntegrationTest.java` und die Frontend-Seitentests aus; prüfe jede der neun Verwaltungsaktionen bei Modus an und aus.

**Checkpoint**: Alle vorhandenen Verwaltungsaktionen sind genau einem Recht zugeordnet; Sammelseiten und Direktzugriffe bleiben konsistent.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Rechteentzug, Dokumentation und vollständige Abnahme absichern.

- [X] T047 Schreibe zuerst einen fehlschlagenden Client-Test in `frontend/src/API/client.test.ts`: `403 PERMISSION_REQUIRED` aktualisiert effektive Rechte, ohne `404` für fremde Reservierungen oder `401` für Anmeldung falsch umzudeuten.
- [X] T048 Passe `frontend/src/API/client.ts` so an, dass ein verweigerter Funktionsaufruf eine aktuelle Rechteabfrage auslöst und keine zuvor sichtbare Aktion erneut optimistisch freigibt; prüfe T047 grün.
- [X] T049 Aktualisiere die Beschreibung der lokalen Fixture und der Viewer-Startrechte in `README.md` sowie die Ende-zu-Ende-Prüfschritte in `specs/015-role-permission-management/quickstart.md` entsprechend dem tatsächlich umgesetzten Verhalten.
- [X] T050 Führe `./mvnw.cmd test` im Verzeichnis `backend/` sowie `npm test`, `npm run lint` und `npm run build` in `frontend/` aus; dokumentiere Ergebnisse und verbleibende Grenzen in `specs/015-role-permission-management/quickstart.md`.
- [X] T051 Gleiche alle 16 funktionalen Anforderungen, 14 Rechte und die neun Verwaltungsbereiche gegen `specs/015-role-permission-management/spec.md`, `specs/015-role-permission-management/contracts/authorization-matrix.md` und die ausgeführten Tests ab; ergänze fehlende Abnahmeschritte in `specs/015-role-permission-management/quickstart.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup → Foundational**: T001–T003 vor T004–T009; die vorhandenen Technologien und Testwerkzeuge werden weiterverwendet.
- **Foundational → Stories**: T004/T005 zuerst rot, T006–T009 anschließend grün. Alle Geschichten benötigen gespeicherte Rechte und aktuelle Snapshots.
- **US1 und US2**: Nach Foundational parallel möglich, soweit Dateien getrennt bleiben; US1 liefert den administrativen Rechteschalter, US2 das wirksame Nutzerprofil.
- **US3**: Benötigt den aktuellen Rechte-Snapshot aus US2; die Reservierungs- und Gerätepfade sind danach eigenständig prüfbar.
- **US5**: Benötigt die neue Rechteverwaltung aus US1; kann unabhängig von US3 bearbeitet werden.
- **US4**: Benötigt US1/US2 für gespeicherte Rechte, US3 für die vollständige Klassifikation im gemeinsamen Zugriffsfilter und US5 für feste Admin-Grenzen.
- **Polish**: Nach allen Geschichten; T047 vor T048, dann Dokumentation und vollständige Abnahme.

### User Story Dependencies

| Story | Voraussetzung | Unabhängiger Abschlussnachweis |
| --- | --- | --- |
| US1 (P1) | Foundational | Rechteauswahl speichern und `BUILDING_MANAGE` als Beispiel durchsetzen |
| US2 (P1) | Foundational | 31 Vereinigungen, Viewer-Standard und nächster Request |
| US3 (P1) | US2 | Eigene/fremde Buchungs- und Gerätefälle einschließlich 404/Redaktion |
| US5 (P1) | US1 | Einziger Admin geschützt; Nicht-Admin mit allen Rechten ausgeschlossen |
| US4 (P2) | US1, US2, US3, US5 | Alle neun Verwaltungsrechte und zusammengesetzte Seiten getrennt geschützt |

### Within Each User Story

1. Tests der jeweiligen Phase schreiben und rot ausführen.
2. Datentypen/Modelle, dann Repository und Service, danach Endpunkte und Oberfläche umsetzen.
3. Neue Tests grün ausführen, bestehende betroffene Regressionstests prüfen, erst dann zum nächsten abhängigen Abschnitt gehen.

### Parallel Opportunities

- T001 und T002; T004 und T005; T010 und T011; T019 und T020; T025 und T026; T032 und T033; T037 bis T039 bearbeiten unterschiedliche Testdateien.
- Nach den roten US1-Tests kann T014 an Frontend-Typen/API parallel zu T012/T013 im Backend beginnen. US1 und US2 können nach dem Fundament getrennt bearbeitet werden; die gemeinsame Navigation wird erst nach Zusammenführung geändert.
- US3 und US5 können nach ihren jeweiligen Vorbedingungen an getrennten Buchungs- bzw. Rollensicherheitsdateien laufen. Bei gemeinsamem `RoleAccessFilter.java`, `RequireAdminMode.tsx` oder `Navigation.tsx` müssen Änderungen sequenziell integriert werden.

### Parallel Example: User Story 1

```text
Parallel nach Foundational: T010 Backend-Vertragstest in backend/src/test/java/at/mci/igp/raumlotse/RolePermissionIntegrationTest.java
Parallel nach Foundational: T011 Frontend-Seitentest in frontend/src/pages/RolePermissionPage.test.tsx
Nach roten Tests getrennte Dateien: T012/T013 Backend-Service/Controller und T014 Frontend-Typen/API
Gemeinsame Integration danach: T015–T018
```

### Parallel Example: User Story 2

```text
Parallel nach Foundational: T019 Backend-Vereinigungstest und T020 Frontend-Rechteanzeige-Test
Nach roten Tests: T021 Serverantwort und T022 Client-Hook in getrennten Dateien
Gemeinsame Integration danach: T023–T024
```

### Parallel Example: User Story 3

```text
Parallel nach US2: T025 Backend-Besitz-/Rechtetest und T026 Frontend-Buchungstest
Nach roten Tests: T027–T029 Serverzugriff, anschließend T030 Oberfläche
Gemeinsame Integration danach: T031
```

### Parallel Example: User Story 5

```text
Parallel nach US1: T032 Backend-Sicherheitstest und T033 Frontend-Admin-Test
Nach roten Tests: T034 bestehende Admin-Sperre und T035 feste UI-/Service-Regel
Gemeinsame Integration danach: T036
```

### Parallel Example: User Story 4

```text
Parallel nach US1/US2/US3/US5: T037 Backend-Matrix, T038 Modus/Navigation, T039 Teilbereichsseiten
Nach roten Tests: T040/T041 Serverrechte und Modus; T042–T045 Oberflächenprüfungen in den genannten Seiten
Gemeinsame Abnahme danach: T046
```

---

## Implementation Strategy

### MVP First (US1)

1. T001–T009 schaffen Testdaten, Migration und aktuelle Rechteermittlung.
2. T010/T011 zuerst rot; T012–T017 setzen die Rollenrechteverwaltung und ein tatsächlich wirksames Verwaltungsrecht um.
3. T018 validiert US1 unabhängig. Danach können weitere Rechtebereiche schrittweise folgen.

### Incremental Delivery

1. US1: Admin konfiguriert Rechte und `BUILDING_MANAGE` wirkt.
2. US2: Mehrfachrollen, Viewer-Startrecht und aktuelle wirksame Rechte.
3. US3: Buchungs-, Eigentums- und Geräteberechtigungen.
4. US5: Letzter Admin und feste Rollenverwaltung umfassend abgesichert.
5. US4: Sämtliche Verwaltungsrechte, Modus und gemischte Seiten vollständig umgestellt.
6. Phase 8: Rechteentzug im Client, Regressionen, Dokumentation und Abnahme.

## Notes

- `[P]` bedeutet verschiedene Dateien ohne unerfüllte Vorbedingung; die Markierung ist keine Anweisung, parallel arbeitende Agents zu starten.
- Aufgaben mit vorhandenen Testdateien dürfen diese erweitern; bei neuen Dateien ist der angegebene Pfad der Zielpfad.
- Für Migrations-, API- und Frontend-Änderungen muss der rote Testlauf vor Produktionscode nachvollziehbar sein.
- Die Verträge in `specs/015-role-permission-management/contracts/` sind maßgeblich; bei einer notwendigen Vertragsänderung wird der Vertrag vor dem betreffenden Produktionscode aktualisiert.

## Phase 9: Convergence

- [X] T052 CRITICAL Schreibe zuerst einen fehlschlagenden Test für die Bearbeitung von Reservierungsmetadaten in `frontend/src/components/ReservationList/ReservationList.test.tsx`; validiere das gesamte Editierformular anschließend mit einem Schema in `frontend/src/components/ReservationList/ReservationList.tsx` und einer zugehörigen Schemadatei, insbesondere ganzzahlige Teilnehmerzahlen, Pflichtfelder und Längengrenzen, bevor der API-Aufruf erfolgt, per Constitution IV (contradicts).
- [X] T053 CRITICAL Ergänze zuerst fehlschlagende Filter-/Logtests für unbekannte geschützte Aktionen und fehlgeschlagene Rechte-Speicherabfragen; protokolliere diese Ausgänge in `backend/src/main/java/at/mci/igp/raumlotse/service/RoleAccessFilter.java` strukturiert mit Methode, normalisiertem Pfad, Kategorie und Status, ohne Buchungsinhalt oder Kontodaten, per Constitution V und T040 (contradicts).
- [X] T054 Schreibe zuerst einen Integrationstest, der die Konfiguration einer nicht zugewiesenen festen Rolle entfernt oder ungültig macht und dann eine ansonsten erlaubte geschützte Anfrage stellt; verweigere bei unvollständiger globaler Rollenrechte-Konfiguration die Freigabe mit `503 ROLE_MANAGEMENT_UNAVAILABLE` in `RolePermissionRepository`/`EffectivePermissionService`, per plan: fail-closed rights und T005 (partial).
- [X] T055 Schreibe zuerst Frontend-Tests für Nutzer mit `OWN_ACTIVE_DEVICE_CONTROL`, aber ohne eigene aktive Reservierung, sowie für den direkten Aufruf von `/rooms/:roomId/control`; zeige Link und Steuerungsansicht in `RoomDetailPage.tsx`, `App.tsx` und der Gerätesteuerung nur bei aktuell bestätigter eigener aktiver Buchung, per FR-014 und FR-016 (partial).
- [X] T056 Schreibe zuerst API-Tests für einen bekannten Eigentümer ohne `OWN_RESERVATION_MANAGE` und für fremde beziehungsweise unbekannte Reservierungen; liefere bei eigener verweigerter Änderung `403 PERMISSION_REQUIRED` und bei fremdem oder unbekanntem Einzelzugriff denselben `404 RESERVATION_NOT_FOUND`-Problemkörper in `ReservationAccessPolicy` und der Fehlerbehandlung, per FR-013 und T025 (contradicts).
- [X] T057 Ersetze die Schein-Erfolgsprüfung `status != 403` in `ManagementPermissionIntegrationTest.java` durch gültige Anfragen und nachgewiesene erfolgreiche Fachaktionen für jedes der neun Verwaltungsrechte; prüfe jeweils die Verweigerung ohne Recht, unveränderte Daten und API-Verhalten bei ein- und ausgeschaltetem Modus, per SC-002 und T037 (partial).
- [X] T058 Schreibe zuerst einen Test für die Anzahl und Konsistenz der Rechte-Snapshot-Lesezugriffe innerhalb einer Anfrage; verwende in `RoleAccessFilter`, `CurrentRolesController` und betroffenen Services denselben aktuellen Anfrage-Snapshot ohne Sitzungs-Cache, wobei die Admin-Neuprüfung nach einer Schreibsperre erhalten bleibt, per plan: one snapshot per request und T021 (partial).
- [X] T059 Ergänze zuerst einen Seitentest für jede ausgewählte Nicht-Admin-Rolle; zeige in `RolePermissionPage.tsx` die feste Rollenverwaltungsbefugnis ausdrücklich als nicht verfügbar und weiterhin nicht änderbar an, per FR-010 und T015 (partial).
- [X] T060 Ergänze zuerst einen Seitentest für einen fehlgeschlagenen Reload nach bereits geladener Rollenrechteauswahl; verberge oder deaktiviere in `RolePermissionPage.tsx` den veralteten gespeicherten Stand und Speichern bis zum erfolgreichen Neuladen, per FR-008 und T015 (partial).
