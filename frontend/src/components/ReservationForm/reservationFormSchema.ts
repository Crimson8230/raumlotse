import { z } from 'zod'

const instant = z.string().refine((value) => Number.isFinite(Date.parse(value)), 'Ungültiges Datum oder ungültige Uhrzeit')

export const reservationFormSchema = z.object({
  startTime: instant,
  endTime: instant,
  seatingArrangementId: z.uuid(),
  expectedAttendees: z.number().int().positive(),
  reservedFor: z.string().trim().min(1).max(255),
  note: z.string().max(2000).optional(),
  additionalEquipmentTypeIds: z.array(z.uuid()).optional(),
  emailNotification: z.boolean(),
}).refine(
  ({ startTime, endTime }) => Date.parse(endTime) > Date.parse(startTime),
  { message: 'Das Ende muss nach dem Beginn liegen.', path: ['endTime'] },
)
