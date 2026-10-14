import { describe, expect, it } from 'vitest'
import { defaultFieldLabels, fieldLabel } from './fieldLabels'

describe('fieldLabel', () => {
  it('maps known technical names to German labels', () => {
    expect(fieldLabel('startTime')).toBe('Beginn')
    expect(fieldLabel('endTime')).toBe('Ende')
  })

  it('lets pages extend or override the mapping', () => {
    expect(fieldLabel('startTime', { startTime: 'Von' })).toBe('Von')
    expect(fieldLabel('eventTitle', { eventTitle: 'Veranstaltung' })).toBe('Veranstaltung')
  })

  it('humanizes unknown names instead of showing the technical string', () => {
    expect(fieldLabel('some_field')).toBe('Some field')
    expect(fieldLabel('someOtherField')).toBe('Some other field')
  })

  it('has a label for every default entry', () => {
    for (const label of Object.values(defaultFieldLabels)) {
      expect(label.length).toBeGreaterThan(0)
    }
  })
})
