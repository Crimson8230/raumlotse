import { describe, expect, it } from 'vitest'
import { stateVocabulary, windowPriority } from './vocabulary'

describe('state vocabulary', () => {
  it('defines the five states with a word and a distinct pattern each', () => {
    const states = Object.keys(stateVocabulary)
    expect(states.sort()).toEqual(['belegt', 'frei', 'gesperrt', 'jetzt', 'meine'])
    const patterns = Object.values(stateVocabulary).map((definition) => definition.pattern)
    expect(new Set(patterns).size).toBe(5)
    for (const definition of Object.values(stateVocabulary)) {
      expect(definition.word.length).toBeGreaterThan(0)
    }
  })

  it('lets the stricter state win when windows overlap', () => {
    expect(windowPriority).toEqual(['gesperrt', 'belegt', 'meine', 'frei'])
  })
})
