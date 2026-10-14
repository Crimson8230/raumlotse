/** Kontaktadresse der Hilfeseite. Platzhalter: vom Projekt vor dem Betrieb zu setzen. */
export const HELP_CONTACT = 'raumlotse@example.org'

export interface HelpSection {
  heading: string
  paragraphs: string[]
}

/** Feste Texte der Hilfeseite (keine Bearbeitung in der Anwendung). */
export const helpSections: HelpSection[] = [
  {
    heading: 'Raum finden und buchen',
    paragraphs: [
      'Unter „Räume“ suchen Sie nach Name, Gebäude, Plätzen oder Ausstattung. Jede Zeile zeigt den Tag als Streifen und genau eine Aktion.',
      'Im Raum sehen Sie Ihre Buchungen, die Belegungen des Tages und rechts das Formular zum Buchen. Buchen können angemeldete Nutzer mit entsprechender Berechtigung.',
    ],
  },
  {
    heading: 'Bedeutung der Streifen und Muster',
    paragraphs: [
      'Zustände sind immer durch Muster und Wort erkennbar: frei = leer, belegt = Schraffur, meine = volle Tinte, jetzt = blaue Linie, gesperrt = gepunktet.',
    ],
  },
  {
    heading: 'Kontakt',
    paragraphs: ['Bei Fragen oder Problemen wenden Sie sich an die unten genannte Adresse.'],
  },
]
