import { z } from 'zod'

export function reservationMetadataSchema(maxCapacity?: number) {
  return z.object({
    expectedAttendees: z
      .string()
      .regex(/^\d+$/, 'Die Teilnehmerzahl muss eine ganze Zahl ab 1 sein.')
      .transform(Number)
      .pipe(z.number().int().min(1, 'Die Teilnehmerzahl muss eine ganze Zahl ab 1 sein.'))
      .superRefine((value, context) => {
        if (maxCapacity !== undefined && value > maxCapacity) {
          context.addIssue({
            code: 'custom',
            message: `Die Teilnehmerzahl (${value}) überschreitet die Kapazität der Sitzordnung (${maxCapacity}).`,
          })
        }
      }),
    reservedFor: z.string().trim().min(1, 'Reserviert für ist ein Pflichtfeld.').max(255, 'Reserviert für darf maximal 255 Zeichen lang sein.'),
    note: z.string().max(2000, 'Notizen dürfen maximal 2000 Zeichen lang sein.'),
  })
}
