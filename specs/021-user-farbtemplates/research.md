# Research: Persönliche Farbtemplates

## Entscheidung 1: Konto als Quelle der Farbpräferenz

**Decision**: `user_account` erhält einen optionalen `theme_code` und einen Status für die abgeschlossene Erstauswahl. Eine Flyway-Migration markiert bestehende Konten als abgeschlossen; neue Konten starten mit offener Erstauswahl.

**Rationale**: Die Präferenz muss geräteübergreifend gelten und beim Kontowechsel korrekt wechseln. `UserAccount` und die bestehende PostgreSQL-Datenbank bieten dafür bereits eine eindeutige Benutzeridentität. Die Migration muss alte und neue Konten unterschiedlich behandeln: vorhandene Zeilen `completed=true`, danach Spalten-Standard `false` für neue Zeilen.

**Alternatives considered**: Browser-Speicher wäre nicht geräteübergreifend und könnte eine frühere Kontofarbe zeigen; ein separates Präferenz-Objekt erhöht die Komplexität für zwei Felder. Ein Flag allein aus `created_at` abzuleiten wäre von Rollout-Zeit und Uhrensynchronisation abhängig.

## Entscheidung 2: Eigenständige persönliche Schnittstelle

**Decision**: `GET /api/me/theme-preference` liest den aktuellen Stand; `PUT /api/me/theme-preference` speichert `LIGHT` oder `DARK`; `POST /api/me/theme-preference/skip` schließt eine offene Erstauswahl ohne neue Präferenz ab. Die Identität kommt ausschließlich aus der authentifizierten Sitzung. Die drei Routen werden in `RoleAccessFilter` als `IDENTITY` eingetragen.

**Rationale**: `/api/me/favorite-rooms` zeigt bereits das Muster für persönliche Daten. Der Sitzungs-Prinzipal `AuthenticatedUser` enthält nur stabile Identitätsdaten. Ein eigener GET liest Änderungen anderer Sitzungen beim nächsten Laden und verhindert veraltete Farbwerte im Prinzipal. Schreibtoken und Authentifizierung bleiben nach den bestehenden Regeln erforderlich.

**Alternatives considered**: Erweiterung von Login- und `/api/auth/me`-Antwort würde veränderliche Präferenzen im Sitzungsmodell duplizieren. Ein einzelner PUT mit `null` als „Überspringen“ wäre weniger eindeutig und könnte eine gespeicherte Präferenz versehentlich löschen. Ein nur leserechtsgeschützter Endpunkt würde Benutzer ohne `READ` ausschließen.

## Entscheidung 3: Verhalten bei parallelen Sitzungen und ungültigen Werten

**Decision**: Erfolgreiche Auswahlen werden pro Konto atomar gespeichert; der letzte erfolgreich abgeschlossene Schreibvorgang bestimmt die Präferenz. Ein unbekannter gespeicherter Code wird als wirksames `LIGHT` ausgeliefert, ohne die Anmeldung zu blockieren. Die Schreibschnittstelle akzeptiert ausschließlich `LIGHT` und `DARK`. „Überspringen“ ist idempotent und löscht eine bereits gespeicherte Auswahl nicht.

**Rationale**: Das entspricht der Spezifikation und benötigt keinen zusätzlichen Versionsmechanismus. Die explizite Trennung von gewähltem und wirksamem Schema macht den Rückfall sichtbar und testbar.

**Alternatives considered**: Optimistische Versionsnummern würden Konfliktauflösung für eine einfache Benutzerpräferenz verlangen; hartes Scheitern bei unbekanntem gespeichertem Wert würde den Zugang unnötig stören.

## Entscheidung 4: Frontend-Ladegrenze und Navigation

**Decision**: Die authentifizierte Anwendung lädt die persönliche Präferenz vor der Anzeige regulärer Inhalte, setzt den Farbmodus erst nach erfolgreichem Abruf und zeigt bei offenem Erstlogin-Status die Auswahl. Ein Link „Persönliche Einstellungen“ führt alle Benutzer zu `/settings`; `/admin/settings` bleibt für administrative Einstellungen erhalten. Nach Abschluss der Erstauswahl wird die ursprünglich angesteuerte geschützte Adresse weiterverwendet.

**Rationale**: `AuthProvider` prüft bereits beim Start die Sitzung; `RequireAuth` ist der gemeinsame Rahmen der geschützten Seiten. Ein Ladezustand verhindert kurz sichtbare falsche Farben und die Übernahme der Farbe eines zuvor angemeldeten Kontos. Die Navigation hat heute nur einen administrativen Einstellungslink.

**Alternatives considered**: Laden erst auf der Einstellungsseite würde andere Seiten mit falschem Schema starten. Lokaler Cache ohne Kontoabgleich birgt eine Verwechslung zwischen Konten. Eine neue administrative Einstellungsoption würde normale Benutzer ausschließen.

## Entscheidung 5: Farbtokens und Barrierefreiheit

**Decision**: Die vorhandenen semantischen CSS-Farbvariablen in `frontend/src/index.css` erhalten einen dunklen Satz unter einem expliziten Theme-Attribut. Feste Farbwerte in Seitenstilen werden geprüft und in semantische Tokens überführt, soweit sie zur regulären Oberfläche gehören. Die eigenständige Raumanzeige erhält einen festen hellen Token-Kontext. Vorschau und Auswahl werden als benannte, tastaturbedienbare Optionen gestaltet; automatisierte Kontrast- und Zugänglichkeitsprüfung ergänzt die Sichtprüfung.

**Rationale**: „Papier & Tinte“ nutzt bereits zentrale Tokens und Muster statt Farbe als alleinige Statusinformation. Die Raumanzeige liegt technisch innerhalb des geschützten Routings, soll laut Spezifikation aber unverändert aussehen. Einige Seitenstile enthalten feste Farbwerte; bloßes Umschalten der Root-Variablen reicht deshalb nicht.

**Alternatives considered**: Ein zweites komplettes Stylesheet würde Seiten auseinanderlaufen lassen. Eine automatische Systemfarben-Erkennung ist nicht Teil des vereinbarten Umfangs. Nur visuelle Prüfung deckt Fokus- und Kontrastfehler nicht zuverlässig ab.

## Abhängigkeiten und offene technische Fragen

- Keine neue Laufzeitbibliothek vorgesehen. Vorhanden sind React, TypeScript, React Router, Spring Boot, Bean Validation, PostgreSQL, Flyway, Vitest, JUnit und Testcontainers.
- Nächste freie Datenbankmigration nach dem aktuellen Stand `V17` ist `V18`; vor Umsetzung erneut prüfen, ob zwischenzeitlich eine Migration hinzugekommen ist.
- Die genaue dunkle Palette ist eine Designentscheidung in der Umsetzung; ihre Kontrastwerte müssen für konkrete Text-, Aktions-, Fehler- und Fokuspaare gemessen werden.
- Keine offenen technischen Klärungen für die Planung.

## Bestandsprüfung vor Umsetzung (2026-10-20)

- Höchste vorhandene Flyway-Version ist weiterhin `V17`; `V18` ist frei.
- `frontend/src/index.css` enthält die hellen zentralen Tokens. Feste Farben außerhalb davon stehen in `AdminOccupancyPage.css`, `AdminStatisticsPage.css`, `RolePermissionPage.css`, `RoomDeviceControlsDialog.css` und `CheckInQrCode.css`. Die beiden Dialog-Overlays nutzen halbtransparentes Schwarz; deren Kontrast ist im Browser zu prüfen. `RoomDisplayPage.css` existiert noch nicht und wird für den festen Anzeigen-Kontext neu angelegt.
