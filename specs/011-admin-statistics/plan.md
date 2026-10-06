# Implementation Plan: Admin-Statistikbereich

**Branch**: `011-admin-statistics` | **Date**: 2026-10-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-admin-statistics/spec.md`

## Summary

Der geschützte Adminbereich erhält eine gemeinsame Statistikübersicht für einen auswählbaren Zeitraum. Sie zeigt Raumauslastung, Nutzung der Raum-Ausstattung, durchschnittliche geplante Teilnehmerzahl pro gültiger Buchung sowie Stornierungsanzahl und -quote. Die Auswertung erfolgt read-only aus den vorhandenen Reservierungs-, Raum- und Ausstattungstabellen; neue Persistenzobjekte sind nicht erforderlich. Die Daten werden über eine Admin-geschützte Statistik-API aggregiert und im bestehenden React-Navigations- und Rollenmodell dargestellt.

## Technical Context

<!--
  Technical context for this feature.
-->

**Language/Version**: Java 21; TypeScript in strict mode

**Primary Dependencies**: Spring Boot 4.1.1 Web MVC/Data JPA/Security/Validation; React 19; React Router 7; Vite; vorhandene Lucide-Icons

**Storage**: PostgreSQL 17; bestehende `reservation`, `room`, `equipment_type`, `room_equipment` und abhängige Tabellen; keine neue Tabelle

**Testing**: JUnit 5, Mockito, MockMvc, Testcontainers/PostgreSQL; Vitest und React Testing Library

**Target Platform**: Webanwendung im bestehenden Docker-/Spring-Boot- und Vite-Setup

**Project Type**: Webanwendung mit Spring-Backend und React-Frontend

**Performance Goals**: Erste nutzbare Statistikdarstellung für 100 Räume und 10.000 Buchungen innerhalb von 5 Sekunden; alle vier Metrikgruppen verwenden denselben Zeitraum und einen gemeinsamen Ladevorgang.

**Constraints**: Nur ADMIN; GET-Anfrage bleibt durch die bestehende Session-Authentifizierung geschützt; Zeitraum als inklusives lokales Startdatum und exklusives lokales Enddatum in `Europe/Berlin`; Halb-offene Zeitintervalle; stornierte und abgelaufene Buchungen zählen nicht als tatsächliche Nutzung; keine neue Abhängigkeit und keine ungeschützte Statistikdarstellung.

**Scale/Scope**: Eine Admin-Seite, ein read-only Backend-Endpunkt, bis mindestens 100 Räume/10.000 Buchungen pro Auswertung; keine Exporte, keine konfigurierbaren Öffnungszeiten, keine Anwesenheitserfassung.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Die Implementierung wird mit Backend-Service-/Repository-/Controller-Tests und Frontend-Komponenten-/API-Tests begonnen.
- **II. Modern, Typed, and Consistent Codebases**: PASS. Die Lösung bleibt in Java 21, Spring Boot, TypeScript strict und funktionalen React-Komponenten; keine untypisierten Datenmodelle.
- **III. Contract-First API Design**: PASS. Der Statistik-Endpunkt wird in [contracts/admin-statistics.yaml](./contracts/admin-statistics.yaml) mit Anfrage, Antwort, Statuscodes und Berechnungssemantik dokumentiert.
- **IV. Secure and Data-Respecting by Default**: PASS. Der Endpunkt wird in den bestehenden Admin-Rollenfilter aufgenommen; nur aggregierte Kennzahlen werden ausgegeben, keine Buchungs- oder Benutzerdetails.
- **V. Simplicity and Observability**: PASS. Das Feature nutzt bestehende Tabellen und eine fokussierte Auswertung ohne neue Persistenzabstraktion; ungültige Anfragen und Backend-Fehler bleiben strukturiert diagnostizierbar.

## Project Structure

### Documentation (this feature)

```text
specs/011-admin-statistics/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  Concrete source layout for this feature.
-->

```text
backend/
├── src/
│   ├── main/java/at/mci/igp/raumlotse/controller/AdminStatisticsController.java
│   ├── main/java/at/mci/igp/raumlotse/service/AdminStatisticsService.java
│   ├── main/java/at/mci/igp/raumlotse/repository/StatisticsRepository.java
│   └── main/java/at/mci/igp/raumlotse/dto/AdminStatisticsResponse.java
└── test/java/at/mci/igp/raumlotse/
    ├── controller/AdminStatisticsControllerTest.java
    ├── repository/AdminStatisticsRepositoryIntegrationTest.java
    └── service/AdminStatisticsServiceTest.java

frontend/
├── src/
│   ├── API/adminStatistics.ts
│   ├── pages/AdminStatisticsPage.tsx
│   ├── pages/AdminStatisticsPage.test.tsx
│   ├── types/adminStatistics.ts
│   └── components/Navigation/Navigation.tsx
└── src/App.tsx

```

**Structure Decision**: Die bestehende getrennte Backend-/Frontend-Struktur wird erweitert. Das Backend berechnet und schützt die aggregierten Daten; das Frontend lädt genau einen Snapshot und rendert Karten/Kennzahlen sowie sortierbare Tabellen. Die vorhandene `RequireAdmin`-Route und der `RoleAccessFilter` werden wiederverwendet.

## Complexity Tracking


| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Keine | N/A | Keine Verfassungsverletzung und keine zusätzliche Projektstruktur erforderlich. |

## Phase 0: Research Plan

Die Rechercheentscheidungen sind in [research.md](./research.md) festgehalten. Besonders geprüft werden Statussemantik, Zeitintervallberechnung, Aggregation über Raum-Features, Admin-Schutz und die bestehende Test-/API-Struktur.

## Phase 1: Design Outputs

- [research.md](./research.md): getroffene fachliche und technische Entscheidungen
- [data-model.md](./data-model.md): read-only Statistikmodell, Beziehungen und Berechnungsregeln
- [contracts/admin-statistics.yaml](./contracts/admin-statistics.yaml): API-Vertrag für den Admin-Endpunkt
- [quickstart.md](./quickstart.md): reproduzierbare Validierungsszenarien für Backend und Frontend

## Post-Design Constitution Check

- **Test-First**: PASS — Vertrag, Berechnungsregeln und konkrete Testfälle sind vor der Implementierung beschrieben.
- **Typed/consistent**: PASS — DTOs und TypeScript-Typen bilden denselben Vertrag ab.
- **Contract-first**: PASS — API- und UI-Datenmodell sind versioniert dokumentiert.
- **Secure/data-respecting**: PASS — nur Admins erhalten aggregierte Ergebnisse; keine personenbezogenen Details werden ausgeliefert.
- **Simple/observable**: PASS — read-only Aggregation ohne neue Tabellen; Eingabefehler und Auswertungsfehler bleiben strukturiert behandelbar.
