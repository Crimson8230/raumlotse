# Data Model: Rollenrechte verwalten

## Persistierte Entitäten

### UserAccount (bestehend)

- `id`: stabile UUID, Primärschlüssel.
- Beziehung: besitzt mindestens eine `RoleAssignment`; neue Konten erhalten atomar `UserRoleState` und `VIEWER`.
- Die lokale Erst-Admin-Fixture ist eine explizite Bootstrap-Ausnahme für ein frisch angelegtes Konto.

### UserRoleState und RoleAssignment (bestehend)

- `user_role_state.user_id`: UUID, Primär- und Fremdschlüssel auf `user_account.id`.
- `user_role_state.roles_version`: nicht negative Versionszahl für vollständige Rollenersetzung.
- `role_assignment(user_id, role_code)`: zusammengesetzter Primärschlüssel; `role_code` ist genau einer aus `ADMIN`, `UNIVERSITY_STAFF`, `STUDENT`, `LECTURER`, `VIEWER`.
- Mindestens eine Zuordnung pro Nutzer wird fachlich erzwungen. Der bestehende `role_mutation_guard` serialisiert Änderungen, die den letzten Admin betreffen können.

### RolePermissionState (neu)

- `role_code`: einer der fünf festen Rollencodes; Primärschlüssel.
- `permissions_version`: nicht negative Ganzzahl, initial `0`.
- Genau fünf Zeilen müssen existieren. Eine Zeile wird beim Ändern der Rechte ihrer Rolle gesperrt; nur eine tatsächlich veränderte Auswahl erhöht die Version um eins.
- Eine veraltete erwartete Version wird auch dann abgelehnt, wenn die eingereichte Auswahl zufällig dem aktuellen Inhalt entspricht.

### RolePermission (neu)

- `role_code`: Fremdschlüssel auf `role_permission_state.role_code`.
- `permission_code`: einer der 14 festen Codes aus dem Katalog unten.
- Primärschlüssel `(role_code, permission_code)` verhindert doppelte Freigaben.
- Das Fehlen einer Zeile bedeutet „nicht gewährt“. Andere Rollen können dasselbe Recht gewähren; die wirksame Menge eines Nutzers ist die Vereinigungsmenge.
- `READ` muss für eine Rolle vorhanden sein, wenn irgendein anderer konfigurierbarer Code für diese Rolle gespeichert wird. Vollständige Ersetzungen werden vor dem Schreiben validiert; unbekannte Codes und `ROLE_MANAGEMENT` sind kein gültiger Inhalt dieser Tabelle.

## Fester Berechtigungskatalog

| Code | Funktion | Anfangsrollen |
| --- | --- | --- |
| `READ` | Raum-, Karten-, Status-, Belegungs- und eigene Reservierungsansichten | Alle fünf |
| `RESERVE` | Eigene Reservierung anlegen | Admin, University Staff, Student, Lecturer |
| `OWN_RESERVATION_MANAGE` | Eigene Reservierung ändern und Zustandsaktionen | Admin, University Staff, Student, Lecturer |
| `OTHER_RESERVATION_MANAGE` | Fremde Einzel-Details und Änderungsaktionen | Admin |
| `OWN_ACTIVE_DEVICE_CONTROL` | Geräte der eigenen aktiven Buchung anzeigen und bedienen | Admin, University Staff, Student, Lecturer |
| `BUILDING_MANAGE` | Gebäude pflegen | Admin |
| `FLOOR_MANAGE` | Stockwerke pflegen | Admin |
| `EQUIPMENT_TYPE_MANAGE` | Ausstattungsarten pflegen | Admin |
| `ROOM_MANAGE` | Räume pflegen | Admin |
| `MAP_MANAGE` | Karten hochladen/ersetzen/entfernen | Admin |
| `ROOM_PLACEMENT_MANAGE` | Raumpositionen pflegen | Admin |
| `CONNECTION_MANAGE` | Verbindungen und Punkte pflegen | Admin |
| `STATISTICS_READ` | Verwaltungsstatistiken lesen | Admin |
| `RESERVATION_MAINTENANCE` | Manuellen Ablauf für überfällige Reservierungen starten | Admin |

`ROLE_MANAGEMENT` ist bewusst **kein** konfigurierbarer Code. Diese fest geschützte Befugnis folgt ausschließlich der aktuellen Rolle `ADMIN` und umfasst Nutzerrollen sowie Rollenrechte.

## Abgeleitete Zustände und Regeln

1. **Wirksame Rechte**: `union(role_permission[role] for role in current role_assignment[user])`. Für jede geschützte Anfrage neu ermitteln; keine gespeicherte Nutzerkopie und keine Freigabe durch die UI oder den Administrationsmodus.
2. **Admin-Zugang**: `ADMIN in current role_assignment[user]`; unabhängig von `READ` und allen konfigurierbaren Rechten. Entfernen der letzten solchen Zuordnung bleibt verboten.
3. **Administrationsmodus**: bestehender Sitzungswert, wirksam nur mit `ADMIN` oder mindestens einem der neun Verwaltungsrechte (`BUILDING_MANAGE` bis `RESERVATION_MAINTENANCE` einschließlich `STATISTICS_READ`). Der Modus gewährt selbst keine Rechte.
4. **Reservierungseigentum**: bestehende `reservation.created_by_user_id` ist maßgeblich. Fehlt sie, ist die Buchung für jeden Nutzer „fremd“. `reserved_for` und Anzeigenamen haben keinen Einfluss.
5. **Buchungszustand**: Die bestehenden zulässigen Reservierungsübergänge bleiben unverändert. Rechteprüfung und Eigentumsprüfung sind zusätzliche Voraussetzungen, keine neuen Zustände.

## Änderungen und Fehlerszenarien

| Vorgang | Vorbedingung | Erfolg | Ablehnung |
| --- | --- | --- | --- |
| Neue Nutzeranlage | Konto ist neu | `UserRoleState(0)` und ausschließlich `VIEWER` | Kontoanlage insgesamt fehlgeschlagen, falls Standardzuordnung nicht erstellt werden kann |
| Rollen ersetzen | aktueller Admin, aktuelle Version, nichtleer, letzter Admin bleibt | Zuordnungen gemeinsam ersetzt, Version bei Änderung +1 | 400/403/404/409; keine Teiländerung |
| Rollenrechte ersetzen | aktueller Admin, aktuelle Version, 14 bekannte Codes, `READ`-Abhängigkeit | Rechte einer Rolle gemeinsam ersetzt, Version bei Änderung +1 | 400/403/404/409; keine Teiländerung |
| Rolle oder Recht entziehen | gespeicherter Zustand geändert | nächster geschützter Request sieht neue wirksame Menge | bei Speicherfehler keine Freigabe |
