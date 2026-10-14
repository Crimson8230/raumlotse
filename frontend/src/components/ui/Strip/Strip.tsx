import { useState } from 'react'
import { stateVocabulary } from '../vocabulary'
import { buildAccessibleName, buildSegments, formatTime, percent, stateAt, type TimeWindow } from './stripModel'
import '../ui.css'
import './Strip.css'

export type { TimeWindow } from './stripModel'

interface StripProps {
  /** Raumname; wird sichtbar in Serif gezeigt und dem Namen des Streifens vorangestellt. */
  name?: string
  rangeStart: Date | string
  rangeEnd: Date | string
  windows: TimeWindow[]
  /** Aktueller Zeitpunkt; ohne Angabe der Zeitpunkt des ersten Renderns (Seiten übergeben eine laufende Uhrzeit). Außerhalb des Bereichs gibt es keine "Jetzt"-Marke. */
  now?: Date | string
}

/**
 * Tagesstreifen: frei = leer, belegt = Schraffur, meine = volle Tinte, gesperrt = gepunktet, dazu die "Jetzt"-Marke.
 * Der zugängliche Name nennt alle Zeitfenster in zeitlicher Reihenfolge und den aktuellen Zustand in Worten.
 */
export function Strip({ name, rangeStart, rangeEnd, windows, now }: StripProps) {
  const from = new Date(rangeStart).getTime()
  const to = new Date(rangeEnd).getTime()
  const segments = buildSegments(rangeStart, rangeEnd, windows)
  const [mountedAt] = useState(() => Date.now())
  const nowMs = new Date(now ?? mountedAt).getTime()
  const nowInRange = segments.length > 0 && nowMs >= from && nowMs <= to
  const current = nowInRange ? stateAt(segments, nowMs) : null
  const label = buildAccessibleName(segments, nowInRange ? nowMs : null, name)

  return (
    <div className="ui-strip-block">
      {name && <span className="ui-strip__name">{name}</span>}
      <div className="ui-strip" role="img" aria-label={label}>
        {segments.map((segment) => {
          const definition = stateVocabulary[segment.state]
          return (
            <div
              key={`${segment.start}-${segment.state}`}
              className={`ui-strip__segment ui-pattern-${definition.pattern}`}
              data-state={segment.state}
              style={{
                left: percent(segment.start, from, to),
                width: `${+(((segment.end - segment.start) / (to - from)) * 100).toFixed(3)}%`,
              }}
            >
              <span className="ui-strip__word">{definition.word}</span>
            </div>
          )
        })}
        {nowInRange && current && (
          <div className="ui-strip__now" style={{ left: percent(nowMs, from, to) }}>
            <span className="ui-strip__now-label">Jetzt</span>
          </div>
        )}
      </div>
      {segments.length > 0 && (
        <div className="ui-strip__times" aria-hidden="true">
          <span className="ui-strip__time">{formatTime(from)}</span>
          <span className="ui-strip__time">{formatTime(to)}</span>
        </div>
      )}
    </div>
  )
}
