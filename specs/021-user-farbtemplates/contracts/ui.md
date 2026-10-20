# UI Contract: Farbschema-Auswahl

## Anwendungsrahmen und Laden

Nach erfolgreicher Authentifizierung lädt der geschützte Anwendungsrahmen `GET /api/me/theme-preference`, bevor reguläre Seiteninhalte erscheinen. Währenddessen wird ein neutraler, zugänglicher Ladestatus angezeigt. Bei einem Abruffehler wird ein verständlicher Fehler mit „Erneut versuchen“ angezeigt; der Benutzer wird nicht irrtümlich als abgemeldet behandelt. Bei `401` greift die bestehende Sitzungsablauf-Behandlung. Bis der kontobezogene Stand bekannt ist, wird keine frühere Kontofarbe angewandt.

Nach erfolgreichem Abruf setzt der Anwendungsrahmen `effectiveTheme` für reguläre geschützte Ansichten. Beim Verlassen der authentifizierten Sitzung wird das Theme-Attribut entfernt und das helle Standardschema aktiv. Nach einem erfolgreichen PUT gilt die zurückgelieferte Farbe sofort, auch ohne Seitenneuladen. Bei fehlgeschlagenem PUT bleibt der zuletzt bestätigte Stand aktiv und sichtbar.

## Einmalige Erstauswahl

Bei `onboardingPending=true` erscheint vor der regulären Startseite bzw. vor einem direkt angesteuerten geschützten Ziel die Erstauswahl. Sie zeigt „Hell“ und „Dunkel“ mit Namen und Vorschau sowie „Auswahl speichern“ und „Später auswählen“. Ein Radio- oder gleichwertiges Muster macht den Auswahlzustand für Tastatur und Vorlesehilfe eindeutig. Die ursprüngliche Zieladresse einschließlich Suchparametern bleibt nach erfolgreichem Abschluss erhalten. Bei fehlgeschlagenem Speichern oder Überspringen bleibt die Auswahl offen und die Fehlermeldung wird vorgelesen. Das Erscheinen der Auswahl ändert keine Rollenrechte.

## Persönliche Einstellungen

`/settings` liegt innerhalb der authentifizierten Routen, außerhalb des administrativen Modus. Ein Link „Persönliche Einstellungen“ ist in der regulären Navigation für jede angemeldete Rolle erreichbar, auch wenn kein `READ`-Recht vorhanden ist. Der bisherige Link `/admin/settings` wird eindeutig als „Systemeinstellungen“ bezeichnet. Auf `/settings` sind das wirksame Schema und gegebenenfalls die eigene Auswahl erkennbar. Ein Wechsel erfordert eine bewusste Bestätigung; Erfolg und Fehler werden verständlich gemeldet. Der Benutzer kann nach einem Fehler erneut speichern.

## Farbkontext und Zugänglichkeit

Der helle Satz der vorhandenen semantischen Farbvariablen bleibt der Standard. Der dunkle Satz definiert Hintergrund, Flächen, Text, gedämpften Text, Konturen, Haarlinien, Links/Fokus, Fehler und Statusflächen. Seiten mit festen Farbwerten werden geprüft. Fokus muss auf beiden Schemata sichtbar bleiben; Text und interaktive Elemente erfüllen WCAG 2.1 AA. Status wie Belegung, Warnung oder aktive Navigation bleiben zusätzlich durch Text, Kontur oder Muster verständlich. Die Auswahl und ihre Vorschau bleiben ab 320 px und bei vergrößerter Ansicht ohne horizontales Scrollen bedienbar.

`/login` und `/hilfe` ohne Anmeldung behalten das bisherige helle Schema. Die eigenständige Raumanzeige unter `/rooms/:roomId/display` bleibt trotz geschützter Route in ihrem bisherigen festen Farbkontext. Beim Kontowechsel darf die Farbe des vorherigen Kontos nicht auf regulären Seiten des neuen Kontos sichtbar werden.
