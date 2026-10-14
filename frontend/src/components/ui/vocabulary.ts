export type OccupancyState = 'frei' | 'belegt' | 'meine' | 'jetzt' | 'gesperrt'

export type PatternKind = 'empty' | 'hatch' | 'solid' | 'line' | 'dotted'

export interface StateDefinition {
  state: OccupancyState
  /** Anzeige- und Vorlesetext; jeder Zustand trägt immer ein Wort (nie nur Muster oder Farbe). */
  word: string
  pattern: PatternKind
  tone: 'free' | 'busy' | 'ink' | 'primary' | 'muted'
}

/** Gemeinsame Zustandssprache von Streifen, Chip und späteren Seiten ("Papier & Tinte"). */
export const stateVocabulary: Record<OccupancyState, StateDefinition> = {
  frei: { state: 'frei', word: 'frei', pattern: 'empty', tone: 'free' },
  belegt: { state: 'belegt', word: 'belegt', pattern: 'hatch', tone: 'busy' },
  meine: { state: 'meine', word: 'meine', pattern: 'solid', tone: 'ink' },
  jetzt: { state: 'jetzt', word: 'jetzt', pattern: 'line', tone: 'primary' },
  gesperrt: { state: 'gesperrt', word: 'gesperrt', pattern: 'dotted', tone: 'muted' },
}

/** Bei überlappenden Zeitfenstern gewinnt der strengere Zustand. */
export const windowPriority: readonly OccupancyState[] = ['gesperrt', 'belegt', 'meine', 'frei']
