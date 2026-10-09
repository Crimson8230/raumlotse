# Quickstart: Rollenrechte validieren

## Voraussetzungen

- Java 21, Node.js mit npm und Docker für die PostgreSQL-basierten Integrationstests.
- Eine lokale `.env` gemäß `README.md`; für manuelle Prüfung kann das Profil `local-auth-fixture` mit einem frischen Testkonto aktiviert werden. Niemals produktive Daten für die Szenarien verwenden.
- Mindestens ein Admin-Konto sowie getrennte Testkonten mit Viewer und weiteren Rollen. Der Berechtigungskatalog und die erwarteten Endpunkte stehen in [data-model.md](./data-model.md) und [contracts/](./contracts/).

## Automatisierte Prüfung nach der Implementierung

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

Neue Tests werden gemäß Projektverfassung vor der zugehörigen Produktionsänderung erstellt und zunächst fehlschlagend ausgeführt. Die Backend-Suite benötigt eine verfügbare Docker-Umgebung für Testcontainers.

## Manuelle Ende-zu-Ende-Szenarien

1. **Anfangszustand**: Mit frischer Datenbank starten. Viewer hat ausschließlich `READ`; Staff, Student und Lecturer haben die vier allgemeinen Anfangsrechte; Admin hat alle 14. Bei erneutem Start bleiben veränderte Rollenrechte unverändert. Ein neu angelegtes gewöhnliches Konto hat ausschließlich Viewer.
2. **Rollenrechte speichern**: Als Admin `/admin/roles` öffnen, bei University Staff `BUILDING_MANAGE` einschalten und speichern. Nach Neuladen ist der Zustand erhalten. Derselbe Nutzer kann Gebäude verwalten, aber ohne weitere Rechte keine Karten oder Rollen pflegen.
3. **Vereinigung**: Einem Testnutzer Student und University Staff zuweisen. Die wirksamen Rechte in `/api/auth/roles` entsprechen genau der Vereinigungsmenge. Entzug eines Rechts wirkt bei seiner nächsten geschützten Aktion in derselben Sitzung.
4. **Lesen-Abhängigkeit**: Bei einer Rolle `READ` ausschalten und ein anderes Recht aktiv lassen. Das Speichern wird vollständig abgelehnt; kein einzelnes Recht ändert sich. Eine Rolle ohne Rechte ist zulässig und erhält keine implizite Freigabe.
5. **Buchungen**: Viewer kann Raum- und Belegungsdaten lesen, aber nicht buchen. Ein Nutzer mit `RESERVE` legt eine Buchung an. Ohne `OWN_RESERVATION_MANAGE` kann er sie nicht ändern. Ein Nutzer mit `OTHER_RESERVATION_MANAGE` kann fremde Einzel-Details und erlaubte Aktionen nutzen; ohne dieses Recht sieht er bei einem fremden Einzelaufruf denselben 404-Ausgang wie bei einer nicht vorhandenen Buchung. Raum-Belegungslisten zeigen keine fremden Personendaten.
6. **Verwaltungsmodus**: Ein Nicht-Admin mit genau einem Verwaltungsrecht kann den Modus einschalten und sieht nur diesen Bereich. Direkte HTTP-Aktionen sind mit diesem Recht auch bei ausgeschaltetem Modus zulässig. Nach Entzug verschwindet der Bereich und die nächste Aktion wird abgelehnt. `/admin/users` und `/admin/roles` bleiben gesperrt.
7. **Letzter Admin**: Entfernen der Admin-Rolle beim einzigen Admin wird mit unverändertem Zustand abgelehnt. Mit zwei Admins kann einer die Rolle abgeben, sofern ihm eine andere Rolle bleibt. Die feste Rollenverwaltung kann in keiner Rollenrechteauswahl abgeschaltet oder an einen Nicht-Admin vergeben werden.
8. **Konflikte und Fehler**: Zwei Admins laden dieselbe Rollenrechte-Version. Nach dem Speichern durch den ersten wird der zweite Stand zurückgewiesen und muss neu geladen werden. Unbekannte Rechte, doppelte Codes, fehlende Version und ein simuliert nicht verfügbarer Rechtebestand bewirken keine Teiländerung und keine Freigabe.
9. **Neun Verwaltungsrechte einzeln**: Einem Nicht-Admin jeweils nur `READ` und eines von `BUILDING_MANAGE`, `FLOOR_MANAGE`, `EQUIPMENT_TYPE_MANAGE`, `ROOM_MANAGE`, `MAP_MANAGE`, `ROOM_PLACEMENT_MANAGE`, `CONNECTION_MANAGE`, `STATISTICS_READ` oder `RESERVATION_MAINTENANCE` geben. Je eine erlaubte Aktion direkt per API und eine fremde Verwaltungsaktion mit `403 PERMISSION_REQUIRED` prüfen. Statistik-Lesen, ungeplatzierte Räume und den manuellen Wartungslauf ausdrücklich einbeziehen; der Modusschalter darf das API-Ergebnis nicht ändern.
10. **Besitz und Gerätezustand**: Eine bestehende eigene Buchung nach Entzug von `OWN_RESERVATION_MANAGE` noch lesen, aber nicht ändern können. Bei einer Buchung ohne `created_by_user_id` darf das Eigentumsrecht nicht greifen. `OTHER_RESERVATION_MANAGE` erlaubt fremde Einzel-Details, während Raum-Belegungslisten weiter anonym bleiben. Das Geräterecht allein reicht ohne eigene aktive Buchung nicht aus; eine aktive Buchung allein reicht ohne Geräterecht ebenfalls nicht aus.
11. **Protokoll- und Fehlergrenzen**: Eine unbekannte geschützte Methode/Pfad-Kombination verweigern. `HEAD` mit derselben Rechteklasse wie `GET` und `OPTIONS` ohne fachliche Daten oder Zustandsänderung prüfen. Bei nicht verfügbarem Rollenrechte-Speicher muss jede geschützte Aktion ohne Freigabe enden; Ablehnungslogs dürfen keine Namen, Buchungsinhalte oder Zugangsdaten enthalten.

