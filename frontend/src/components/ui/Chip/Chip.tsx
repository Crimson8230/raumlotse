import type { ReactNode } from 'react'
import { stateVocabulary, type OccupancyState } from '../vocabulary'
import '../ui.css'
import './Chip.css'

type ChipProps = (
  | { state: OccupancyState; children?: ReactNode }
  | { state?: undefined; children: ReactNode }
) & {
  /** Macht den Chip zu einer Schaltfläche (mind. 44 px). */
  onClick?: () => void
}

/** Statusmarke: enthält immer ein Wort; Zustände unterscheiden sich durch Muster, nicht nur durch Farbton. */
export function Chip({ state, children, onClick }: ChipProps) {
  const definition = state ? stateVocabulary[state] : undefined
  const content = (
    <>
      {definition && <span className={`ui-chip__swatch ui-pattern-${definition.pattern}`} aria-hidden="true" />}
      <span>{children ?? definition?.word}</span>
    </>
  )
  const className = `ui-chip${definition ? '' : ' ui-chip--neutral'}`

  if (onClick) {
    return (
      <button type="button" className={`${className} ui-chip--interactive ui-target`} onClick={onClick}>
        {content}
      </button>
    )
  }
  return <span className={className}>{content}</span>
}
