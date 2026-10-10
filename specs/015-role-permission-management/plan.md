# Implementation Plan: Rollenrechte verwalten

**Branch**: `015-role-permission-management` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/015-role-permission-management/spec.md`

## Summary

Die fünf festen Rollen erhalten je 14 gespeicherte, versionierte Funktionsrechte. Der Server bildet bei jeder geschützten Anfrage aus den aktuellen Rollen und Rollenrechten eine wirksame Vereinigungsmenge und ordnet jede vorhandene Aktion ausdrücklich einem Recht zu. Nur die Admin-Rolle verwaltet Rollen und Rollenrechte; der bestehende Schutz des letzten Admins bleibt erhalten. Die Oberfläche zeigt Rechte je Rolle, blendet Nutzer- und Verwaltungsaktionen entsprechend ein und macht den Administrationsmodus für Nutzer mit Verwaltungsrechten zugänglich.

Die Umsetzung erweitert die vorhandene Java/Spring/PostgreSQL- und React-Anwendung. Eine Migration setzt die Startrechte und Viewer für neu angelegte Konten. Bestehende Rollenzuordnungen bleiben erhalten; ein neues versioniertes Rollenrechte-API folgt dem bewährten Muster der Nutzerrollenverwaltung. [research.md](./research.md) dokumentiert die Entscheidungen und verworfenen Alternativen.

## Technical Context

**Language/Version**: Java 21; TypeScript strict, React 19.

**Primary Dependencies**: Spring Boot 4.1.1, Spring Security, Spring Data JPA/JDBC, Flyway; React Router 7, Zod, Vitest/Testing Library. Keine neue Laufzeitabhängigkeit geplant.

**Storage**: PostgreSQL 17; Flyway-Migration V16 nach den Check-in-Migrationen V14 und V15 für fünf Rollenrechte-Versionen, bis zu 70 Rollenrecht-Zeilen und Viewer-Standard bei neuer Kontoanlage. Der Administrationsmodus bleibt Sitzungszustand.

**Testing**: JUnit 5, MockMvc und Testcontainers/PostgreSQL im Backend; Vitest, Testing Library, ESLint und TypeScript-Build im Frontend. Für jede Implementierung zuerst ein fehlschlagender Test gemäß Verfassung.

**Target Platform**: Bestehender Docker-Compose-Betrieb auf Linux; moderne Browser für die React-Oberfläche.

**Project Type**: Webanwendung mit Backend und Frontend.

**Performance Goals**: Keine nutzerseitig spürbare Verzögerung durch die Rechteprüfung; pro Anfrage nur ein aktueller, indexgestützter Rollen-/Rechte-Snapshot, kein wiederholtes Nachladen innerhalb derselben Anfrage. Die feste Matrix umfasst fünf Rollen und 14 Rechte. Kein neues Durchsatz-SLA wird aus der Spezifikation abgeleitet.

**Constraints**: Berechtigungen gelten ab der nächsten geschützten Anfrage, daher kein Sitzungs- oder Browser-Cache als Autoritätsquelle. Fehlende/ungültige Rechtekonfiguration und nicht klassifizierte Aktionen werden verweigert. Bestehende API-Körper bleiben kompatibel; `/api/auth/roles` erhält nur zusätzliche Felder. Die beabsichtigte Einschränkung für Viewer und andere Rollen wird im Vertrag dokumentiert.

**Scale/Scope**: Fünf Rollen, 14 konfigurierbare Rechte, 70 mögliche Rolle-Recht-Paare; bestehende Controller-Aktionen aus Raum-, Reservierungs-, Geräte-, Karten-, Stammdaten-, Statistik- und Nutzerrollenbereichen. Keine neue frei definierbare Rolle und keine nutzerindividuelle Rechteausnahme.

## Constitution Check

*GATE: Vor Phase 0 geprüft und nach Phase 1 erneut geprüft.*

| Verfassungsprinzip | Vor Design | Nach Design | Nachweis/Umsetzung |
| --- | --- | --- | --- |
| I. Test-First Development | PASS | PASS | Aufgabenphase beginnt jede Scheibenänderung mit fehlschlagendem Backend- oder Frontend-Test. Matrix-, Migrations-, Konkurrenz- und UI-Tests sind in [quickstart.md](./quickstart.md) zur Abnahme beschrieben. |
| II. Typed and Consistent Codebases | PASS | PASS | Feste Java-Enums/Typen und strikte TypeScript-Codes; bestehende Controller-Service-Repository-Struktur bleibt. |
| III. Contract-First API Design | PASS | PASS | Neue und erweiterte HTTP-Formen sowie Statuscodes stehen in [contracts/role-permissions-api.md](./contracts/role-permissions-api.md); bestehende Aktionen in [contracts/authorization-matrix.md](./contracts/authorization-matrix.md). |
| IV. Secure and Data-Respecting | PASS | PASS | Serverseitige aktuelle Prüfung, Default-Verweigerung, 404 für fremde Buchungen, keine Personendaten in Belegungslisten oder Ablehnungslogs; Eingaben und Versionen werden validiert. |
| V. Simplicity and Observability | PASS | PASS | Bestehender Zugriffsfilter, Admin-Modus und Rollenservice werden erweitert; keine neue Bibliothek. Strukturierte Ablehnungslogs und bestehender Health-Endpunkt bleiben. |
| Workflow/Quality Gates | PASS | PASS | Arbeit auf Feature-Branch; vor Merge müssen Tests, Build, Lint und CI erfolgreich sein. Diese Planung führt keinen Merge durch. |

Keine unbegründete Verfassungsabweichung und keine offene technische Klärung. Die Detailentscheidungen sind in [research.md](./research.md) aufgelöst.

## Project Structure

### Documentation (this feature)

```text
specs/015-role-permission-management/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
├── contracts/
│   ├── role-permissions-api.md
│   ├── authorization-matrix.md
│   └── ui.md
└── tasks.md                 # folgt mit /speckit-tasks
```

### Source Code (betroffene Bereiche)

```text
backend/src/main/
├── resources/db/migration/V16__*.sql          # Rollenrechte, Startwerte, Viewer-Standard
└── java/at/mci/igp/raumlotse/
    ├── domain/Role.java, PermissionCode.java  # feste Kataloge
    ├── repository/                             # versionierte Rollenrechte und aktuelle Rechteabfrage
    ├── service/                                # RoleAccessFilter, Rechteentscheidung, UserRoleService,
    │                                           # ReservationAccessPolicy, AdminModeService, Readiness
    ├── controller/                             # Rollenrechte, CurrentRoles, Reservierung/Geräte
    ├── dto/                                    # Rechte- und Mitgliedschaftsantworten
    └── config/LocalAuthFixtureConfiguration.java
backend/src/test/java/at/mci/igp/raumlotse/      # Migration, Matrix, Konkurrenz, Sicherheitsfälle

frontend/src/
├── API/                                       # Rollenrechte und erweitertes Rollen-/Rechteprofil
├── auth/                                      # aktueller Rechte-Hook, Modus, Routenschutz
├── components/Navigation/                    # nur erlaubte Einträge
├── components/UserRoles/                     # bestehende Rollenzuordnung
├── pages/                                    # neue Rollenrechte-Seite; Teilbereichsprüfungen
└── types/                                    # feste PermissionCode- und Antworttypen
```

**Structure Decision**: Bestehende Webanwendungsstruktur beibehalten. Die Rolle-Recht-Persistenz ergänzt die vorhandenen Rollen-Tabellen; die Anfrageentscheidung bleibt zentral und wird in Controller-Services für Eigentum und Gerätebedingungen ergänzt. Die API- und Oberflächenverträge werden vor Umsetzung als Referenz verwendet.

## Complexity Tracking

Keine Verfassungsabweichung zu begründen.
