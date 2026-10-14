import { ArrowLeft, ArrowRight, ArrowUp, ChevronsDown, ChevronsUp, type LucideIcon } from 'lucide-react'
import '../ui.css'
import './Wayfinding.css'

export type WayDirection = 'geradeaus' | 'links' | 'rechts' | 'hoch' | 'runter'

const icons: Record<WayDirection, LucideIcon> = {
  geradeaus: ArrowUp,
  links: ArrowLeft,
  rechts: ArrowRight,
  hoch: ChevronsUp,
  runter: ChevronsDown,
}

interface WayfindingProps {
  direction: WayDirection
  /** z. B. "Aufzug", "Raum 2.14", "Etage 2". */
  target: string
  /** Bereits formatiert, z. B. "ca. 40 m" oder "1 Min."; die Berechnung ist nicht Aufgabe des Bausteins. */
  distance?: string
  href?: string
  onClick?: () => void
}

/** Wegweiser: Ziel und Richtung stehen als Text, das Symbol ist nur Ergänzung. */
export function Wayfinding({ direction, target, distance, href, onClick }: WayfindingProps) {
  const Icon = icons[direction]
  const content = (
    <>
      <Icon className="ui-wayfinding__icon" size={20} aria-hidden="true" />
      <span className="ui-wayfinding__text">
        <span className="ui-wayfinding__target">{target}</span>
        {' · '}
        <span>{direction}</span>
        {distance && (
          <>
            {' · '}
            <span className="ui-wayfinding__distance">{distance}</span>
          </>
        )}
      </span>
    </>
  )

  if (href) {
    return (
      <a className="ui-wayfinding ui-wayfinding--interactive ui-target" href={href}>
        {content}
      </a>
    )
  }
  if (onClick) {
    return (
      <button type="button" className="ui-wayfinding ui-wayfinding--interactive ui-target" onClick={onClick}>
        {content}
      </button>
    )
  }
  return <span className="ui-wayfinding">{content}</span>
}
