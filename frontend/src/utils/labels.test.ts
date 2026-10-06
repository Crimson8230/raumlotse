import { describe, expect, it } from 'vitest'
import { entityStatusLabel, reservationStatusLabels } from './labels'

describe('labels', () => {
  it('names entity statuses in German', () => {
    expect(entityStatusLabel('ACTIVE')).toBe('Aktiv')
    expect(entityStatusLabel('DEACTIVATED')).toBe('Deaktiviert')
  })

  it('names every reservation status', () => {
    expect(Object.values(reservationStatusLabels).every((label) => label.length > 0)).toBe(true)
    expect(reservationStatusLabels.CANCELLED).toBe('Storniert')
  })
})
