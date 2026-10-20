# Data Model: Persönliche Farbtemplates

## Farbschema-Katalog

Der Katalog ist für diese Version fest: `LIGHT` („Hell“) und `DARK` („Dunkel“). `LIGHT` entspricht dem bisherigen „Papier & Tinte“-Design. Der Katalog ist kein eigener Datenbankbestand. Nicht unterstützte gespeicherte Codes werden beim Lesen als `LIGHT` wirksam, aber nicht als gewählte Präferenz ausgegeben.

## Benutzerkonto und Farbpräferenz

Die bestehende Entität `UserAccount` wird um zwei kontobezogene Felder ergänzt:

| Feld | Typ | Bedeutung | Regel |
|---|---|---|---|
| `theme_code` | optionale kurze Zeichenfolge | Vom Benutzer ausdrücklich gewähltes Farbschema | Bei neuen Schreibvorgängen nur `LIGHT` oder `DARK`; `NULL` heißt keine eigene Auswahl. |
| `theme_onboarding_completed` | boolesch, nicht null | Einmalige Erstauswahl abgeschlossen | Bestehende Konten werden auf `true` gesetzt; neue Konten starten mit `false`. |

Die `user_account.id` bleibt der alleinige Schlüssel der Präferenz. Ein fremder Benutzerbezug wird nicht aus einer Anfrage übernommen. Für die Migration: vorhandene Zeilen erhalten `theme_onboarding_completed=true`, anschließend wird der Standard für neu angelegte Zeilen `false`; `theme_code` beginnt mit `NULL`. Die nächste freie Flyway-Version vor der Umsetzung prüfen (aktuell folgt `V18` auf `V17`).

## Abgeleitete Werte

`effectiveTheme` ist `theme_code`, wenn der Code unterstützt wird, sonst `LIGHT`. `selectedTheme` ist `theme_code`, wenn unterstützt, sonst `null`. `onboardingPending` ist das Gegenteil von `theme_onboarding_completed`. Diese drei Werte bilden die persönliche API-Antwort. Ein unbekannter gespeicherter Code darf das Konto nicht sperren.

## Zustandsübergänge

| Vorher | Aktion | Nachher |
|---|---|---|
| Neues Konto: `theme_code=NULL`, `completed=false` | Erstlogin lädt Präferenz | Unverändert; Auswahl wird gezeigt. |
| Offene Auswahl | `PUT` mit `LIGHT` oder `DARK` | Gewählter Code; `completed=true`. |
| Offene Auswahl | `POST .../skip` | `theme_code=NULL`; `completed=true`; wirksam `LIGHT`. |
| Abgeschlossene Auswahl | `PUT` mit `LIGHT` oder `DARK` | Neuer gewählter Code; `completed=true`. |
| Abgeschlossene Auswahl | `POST .../skip` (z. B. verspätete Wiederholung) | Unverändert; bereits gespeicherte Auswahl bleibt erhalten. |
| Beliebiger Zustand | Ungültiger Schreibwert oder Speicherfehler | Unverändert; Fehlerantwort. |

Zwei erfolgreiche Änderungen desselben Kontos werden serialisiert; nach dem nächsten Laden gilt die zuletzt erfolgreich abgeschlossene Änderung. Ein `POST .../skip` darf einen parallel gespeicherten Theme-Code niemals wieder entfernen.

## Validierung und Datenschutz

- API-Schreibanfrage: genau ein nicht leerer Code aus `LIGHT` oder `DARK`; fehlende, `null` oder unbekannte Codes sind ungültig. Die Skip-Aktion hat keinen Request-Body.
- Alle persönlichen Zugriffe verlangen eine gültige Sitzung und bei Änderungen den bestehenden CSRF-Schutz.
- Antworten und Logs enthalten keine E-Mail-Adresse, keinen Namen und keinen Passwortwert; API-Antworten geben nur die drei Präferenzwerte zurück.
- Präferenzabrufe werden nicht für andere Benutzer adressierbar gemacht und nicht als gemeinsamer Browserwert gespeichert.
