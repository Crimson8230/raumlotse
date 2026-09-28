import { z } from 'zod'
import { emptySearchFormState } from '../../types/room'
import type { RoomSearchFormState } from '../../types/room'

const WHOLE_NUMBER = 'Bitte eine ganze Zahl ab 1 eingeben.'
const MIN_GREATER_MAX = 'Min. Personen darf nicht größer als Max. Personen sein.'
const INCOMPLETE_WINDOW = 'Bitte Datum, Von und Bis vollständig angeben.'
const END_NOT_AFTER_START = 'Bis muss nach Von liegen.'

// Empty means "no filter"; otherwise a whole number >= 1 (FR-003).
const personCount = z
  .string()
  .regex(/^\d*$/, WHOLE_NUMBER)
  .refine((value) => value === '' || Number(value) >= 1, WHOLE_NUMBER)

export const roomSearchSchema = z
  .object({
    minPersons: personCount,
    maxPersons: personCount,
    buildingId: z.string(),
    seatingArrangement: z.string(),
    equipmentTypeIds: z.array(z.string()),
    barrierFree: z.boolean(),
    date: z.string(),
    startTime: z.string(),
    endTime: z.string(),
  })
  .superRefine((state, ctx) => {
    const min = Number(state.minPersons)
    const max = Number(state.maxPersons)
    if (state.minPersons !== '' && state.maxPersons !== '' && min >= 1 && max >= 1 && min > max) {
      ctx.addIssue({ code: 'custom', message: MIN_GREATER_MAX, path: ['maxPersons'] })
    }
    // The time window is all-or-nothing (FR-014); partial input is rejected instead of ignored.
    const windowParts = [state.date, state.startTime, state.endTime].filter((part) => part !== '')
    if (windowParts.length > 0 && windowParts.length < 3) {
      ctx.addIssue({ code: 'custom', message: INCOMPLETE_WINDOW, path: ['date'] })
    } else if (windowParts.length === 3 && state.endTime <= state.startTime) {
      ctx.addIssue({ code: 'custom', message: END_NOT_AFTER_START, path: ['endTime'] })
    }
  })

/** Field name → first validation message; empty when the form is valid. */
export function validate(state: RoomSearchFormState): Partial<Record<keyof RoomSearchFormState, string>> {
  const result = roomSearchSchema.safeParse(state)
  const errors: Partial<Record<keyof RoomSearchFormState, string>> = {}
  if (result.success) return errors
  for (const issue of result.error.issues) {
    const field = issue.path[0] as keyof RoomSearchFormState
    errors[field] ??= issue.message
  }
  return errors
}

const TEXT_FIELDS = [
  'minPersons',
  'maxPersons',
  'buildingId',
  'seatingArrangement',
  'date',
  'startTime',
  'endTime',
] as const
const EQUIPMENT_PARAM = 'equipmentTypeId'

export function parseSearchParams(params: URLSearchParams): RoomSearchFormState {
  const state: RoomSearchFormState = { ...emptySearchFormState }
  for (const field of TEXT_FIELDS) {
    state[field] = params.get(field) ?? ''
  }
  state.equipmentTypeIds = params.getAll(EQUIPMENT_PARAM)
  state.barrierFree = params.get('barrierFree') === 'true'
  return state
}

/** Page URL representation; only filled fields are written. */
export function toSearchParams(state: RoomSearchFormState): URLSearchParams {
  const params = new URLSearchParams()
  for (const field of TEXT_FIELDS) {
    if (state[field] !== '') params.set(field, state[field])
  }
  for (const id of state.equipmentTypeIds) params.append(EQUIPMENT_PARAM, id)
  if (state.barrierFree) params.set('barrierFree', 'true')
  return params
}

/** Query string for `GET /api/rooms/search` (contracts/room-search-api.yaml). */
export function toApiQuery(state: RoomSearchFormState): URLSearchParams {
  const query = new URLSearchParams()
  if (state.minPersons !== '') query.set('minPersons', state.minPersons)
  if (state.maxPersons !== '') query.set('maxPersons', state.maxPersons)
  if (state.buildingId !== '') query.set('buildingId', state.buildingId)
  if (state.seatingArrangement !== '') query.set('seatingArrangement', state.seatingArrangement)
  for (const id of state.equipmentTypeIds) query.append(EQUIPMENT_PARAM, id)
  if (state.barrierFree) query.set('barrierFree', 'true')
  const window = toInstantWindow(state)
  if (window) {
    query.set('from', window.from)
    query.set('to', window.to)
  }
  return query
}

/** Local date + times → ISO instants in the browser's time zone, as the booking form does; null if incomplete. */
function toInstantWindow(state: RoomSearchFormState): { from: string; to: string } | null {
  if (state.date === '' || state.startTime === '' || state.endTime === '') return null
  return {
    from: new Date(`${state.date}T${state.startTime}`).toISOString(),
    to: new Date(`${state.date}T${state.endTime}`).toISOString(),
  }
}

/** Result link; with a complete window it carries `start`/`end` for the booking pre-fill (FR-011a). */
export function roomLink(roomId: string, state: RoomSearchFormState): string {
  const window = toInstantWindow(state)
  if (!window) return `/rooms/${roomId}`
  return `/rooms/${roomId}?${new URLSearchParams({ start: window.from, end: window.to }).toString()}`
}

/** "Buchen" link (FR-011b): always opens the booking form, pre-filled when the search has a complete window. */
export function bookingLink(roomId: string, state: RoomSearchFormState): string {
  const params = new URLSearchParams({ book: 'true' })
  const window = toInstantWindow(state)
  if (window) {
    params.set('start', window.from)
    params.set('end', window.to)
  }
  return `/rooms/${roomId}?${params.toString()}`
}
