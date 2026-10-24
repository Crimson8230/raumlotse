# Contract: Persönliche Farbpräferenz

Basis: vorhandene sitzungsbasierte Authentifizierung unter `/api`. Alle Antworten mit persönlichen Daten erhalten `Cache-Control: no-store`. Der Benutzer wird ausschließlich aus der authentifizierten Sitzung bestimmt. Alle drei Routen müssen in der zugriffsbeschränkenden `RoleAccessFilter`-Regeltabelle als `IDENTITY` zugelassen werden, unabhängig von `READ` oder Administrationsmodus.

## Datenformen

```json
{
  "effectiveTheme": "LIGHT",
  "selectedTheme": null,
  "onboardingPending": true
}
```

- `effectiveTheme`: `LIGHT` oder `DARK`; tatsächlich anzuwendendes Schema.
- `selectedTheme`: `LIGHT`, `DARK` oder `null`; zuletzt ausdrücklich gewählte gültige Präferenz.
- `onboardingPending`: boolesch; nur für neue Konten vor Auswahl oder Überspringen `true`.
- Falls ein gespeicherter Code unbekannt ist, liefert der Server `effectiveTheme: "LIGHT"` und `selectedTheme: null`, ohne die Anmeldung zu verweigern.

## GET `/api/me/theme-preference`

**Zweck**: Aktuellen kontobezogenen Stand aus dem dauerhaften Speicher lesen, auch nach Änderung in einer anderen Sitzung.

**Anfrage**: Kein Body.

**Erfolg**: `200 OK` mit obiger Datenform. Beispiel nach einer dunklen Auswahl:

```json
{
  "effectiveTheme": "DARK",
  "selectedTheme": "DARK",
  "onboardingPending": false
}
```

## PUT `/api/me/theme-preference`

**Zweck**: `LIGHT` oder `DARK` für das angemeldete Konto speichern und eine offene Erstauswahl abschließen.

**Anfrage**: `Content-Type: application/json`, bestehender CSRF-Header, genau ein Pflichtfeld:

```json
{ "theme": "DARK" }
```

**Erfolg**: `200 OK` mit dem nach dem Speichern gültigen Stand, z. B. `{"effectiveTheme":"DARK","selectedTheme":"DARK","onboardingPending":false}`. Die Rückgabe erfolgt erst nach erfolgreicher Speicherung. Wiederholtes Speichern desselben Codes ist zulässig.

**Validierung**: Fehlendes Feld, `null`, leere Zeichenfolge, unbekannter Code und falscher Feldtyp liefern `400 Bad Request` im vorhandenen `Problem`-Format. Eine abgelehnte Anfrage ändert weder Präferenz noch Erstlogin-Status. Für die erste Version werden zusätzliche Felder abgewiesen, damit Tippfehler nicht stillschweigend akzeptiert werden.

## POST `/api/me/theme-preference/skip`

**Zweck**: Eine noch offene Erstauswahl als abgeschlossen markieren und ohne eigene Auswahl mit `LIGHT` fortfahren.

**Anfrage**: Kein Body; bestehender CSRF-Header.

**Erfolg**: `200 OK` mit dem gespeicherten Stand. Bei noch offener Erstauswahl und fehlender Präferenz lautet er `{"effectiveTheme":"LIGHT","selectedTheme":null,"onboardingPending":false}`. Ist die Erstauswahl schon abgeschlossen, ist die Aktion idempotent und lässt eine inzwischen gespeicherte Farbauswahl unverändert.

## Gemeinsame Fehler und Sicherheitsregeln

| Fall | Antwort | Wirkung |
|---|---|---|
| Keine gültige Sitzung | `401 Unauthorized`, vorhandenes `Problem`-Format | Keine Präferenzdaten oder Änderung. |
| Fehlender/ungültiger CSRF-Token bei PUT/POST | `403 Forbidden`, vorhandenes Fehlerformat | Keine Änderung. |
| Ungültiger PUT-Body | `400 Bad Request`, `Problem` | Keine Änderung. |
| Dauerhafter Speicher vorübergehend nicht erreichbar | `503 Service Unavailable`, `Problem` | Keine bestätigte Änderung; Oberfläche zeigt den zuletzt bestätigten Stand und bietet Wiederholung an. |

Andere Methoden auf diesen Pfaden bleiben durch die bestehende Freigabeliste gesperrt. Fehlerlogs nennen Methode, Pfad und strukturierten Fehlercode, aber weder Kontoidentität noch Präferenz-Body.
