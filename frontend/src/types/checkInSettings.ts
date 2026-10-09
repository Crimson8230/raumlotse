import { z } from 'zod'

/** Admin-editable check-in times (feature 014, FR-022); the ranges mirror the backend validation. */
export const checkInSettingsSchema = z.object({
  earlyCheckInMinutes: z.number({ message: 'Bitte eine ganze Zahl eingeben.' }).int('Bitte eine ganze Zahl eingeben.')
    .min(0, 'Der frühe Check-in muss zwischen 0 und 60 Minuten liegen.')
    .max(60, 'Der frühe Check-in muss zwischen 0 und 60 Minuten liegen.'),
  gracePeriodMinutes: z.number({ message: 'Bitte eine ganze Zahl eingeben.' }).int('Bitte eine ganze Zahl eingeben.')
    .min(1, 'Die Kulanzzeit muss zwischen 1 und 30 Minuten liegen.')
    .max(30, 'Die Kulanzzeit muss zwischen 1 und 30 Minuten liegen.'),
})

export type CheckInSettingsUpdate = z.infer<typeof checkInSettingsSchema>

export interface CheckInSettings extends CheckInSettingsUpdate {
  updatedAt: string
}
