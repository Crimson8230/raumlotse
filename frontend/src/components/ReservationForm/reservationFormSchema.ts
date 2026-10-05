import { z } from 'zod'

const instant = z.string().refine((value) => Number.isFinite(Date.parse(value)), 'Invalid date/time')

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
  { message: 'End time must be strictly after start time.', path: ['endTime'] },
)
