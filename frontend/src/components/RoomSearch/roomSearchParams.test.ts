import { describe, expect, it } from 'vitest'
import { emptySearchFormState } from '../../types/room'
import { bookingLink, parseSearchParams, roomLink, toApiQuery, toSearchParams, validate } from './roomSearchParams'

const WHOLE_NUMBER = 'Bitte eine ganze Zahl ab 1 eingeben.'
const MIN_GREATER_MAX = 'Min. Personen darf nicht größer als Max. Personen sein.'

describe('roomSearchParams', () => {
  it('parses an empty URL into the empty form state', () => {
    expect(parseSearchParams(new URLSearchParams())).toEqual(emptySearchFormState)
  })

  it('round-trips person range and building through the page URL', () => {
    const state = { ...emptySearchFormState, minPersons: '20', maxPersons: '50', buildingId: 'b1' }

    const params = toSearchParams(state)

    expect(params.toString()).toBe('minPersons=20&maxPersons=50&buildingId=b1')
    expect(parseSearchParams(params)).toEqual(state)
  })

  it('omits empty fields from the API query', () => {
    expect(toApiQuery(emptySearchFormState).toString()).toBe('')
    expect(toApiQuery({ ...emptySearchFormState, minPersons: '20' }).toString()).toBe('minPersons=20')
  })

  it('emits person range and building for the API', () => {
    const query = toApiQuery({ ...emptySearchFormState, minPersons: '20', maxPersons: '50', buildingId: 'b1' })

    expect(query.get('minPersons')).toBe('20')
    expect(query.get('maxPersons')).toBe('50')
    expect(query.get('buildingId')).toBe('b1')
  })

  it('accepts an empty form and valid person counts', () => {
    expect(validate(emptySearchFormState)).toEqual({})
    expect(validate({ ...emptySearchFormState, minPersons: '1', maxPersons: '1' })).toEqual({})
  })

  it.each(['0', '-1', '1.5', 'abc'])('rejects person count %s with the whole-number message', (value) => {
    expect(validate({ ...emptySearchFormState, minPersons: value }).minPersons).toBe(WHOLE_NUMBER)
    expect(validate({ ...emptySearchFormState, maxPersons: value }).maxPersons).toBe(WHOLE_NUMBER)
  })

  it('rejects a minimum greater than the maximum on the maximum field', () => {
    expect(validate({ ...emptySearchFormState, minPersons: '30', maxPersons: '10' })).toEqual({
      maxPersons: MIN_GREATER_MAX,
    })
  })

  it('round-trips seating arrangement and repeated equipment ids through the page URL', () => {
    const state = { ...emptySearchFormState, seatingArrangement: 'U-Shape', equipmentTypeIds: ['e1', 'e2'] }

    const params = toSearchParams(state)

    expect(params.toString()).toBe('seatingArrangement=U-Shape&equipmentTypeId=e1&equipmentTypeId=e2')
    expect(parseSearchParams(params)).toEqual(state)
  })

  it('emits one equipmentTypeId per selected equipment type and the seating arrangement for the API', () => {
    const query = toApiQuery({ ...emptySearchFormState, seatingArrangement: 'U-Shape', equipmentTypeIds: ['e1', 'e2'] })

    expect(query.get('seatingArrangement')).toBe('U-Shape')
    expect(query.getAll('equipmentTypeId')).toEqual(['e1', 'e2'])
  })

  it('writes barrierFree=true only when checked and restores it', () => {
    const state = { ...emptySearchFormState, barrierFree: true }

    expect(toSearchParams(state).toString()).toBe('barrierFree=true')
    expect(parseSearchParams(toSearchParams(state))).toEqual(state)
    expect(toApiQuery(state).get('barrierFree')).toBe('true')
    expect(toSearchParams(emptySearchFormState).has('barrierFree')).toBe(false)
    expect(toApiQuery(emptySearchFormState).has('barrierFree')).toBe(false)
  })

  const window = { ...emptySearchFormState, date: '2026-10-05', startTime: '11:00', endTime: '12:00' }
  // Expected instants are derived from local dates, so the test holds in every time zone.
  const from = new Date(2026, 9, 5, 11, 0).toISOString()
  const to = new Date(2026, 9, 5, 12, 0).toISOString()

  it('round-trips date and times in their local form through the page URL', () => {
    const params = toSearchParams(window)

    expect(params.toString()).toBe('date=2026-10-05&startTime=11%3A00&endTime=12%3A00')
    expect(parseSearchParams(params)).toEqual(window)
  })

  it('converts a complete window to from/to instants for the API', () => {
    const query = toApiQuery(window)

    expect(query.get('from')).toBe(from)
    expect(query.get('to')).toBe(to)
    expect(query.has('date')).toBe(false)
  })

  it('rejects a partial date/time input', () => {
    expect(validate({ ...emptySearchFormState, date: '2026-10-05' }).date).toBe(
      'Bitte Datum, Von und Bis vollständig angeben.',
    )
    expect(validate({ ...emptySearchFormState, startTime: '11:00', endTime: '12:00' }).date).toBe(
      'Bitte Datum, Von und Bis vollständig angeben.',
    )
  })

  it('rejects an end that is not after the start', () => {
    expect(validate({ ...window, endTime: '11:00' }).endTime).toBe('Bis muss nach Von liegen.')
    expect(validate({ ...window, endTime: '10:00' }).endTime).toBe('Bis muss nach Von liegen.')
    expect(validate(window)).toEqual({})
  })

  it('links results to the room with the window for booking pre-fill', () => {
    const link = new URL(roomLink('r1', window), 'http://localhost')

    expect(link.pathname).toBe('/rooms/r1')
    expect(link.searchParams.get('start')).toBe(from)
    expect(link.searchParams.get('end')).toBe(to)
    expect(roomLink('r1', emptySearchFormState)).toBe('/rooms/r1')
    expect(roomLink('r1', { ...emptySearchFormState, date: '2026-10-05' })).toBe('/rooms/r1')
  })

  it('builds a booking link that always opens the form and pre-fills a complete window', () => {
    expect(bookingLink('r1', emptySearchFormState)).toBe('/rooms/r1?book=true')

    const link = new URL(bookingLink('r1', window), 'http://localhost')
    expect(link.pathname).toBe('/rooms/r1')
    expect(link.searchParams.get('book')).toBe('true')
    expect(link.searchParams.get('start')).toBe(from)
    expect(link.searchParams.get('end')).toBe(to)
  })
})
