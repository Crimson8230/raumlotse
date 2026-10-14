import type { ReactNode } from 'react'
import { Footer } from '../components/Footer/Footer'
import { SkipLink } from '../components/ui'
import { HELP_CONTACT, helpSections } from './helpContent'

/** Statische Hilfeseite (öffentlich, auch vom Login aus erreichbar); Angemeldete bekommen die Navigation übergeben. */
export default function HelpPage({ navigation }: { navigation?: ReactNode }) {
  return (
    <>
      <SkipLink targetId="inhalt" />
      {navigation}
      <main id="inhalt" className="help-page">
        <h1>Hilfe</h1>
        {helpSections.map((section) => (
          <section key={section.heading}>
            <h2>{section.heading}</h2>
            {section.paragraphs.map((text) => (
              <p key={text}>{text}</p>
            ))}
            {section.heading === 'Kontakt' && (
              <p>
                <a href={`mailto:${HELP_CONTACT}`}>{HELP_CONTACT}</a>
              </p>
            )}
          </section>
        ))}
      </main>
      <Footer />
    </>
  )
}
