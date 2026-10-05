import { describe, expect, it } from 'vitest'
import { reservationFormSchema } from './reservationFormSchema'

const validPayload = {
  startTime: '2026-10-05T10:00:00.000Z',
  endTime: '2026-10-05T11:00:00.000Z',
  seatingArrangementId: '00000000-0000-4000-8000-000000000001',
  expectedAttendees: 4,
  reservedFor: 'Projektgruppe Web',
  additionalEquipmentTypeIds: ['00000000-0000-4000-8000-000000000002'],
  emailNotification: false,
}

describe('reservationFormSchema', () => {
  it('accepts the complete contract payload', () => {
    expect(reservationFormSchema.safeParse(validPayload).success).toBe(true)
  })

  it.each([
    { seatingArrangementId: 'seat-1' },
    { additionalEquipmentTypeIds: ['eq-1'] },
    { emailNotification: 'true' },
    { emailNotification: undefined },
    { expectedAttendees: 1.5 },
    { expectedAttendees: 0 },
    { reservedFor: '' },
    { note: 'x'.repeat(2001) },
  ])('rejects manipulated payload %o', (override) => {
    expect(reservationFormSchema.safeParse({ ...validPayload, ...override }).success).toBe(false)
  })

  it('requires end to be strictly after start', () => {
    expect(reservationFormSchema.safeParse({ ...validPayload, endTime: validPayload.startTime }).success).toBe(false)
  })
})
