import { stateVocabulary, windowPriority, type OccupancyState } from '../vocabulary'

export type WindowState = Exclude<OccupancyState, 'frei' | 'jetzt'>

export interface TimeWindow {
  start: Date | string
  end: Date | string
  state: WindowState
  label?: string
}

export interface Segment {
  /** Millisekunden seit Epoche, innerhalb des Bereichs. */
  start: number
  end: number
  state: OccupancyState
  label?: string
}

/** Höchstzahl vorgelesener Zeitfenster; der Rest wird zusammengefasst. */
export const MAX_SPOKEN_SEGMENTS = 12

const toMs = (value: Date | string) => new Date(value).getTime()

function twoDigits(value: number): string {
  return String(value).padStart(2, '0')
}

export function formatTime(ms: number): string {
  const date = new Date(ms)
  return `${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}`
}

/** Zerlegt den Bereich in lückenlose Segmente: Randfenster abgeschnitten, Überlappungen nach Priorität, Gleiches verschmolzen. */
export function buildSegments(rangeStart: Date | string, rangeEnd: Date | string, windows: TimeWindow[]): Segment[] {
  const from = toMs(rangeStart)
  const to = toMs(rangeEnd)
  if (!(to > from)) return []

  const clamped = windows
    .map((window) => ({
      start: Math.max(toMs(window.start), from),
      end: Math.min(toMs(window.end), to),
      state: window.state,
      label: window.label,
    }))
    .filter((window) => window.end > window.start)

  const boundaries = [...new Set([from, to, ...clamped.flatMap((window) => [window.start, window.end])])].sort(
    (a, b) => a - b,
  )

  const segments: Segment[] = []
  for (let i = 0; i < boundaries.length - 1; i += 1) {
    const start = boundaries[i]
    const end = boundaries[i + 1]
    const covering = clamped.filter((window) => window.start <= start && window.end >= end)
    covering.sort((a, b) => windowPriority.indexOf(a.state) - windowPriority.indexOf(b.state))
    const winner = covering[0]
    const state: OccupancyState = winner ? winner.state : 'frei'
    const label = winner?.label
    const previous = segments[segments.length - 1]
    if (previous && previous.state === state && previous.label === label) {
      previous.end = end
    } else {
      segments.push({ start, end, state, label })
    }
  }
  return segments
}

function describeSegment(segment: Segment): string {
  const text = `${formatTime(segment.start)}–${formatTime(segment.end)} ${stateVocabulary[segment.state].word}`
  return segment.label ? `${text} (${segment.label})` : text
}

/** Der Zustand zum Zeitpunkt; null, wenn der Zeitpunkt außerhalb des Bereichs liegt. */
export function stateAt(segments: Segment[], now: number): OccupancyState | null {
  if (segments.length === 0) return null
  const first = segments[0]
  const last = segments[segments.length - 1]
  if (now < first.start || now > last.end) return null
  return (segments.find((segment) => now >= segment.start && now < segment.end) ?? last).state
}

export function buildAccessibleName(
  segments: Segment[],
  now: number | null,
  name?: string,
): string {
  const prefix = name ? `${name}: ` : ''
  if (segments.length === 0) return `${prefix}kein Zeitraum.`

  let body: string
  if (segments.length === 1 && segments[0].state === 'frei') {
    body = `ganztägig frei (${formatTime(segments[0].start)}–${formatTime(segments[0].end)})`
  } else {
    const spoken = segments.slice(0, MAX_SPOKEN_SEGMENTS).map(describeSegment)
    const rest = segments.length - spoken.length
    if (rest > 0) spoken.push(`und ${rest} weitere Zeitfenster`)
    body = spoken.join(', ')
  }

  let result = `${prefix}${body}.`
  if (now !== null) {
    const current = stateAt(segments, now)
    if (current) result += ` Jetzt: ${formatTime(now)}, ${stateVocabulary[current].word}.`
  }
  return result
}

/** Prozentwert ohne Gleitkomma-Rauschen, z. B. "12.5%". */
export function percent(value: number, from: number, to: number): string {
  return `${+(((value - from) / (to - from)) * 100).toFixed(3)}%`
}
