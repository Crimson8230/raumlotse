# Quickstart: Admin-Statistikbereich

## Voraussetzungen

- Docker läuft für PostgreSQL und die Spring-Integrationstests.
- Eine lokale Datenbank enthält mindestens einen Admin-Benutzer.
- Backend-Abhängigkeiten sind über `backend/mvnw` verfügbar.
- Frontend-Abhängigkeiten sind unter `frontend/node_modules` installiert.

## 1. Backend-Vertrag und Berechnungen prüfen

```bash
cd backend
./mvnw -Dtest=AdminStatisticsServiceTest,AdminStatisticsControllerTest test
./mvnw -Dtest=AdminStatisticsRepositoryIntegrationTest test
```

Erwartungen:

- Nicht-Admins erhalten `403`; nicht eingeloggte Benutzer erhalten `401`.
- Ungültige oder leere Zeiträume werden mit `400` abgelehnt.
- Die Antwort enthält Raum-, Feature-, Teilnehmer- und Stornierungswerte für denselben Zeitraum.
- Reservierungen an den Zeitgrenzen werden über die gekürzte Überlappungsdauer korrekt berücksichtigt.
- `CANCELLED` und `EXPIRED` werden nicht als tatsächliche Nutzung gewertet.

## 2. Frontend-Komponente prüfen

```bash
cd frontend
npm test -- --run src/pages/AdminStatisticsPage.test.tsx
npm run lint
npm run build
```

Erwartungen:

- Die Admin-Navigation enthält den Statistikbereich.
- Ein Admin kann den Zeitraum ändern und alle Kennzahlen aktualisieren.
- Loading-, Fehler- und leerer Zeitraum-Zustand sind verständlich.
- Raum- und Feature-Tabellen können nachvollziehbar sortiert werden.
- Nicht-Admins sehen weder den Link noch Statistikdaten.

## 3. End-to-end manuell prüfen

1. Anwendung und Backend starten.
2. Als Admin anmelden und `/admin/statistics` öffnen.
3. Einen Zeitraum mit bekannten Reservierungen auswählen.
4. Prüfen, dass Raum-Auslastung, Feature-Nutzung, durchschnittliche Teilnehmerzahl und Stornierungswerte sichtbar sind.
5. Zeitraum auf einen Bereich ohne Buchungen ändern und prüfen, dass `0`, `—` und nicht anwendbare Werte unterschieden werden.
6. Als Benutzer ohne Admin-Rolle `/admin/statistics` sowie `/api/admin/statistics?from=2026-01-01&to=2027-01-01` aufrufen und den verweigerten Zugriff prüfen.

## Referenzen

- API: [contracts/admin-statistics.yaml](./contracts/admin-statistics.yaml)
- Read-only-Ausgabemodell: [data-model.md](./data-model.md)
- Fachliche Anforderungen: [spec.md](./spec.md)
