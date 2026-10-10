# Rollenrechte: Oberflächenvertrag

## Rollenrechteverwaltung

- Neue Route `/admin/roles` innerhalb des bestehenden Administrationsmodus, zusätzlich mit fester Admin-Prüfung. Andere Verwaltungsrechte dürfen diese Seite und ihren Navigationslink nicht freigeben.
- Der Admin wählt eine der fünf festen Rollen und sieht alle 14 Rechte mit Bezeichnung, kurzer Funktionsbeschreibung und gespeichertem Ja/Nein-Zustand. Die feste Befugnis „Rollen und Rollenrechte verwalten“ ist für Admin sichtbar als unveränderlich und für andere Rollen als nicht verfügbar.
- Mehrere Rechte können vor einem gemeinsamen Speichern geändert werden. „Abbrechen“ verwirft den Entwurf. Beim Speichern werden fehlendes `READ`, unbekannte Rechte und eine veraltete Version verständlich erklärt; bei Konflikt werden die aktuellen Rechte neu geladen und kein Entwurf stillschweigend darübergeschrieben.
- Während des Ladens, bei leerer/fehlgeschlagener Antwort und nach einem erfolgreichen Speichern zeigt die Seite jeweils einen eindeutigen Zustand. Eine nicht verfügbare Rechteprüfung zeigt keine optimistisch freigegebenen Aktionen.

## Nutzeransicht und Administrationsmodus

- Navigation und Routen prüfen `permissions` aus `GET /api/auth/roles`; `ADMIN` allein steuert nur die feste Rollenverwaltung. Nach Rollen- oder Rechteänderung, Seitenwechsel und Fensterfokus wird der aktuelle Zustand neu gelesen. Ein 403 wegen fehlendem Funktionsrecht löst eine Aktualisierung aus.
- Lesen steuert Raum-, Karten- und Buchungsübersichten. Reservieren steuert die Neuanlage. Eigene und fremde Reservierungsaktionen werden anhand der vom Server ermittelten Eigentumsanzeige und separater Rechte angeboten; der Server prüft dafür die stabile Ersteller-ID. Fremde Belegungszeilen zeigen weiterhin keine Personendaten; berechtigte Bearbeiter öffnen Einzel-Details.
- Der Modusschalter erscheint bei `canUseAdminMode`. Seine Aktivierung blendet nur erlaubte Verwaltungsbereiche ein. `LocationCatalogPage` trennt Gebäude, Stockwerke und Ausstattungsarten; `MapPage` trennt Karten, Positionen und Verbindungen. Raum-, Statistik- und Rollenverwaltung haben ihre eigenen Prüfungen.
- Eine erlaubte Verwaltungsseite mit ausgeschaltetem Modus bietet das Einschalten an. Eine nicht erlaubte Seite zeigt „nicht verfügbar“. Fehlt einem Nutzer jedes konfigurierbare Recht und ist er kein Admin, zeigt die Startansicht einen verständlichen Hinweis statt leerer Navigation.
- Server-Antworten bleiben maßgeblich. Der Modus gewährt keine zusätzliche Berechtigung und ein veralteter Schaltflächenzustand kann keine Aktion freigeben.
