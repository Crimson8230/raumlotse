# Rollenrechte: HTTP-Vertrag

Basis: `/api`, JSON, authentifizierte Sitzung und bestehender CSRF-Schutz für Änderungen. Der Server verwendet ausschließlich die aktuelle Identität und Rollenzuordnung des angemeldeten Nutzers. Alle Rollen- und Rechteantworten senden `Cache-Control: no-store`. Bestehende Endpunkte aus Feature 003 bleiben in Form und Versionskonfliktverhalten erhalten.

## Feste Codes und Antwortformen

- `RoleCode`: `ADMIN`, `UNIVERSITY_STAFF`, `STUDENT`, `LECTURER`, `VIEWER`.
- `PermissionCode`: genau die 14 Codes aus [data-model.md](../data-model.md). `ROLE_MANAGEMENT` ist kein `PermissionCode`.
- `version` und `expectedVersion`: nichtnegative Ganzzahl als kanonischer Dezimaltext; der Client behandelt sie als undurchsichtigen Versionswert.
- Berechtigungsmengen enthalten jeden Code höchstens einmal und werden in Katalogreihenfolge geliefert.
- `RoleOption`: `{code: RoleCode, label: string}`.
- `PermissionOption`: `{code: PermissionCode, label: string, description: string}`.
- `RolePermissions`: `{role: RoleOption, permissions: PermissionCode[], version: string, availablePermissions: PermissionOption[], protectedRoleManagement: boolean}`. Die letzte Eigenschaft ist nur bei `ADMIN` wahr und in Änderungsanfragen nicht zulässig.

## GET `/api/admin/roles`

Nur für aktuelle Admins. Antwort `200`: `{items: RoleOption[]}` mit genau fünf Rollen in fester Reihenfolge. `401` ohne Anmeldung, `403 ADMIN_REQUIRED` ohne Admin-Rolle, `503` bei nicht verfügbarer Rollenverwaltung.

## GET `/api/admin/roles/{roleCode}/permissions`

Nur für aktuelle Admins. Antwort `200`: `RolePermissions`, mit vollständigem Katalog und gespeicherter Auswahl. Unbekannter Rollencode: `404 ROLE_NOT_FOUND`. Weitere Antworten: `401`, `403 ADMIN_REQUIRED`, `503`.

## PUT `/api/admin/roles/{roleCode}/permissions`

Nur für aktuelle Admins. Vollständige Ersetzung der **14 konfigurierbaren** Rechte dieser Rolle:

```json
{"permissions":["READ","RESERVE"],"expectedVersion":"4"}
```

`permissions` ist ein erforderliches Array von 0 bis 14 eindeutigen, bekannten Codes. Ein leeres Array ist zulässig. Sobald ein anderer Code als `READ` enthalten ist, muss `READ` ebenfalls enthalten sein. `ROLE_MANAGEMENT` oder unbekannte/duplizierte Codes, `null`, fehlende Felder und ungültige Versionen werden abgelehnt. `expectedVersion` wird vor einem möglichen unveränderten Speichern geprüft.

- `200`: Gespeichertes `RolePermissions`; bei einer tatsächlichen Änderung ist `version` um eins erhöht, bei gültigem unverändertem Inhalt bleibt sie gleich.
- `400 INVALID_PERMISSION_SELECTION`: ungültige Auswahl, einschließlich fehlendem `READ` bei anderen Rechten; keine Teiländerung.
- `400 INVALID_REQUEST`: fehlerhafte JSON-Struktur oder Version; keine Teiländerung.
- `401 AUTHENTICATION_REQUIRED`, `403 ADMIN_REQUIRED`: kein Lesen oder Ändern der Rollenrechte.
- `404 ROLE_NOT_FOUND`: unbekannter Rollencode.
- `409 STALE_ROLE_PERMISSIONS`: zuvor geladene Version ist veraltet; Client muss neu lesen und Änderungen bewusst erneut auswählen.
- `503 ROLE_MANAGEMENT_UNAVAILABLE`: Speicherung oder Rechteprüfung nicht zuverlässig möglich; Client muss vor erneutem Versuch neu laden.

## GET `/api/auth/roles` (bestehender Vertrag erweitert)

Antwort `200`:

```json
{"roles":["STUDENT"],"ready":true,"adminMode":false,"permissions":["READ","RESERVE"],"canUseAdminMode":false}
```

`roles`, `ready` und `adminMode` bleiben erhalten. `permissions` ist die aktuelle Vereinigung der 14 konfigurierbaren Rechte. `canUseAdminMode` ist wahr bei aktueller Admin-Rolle oder mindestens einem aktuellen Verwaltungsrecht. Ein Admin ohne sonstige Rechte erhält weiterhin `canUseAdminMode: true`, weil die feste Rollenverwaltung zugänglich bleibt. Kein Client darf aus `adminMode` eine API-Berechtigung ableiten. Nicht verfügbarer Rechtezustand führt zu `503`, niemals zu einer impliziten Freigabe.

## PUT `/api/auth/admin-mode` (bestehender Vertrag angepasst)

Anfrage `{ "enabled": true | false }`; Antwort `200 {"adminMode": boolean}`. Der Schalter darf nur bei `canUseAdminMode: true` eingeschaltet werden. Bei entzogener Voraussetzung wird er wirksam ausgeschaltet; `403 ADMIN_MODE_NOT_ALLOWED` bei unzulässigem Einschalten. Das Ausschalten ist für den angemeldeten Nutzer erlaubt. Der Schalter beeinflusst keine Server-Autorisierung für andere Aktionen.

## Fehlerform und Datenzugriff

Fehler verwenden weiter die vorhandene Problem-Form `{title,status,detail,code,errors?}`. Fehlende Anmeldung ergibt `401`; fehlendes Funktionsrecht für eine gewöhnliche Aktion `403 PERMISSION_REQUIRED`, ohne Datenänderung. Ein fremder Reservierungs-Einzelaufruf ohne `OTHER_RESERVATION_MANAGE` ergibt dieselbe `404 RESERVATION_NOT_FOUND`-Antwort wie eine unbekannte Reservierung. Persönliche Felder fremder Buchungen erscheinen nie in der Raum-Belegungsliste; ein berechtigter Nutzer ruft Einzel-Details separat ab. Verweigerungen und Fehler enthalten keine Buchungsinhalte oder Kontodaten.

## Kompatibilität

- `GET/PUT /api/admin/users/{userId}/roles` und `GET /api/admin/users` aus Feature 003 bleiben Admin-only und behalten ihre Körper und Versionsregeln.
- Die zusätzlichen Felder von `GET /api/auth/roles` sind additiv. Neu verweigerte Aktionen für Nutzer ohne Recht sind die beabsichtigte Verhaltensänderung; bestehende Clients müssen 403 und 404 wie oben behandeln.
