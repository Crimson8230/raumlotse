import type { MouseEvent, ReactNode } from 'react'
import '../ui.css'

interface SkipLinkProps {
  /** Id des Hauptinhalts. Seitenvertrag: `<main id="inhalt">`. */
  targetId?: string
  children?: ReactNode
}

/**
 * Sprunglink "Zum Inhalt springen". Seitenvertrag: erstes fokussierbares Element der Seite, und der
 * Hauptinhalt trägt die Id `targetId` (Standard `inhalt`). Eingebunden in `RequireAuth` und `LoginPage` (Feature 019).
 */
export function SkipLink({ targetId = 'inhalt', children = 'Zum Inhalt springen' }: SkipLinkProps) {
  function jump(event: MouseEvent<HTMLAnchorElement>) {
    const target = document.getElementById(targetId)
    if (!target) return
    event.preventDefault()
    if (!target.hasAttribute('tabindex')) target.setAttribute('tabindex', '-1')
    target.focus()
  }

  return (
    <a href={`#${targetId}`} className="ui-skip-link ui-target" onClick={jump}>
      {children}
    </a>
  )
}
