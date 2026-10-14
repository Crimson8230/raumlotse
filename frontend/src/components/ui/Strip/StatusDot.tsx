import { stateVocabulary, type OccupancyState } from '../vocabulary'
import '../ui.css'
import './Strip.css'

interface StatusDotProps {
  state: OccupancyState
}

/** Kleiner Statuspunkt für Listenzeilen: Muster sichtbar, Zustandswort für Hilfstechnik. */
export function StatusDot({ state }: StatusDotProps) {
  const { pattern, word } = stateVocabulary[state]
  return (
    <span className="ui-status-dot-wrap">
      <span className={`ui-status-dot ui-pattern-${pattern}`} aria-hidden="true" />
      <span className="ui-sr-only">{word}</span>
    </span>
  )
}
