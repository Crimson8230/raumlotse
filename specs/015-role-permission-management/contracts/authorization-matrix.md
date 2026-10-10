# Autorisierungsmatrix der bestehenden HTTP-Funktionen

Die Tabelle klassifiziert die aktuell gemappten Controller-Aktionen plus die geplanten Rollenrechte-Endpunkte. Platzhalter wie `{id}` stehen für genau ein Pfadsegment. Spezifische Pfade (`/search`, `/my-upcoming`, `/expire-unattended`) müssen vor generischen ID-Pfaden erkannt werden. Nicht klassifizierte geschützte Methoden und Pfade werden verweigert; ein Handler-Abgleichstest macht neue oder verwaiste Zuordnungen sichtbar.

| Methode und Pfad | Erforderlich | Zusätzliche Prüfung |
| --- | --- | --- |
| `GET /api/health`, `GET /api/auth/csrf`, `POST /api/auth/login` | Öffentlich | Bestehende Anmelde- und Eingaberegeln |
| `GET /api/auth/me`, `GET /api/auth/roles` | Angemeldete Identität | Rollen und Rechte aus aktuellem Bestand; kein alter Sitzungs-Snapshot |
| `PUT /api/auth/admin-mode` | Angemeldete Identität | Einschalten nur mit Admin oder mindestens einem Verwaltungsrecht; Ausschalten immer erlaubt |
| `GET /api/admin/users`, `GET/PUT /api/admin/users/{id}/roles` | Feste Admin-Rolle | Version und Schutz des letzten Admins wie Feature 003 |
| `GET /api/admin/roles`, `GET/PUT /api/admin/roles/{roleCode}/permissions` | Feste Admin-Rolle | Version und Auswahlvalidierung aus [role-permissions-api.md](./role-permissions-api.md) |
| `GET /api/admin/statistics` | `STATISTICS_READ` | Kein Admin-Modus für Serverzugriff erforderlich |
| `GET /api/rooms`, `GET /api/rooms/{id}`, `GET /api/rooms/search`, `GET /api/rooms/search/seating-arrangements` | `READ` | Bestehende Filter und Sichtbarkeitsregeln |
| `GET /api/buildings`, `GET /api/buildings/{id}/floors`, `GET /api/equipment-types` | `READ` | Lesbare Stammdaten |
| `GET /api/maps`, `GET /api/maps/{id}`, `GET /api/maps/{id}/image`, `GET /api/connections` | `READ` | Karten- und Verbindungsansichten |
| `GET /api/rooms/{id}/available-equipment`, `GET /api/rooms/{id}/reservations`, `GET /api/reservations/my-upcoming` | `READ` | Fremde Raum-Belegungen stets ohne Personendaten; „meine“ nach Ersteller-ID |
| `GET /api/reservations/{id}` | `READ` | Eigenes Detail oder zusätzlich `OTHER_RESERVATION_MANAGE`; sonst 404 wie unbekannt |
| `POST /api/rooms/{id}/reservations` | `RESERVE` | Neuanlage für angemeldeten Ersteller; bestehende Buchungsregeln |
| `PATCH /api/reservations/{id}`, `POST /api/reservations/{id}/activate`, `/complete`, `/expire`, `/cancel` | `OWN_RESERVATION_MANAGE` bei Eigentum, sonst `OTHER_RESERVATION_MANAGE` | Eigentum nach stabiler Ersteller-ID; Statusregeln; fremd ohne Recht ergibt 404 |
| `GET /api/rooms/{id}/device-controls`, `POST /api/rooms/{id}/device-controls/{kind}` | `OWN_ACTIVE_DEVICE_CONTROL` | Eigene aktive Reservierung im betreffenden Raum erforderlich |
| `POST /api/buildings`, `PUT /api/buildings/{id}`, `POST /api/buildings/{id}/deactivate`, `/reactivate`, `DELETE /api/buildings/{id}` | `BUILDING_MANAGE` | Bestehende Fachvalidierung |
| `POST /api/buildings/{id}/floors`, `PUT /api/floors/{id}`, `POST /api/floors/{id}/deactivate`, `/reactivate`, `DELETE /api/floors/{id}` | `FLOOR_MANAGE` | Bestehende Fachvalidierung |
| `POST /api/equipment-types`, `PUT /api/equipment-types/{id}`, `POST /api/equipment-types/{id}/deactivate`, `/reactivate`, `DELETE /api/equipment-types/{id}` | `EQUIPMENT_TYPE_MANAGE` | Bestehende Fachvalidierung |
| `POST /api/rooms`, `PUT /api/rooms/{id}`, `POST /api/rooms/{id}/deactivate`, `/reactivate`, `DELETE /api/rooms/{id}` | `ROOM_MANAGE` | Bestehende Fachvalidierung |
| `PUT /api/floors/{id}/map`, `DELETE /api/maps/{id}` | `MAP_MANAGE` | Bestehende Kartenvalidierung |
| `GET /api/maps/{id}/unplaced-rooms`, `PUT/DELETE /api/maps/{id}/placements/{roomId}` | `ROOM_PLACEMENT_MANAGE` | Karten- und Raumbeziehung gültig |
| `POST /api/connections`, `PUT/DELETE /api/connections/{id}`, `PUT/DELETE /api/connections/{id}/points/{mapId}` | `CONNECTION_MANAGE` | Bestehende Verbindungsvalidierung |
| `POST /api/reservations/expire-unattended` | `RESERVATION_MAINTENANCE` | Nur manueller Auslöser; automatischer Ablauf bleibt Systemaufgabe |

`HEAD` darf nur wie das entsprechende klassifizierte `GET` behandelt werden; `OPTIONS` wird ausschließlich für die erforderliche Protokollbehandlung zugelassen und darf weder fachliche Daten liefern noch Änderungen auslösen. Die normale Sicherheitskette unterscheidet fehlende Anmeldung (`401`) von fehlendem Recht (`403`). Für fremde Reservierungs-Einzelzugriffe bleibt die datensparsame `404`-Regel vorrangig.

## Benutzeroberfläche

| Route/Bereich | Sichtbarkeitsregel |
| --- | --- |
| `/`, `/rooms`, `/rooms/:id`, `/rooms/:id/display`, `/maps`, `/maps/:id` | `READ`; Buchungsschaltflächen zusätzlich `RESERVE` |
| `/rooms/:id/control` | `OWN_ACTIVE_DEVICE_CONTROL` und eigene aktive Buchung |
| `/admin/locations` | Modus an und mindestens eines aus `BUILDING_MANAGE`, `FLOOR_MANAGE`, `EQUIPMENT_TYPE_MANAGE`; jede Teilsektion einzeln geschützt |
| `/admin/maps`, `/admin/maps/:id` | Modus an und mindestens eines aus `MAP_MANAGE`, `ROOM_PLACEMENT_MANAGE`, `CONNECTION_MANAGE`; einzelne Bearbeitungsaktionen nach ihrem Recht |
| `/admin/rooms/new`, `/admin/rooms/:id/edit` | Modus an und `ROOM_MANAGE` |
| `/admin/statistics` | Modus an und `STATISTICS_READ` |
| `/admin/users`, `/admin/users/:id/roles`, neue `/admin/roles` | Modus an und feste Admin-Rolle |

Auch bei ausgeschaltetem Modus bleiben direkte HTTP-Aktionen allein von den Rechten abhängig. Die Oberfläche aktualisiert Rechte nach Navigation/Fokus und nach Änderungen; eine veraltete UI-Anzeige kann den Serverzugriff nie erweitern.
