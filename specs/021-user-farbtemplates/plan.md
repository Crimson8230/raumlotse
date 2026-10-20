# Implementation Plan: Persönliche Farbtemplates

**Branch**: `021-user-farbtemplates` | **Date**: 2026-10-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/021-user-farbtemplates/spec.md`

## Summary

Neu angelegte Benutzer erhalten nach ihrer ersten Anmeldung eine einmalige Auswahl zwischen „Hell“ und „Dunkel“; alle angemeldeten Benutzer können das Farbschema später in persönlichen Einstellungen ändern. Die Präferenz wird am Benutzerkonto gespeichert und über eine eigene, für alle Rollen freigegebene persönliche Schnittstelle gelesen und geändert. Das Frontend lädt sie vor regulären geschützten Seiten und schaltet vorhandene semantische Farbvariablen um. Bestehende Konten behalten ohne Dialog das helle Erscheinungsbild.

## Technical Context

**Language/Version**: Java 21; TypeScript ~6.0.2 in strict mode

**Primary Dependencies**: Spring Boot 4.1.1, Spring Web MVC, Spring Security, Spring Data JPA, Bean Validation, Flyway; React 19.3, React Router 7.18, Zod 4.6. Keine neue Laufzeitabhängigkeit.

**Storage**: PostgreSQL 17, bestehende Tabelle `user_account`, Flyway-Migration voraussichtlich `V18`

**Testing**: JUnit/Spring-Integrationstests mit Testcontainers; Vitest und Testing Library; vorhandene axe-core-Zugänglichkeitsprüfung, ergänzende Kontrastmessung und manuelle Tastatur-/Sichtprüfung

**Target Platform**: Bestehende Browser-Anwendung mit Spring-Backend und React-Frontend; unterstützte Ansichten ab 320 px

**Project Type**: Web-Anwendung mit Backend und Frontend

**Performance Goals**: Präferenz bei normaler Verfügbarkeit vor dem ersten geschützten Seiteninhalt laden; keine sichtbare Zwischenanzeige im Schema eines anderen Kontos. Ziel für den Präferenzabruf: 95 % unter einer Sekunde in der bestehenden Betriebsumgebung.

**Constraints**: WCAG 2.1 AA für beide Farbschemata; vorhandenes helles „Papier & Tinte“-Design und eigenständige Raumanzeige erhalten; CSRF-Schutz und kontobezogene Berechtigung; keine neuen Rollenrechte; keine Bibliothek für Themes.

**Scale/Scope**: Zwei vordefinierte Schemata, eine einmalige Erstauswahl, eine persönliche Einstellungsseite, ein persönlicher Präferenzdatensatz je Konto, alle regulären authentifizierten Ansichten.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Grundsatz | Prüfung vor Recherche | Prüfung nach Design |
|---|---|---|
| I. Test-First Development | PASS: Für Datenmigration, persönliche Schnittstelle, Erstauswahl, Einstellungen, Kontowechsel und Farbkontrast werden zuerst fehlschlagende Tests vorgesehen. | PASS: [quickstart.md](quickstart.md) verlangt Red-Green-Nachweis und zielgerichtete Tests vor Implementierung. |
| II. Modern, Typed, and Consistent Codebases | PASS: Bestehende Java-/Spring- und React-/TypeScript-Strukturen werden erweitert. | PASS: Verträge definieren feste Theme-Codes; Frontend und Backend validieren sie typisiert. |
| III. Contract-First API Design | PASS: Ein neuer persönlicher Endpunkt ergänzt bestehende Verträge ohne sie zu brechen. | PASS: [contracts/theme-preference.md](contracts/theme-preference.md) legt Methoden, Felder und Status vor der Implementierung fest. |
| IV. Secure and Data-Respecting by Default | PASS: Präferenz ist Kontodatenbestand; Authentifizierung, CSRF und Eingabeprüfung sind erforderlich. | PASS: Endpunkte verwenden die Sitzungsidentität; `RoleAccessFilter` bekommt ausdrückliche `IDENTITY`-Regeln; Protokolle enthalten keine personenbezogenen Werte. |
| V. Simplicity and Observability | PASS: Zwei Felder am bestehenden Konto, vorhandene CSS-Tokens, keine neue Abhängigkeit. | PASS: Unbekannte gespeicherte Codes fallen auf „Hell“ zurück; Fehler werden mit strukturierten Codes ohne Benutzerdaten protokolliert; vorhandener Health-Check bleibt bestehen. |
| Workflow/Qualitätsgates | PASS: Feature-Branch ist aktiv; Tests, Lint und Build sind vorgesehen. | PASS: Keine Änderung an PR-Richtung oder CI-Gates. |

**Gate-Ergebnis**: Keine Verletzung der Verfassung; keine offenen technischen Klärungen. Phase 0 und Phase 1 können abgeschlossen werden.

## Phase 0: Research

Die Entscheidungen und Alternativen zu Persistenz, persönlicher Schnittstelle, parallelen Sitzungen, Ladegrenze und Farbvariablen stehen in [research.md](research.md). Insbesondere darf die veränderliche Farbpräferenz nicht allein im Authentifizierungs-Prinzipal gespeichert werden, weil sonst andere Sitzungen veraltete Werte liefern.

## Phase 1: Design & Contracts

- [data-model.md](data-model.md) beschreibt Felder, Migration, Validierung und Zustandsübergänge.
- [contracts/theme-preference.md](contracts/theme-preference.md) beschreibt persönliche API, Sicherheits- und Fehlerverhalten.
- [contracts/ui.md](contracts/ui.md) beschreibt Navigation, Erstlogin-Gate, Einstellungen und Farbkontext.
- [quickstart.md](quickstart.md) enthält End-to-End-Prüfschritte.

## Project Structure

### Documentation (this feature)

```text
specs/021-user-farbtemplates/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── theme-preference.md
│   └── ui.md
└── checklists/
    └── requirements.md
```

`tasks.md` folgt erst mit `$speckit-tasks`.

### Source Code (repository root)

```text
backend/
├── src/main/java/at/mci/igp/raumlotse/
│   ├── controller/       # persönliche Präferenzschnittstelle
│   ├── service/          # Kontozuordnung und Auswahlregeln; RoleAccessFilter
│   ├── domain/           # UserAccount
│   ├── dto/              # Anfrage und Antwort
│   └── repository/       # UserAccountRepository
├── src/main/resources/db/migration/  # nächste freie Flyway-Version
└── src/test/java/at/mci/igp/raumlotse/ # Migration, Auth, Präferenzintegration

frontend/
├── src/API/              # typisierter Präferenzclient
├── src/auth/             # Laden, Kontowechsel und Erstlogin-Gate
├── src/components/Navigation/ # persönlicher Einstellungslink
├── src/pages/            # Erstauswahl und persönliche Einstellungen
├── src/index.css         # semantische Theme-Tokens
└── src/**/*.test.tsx     # Komponenten- und Routentests
```

**Structure Decision**: Bestehende Backend-/Frontend-Struktur erweitern. Die Präferenz bleibt im Konto; Oberfläche und Authentifizierung verwenden vorhandene Einstiegspunkte. Administrations-Einstellungen bleiben unter `/admin/settings`, persönliche Einstellungen liegen unter `/settings`.