## Abnahmeprüfung

- Der Backend-Testkatalog vergleicht jede gemappte Aktion mit [contracts/authorization-matrix.md](./contracts/authorization-matrix.md); neue unklassifizierte Funktionen müssen fehlschlagen.
- Je Recht gibt es mindestens eine erlaubte und eine verweigerte Prüfung; alle 31 nichtleeren Rollenkombinationen erfüllen die Vereinigungsregel.
- Die 16 Anforderungen und messbaren Kriterien aus [spec.md](./spec.md) bleiben die fachliche Referenz. Die vorhandene [Qualitätscheckliste](./checklists/requirements.md) prüft die Spezifikation, nicht die Implementierung.

## Prüfstand am 8. Oktober 2026

- Frontend: `npm test` erfolgreich (340 Tests in 49 Dateien), `npm run lint` erfolgreich, `npm run build` erfolgreich.
- Backend: `mvnw.cmd test` und der abschließende vollständige Lauf `mvnw.cmd test -q` mit Testcontainers/PostgreSQL 17 waren erfolgreich. Der letzte Lauf umfasst 555 Tests in 103 Testklassen, ohne Fehler, Fehlschläge oder übersprungene Tests. Die ergänzten Tests prüfen alle neun Verwaltungsrechte, die feste Admin-Grenze, Reservierungsrechte sowie Migrations- und Snapshot-Fehlerfälle.
- Die automatisierten Prüfungen decken die 16 Anforderungen so ab: FR-001–004 durch Rollen-, Migrations-, Vereinigungs- und Sitzungsaktualisierungstests; FR-005–007 durch Katalog-, Matrix- und Startrechteprüfungen; FR-008–012 durch Rollenrechte-UI/API-, Konflikt- und Letzt-Admin-Tests; FR-013–016 durch Aktionsmatrix-, Navigations-, Reservierungs- und Gerätetests. Die Szenarien 1–11 oben sind die manuellen Ende-zu-Ende-Schritte. Die Nutzbarkeitsmessung SC-007 mit Akzeptanzteilnehmern bleibt eine manuelle Abnahme.
