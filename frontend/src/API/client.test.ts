import { describe, expect, it } from 'vitest'
import { ApiError, formatApiError } from './client'

describe('formatApiError', () => {
  it('returns the general detail message when there are no field errors', () => {
    const err = new ApiError(409, { title: 'Conflict', status: 409, detail: 'Already exists.' })

    expect(formatApiError(err)).toBe('Already exists.')
  })

  it('appends per-field messages when the backend returns them', () => {
    const err = new ApiError(400, {
      title: 'Validation Failed',
      status: 400,
      detail: 'One or more fields are invalid.',
      errors: [
        { field: 'name', message: 'must not be blank' },
        { field: 'seatingArrangements[0].maxCapacity', message: 'must be greater than zero' },
      ],
    })

    expect(formatApiError(err)).toBe(
      'One or more fields are invalid. (name: must not be blank; seatingArrangements[0].maxCapacity: must be greater than zero)',
    )
  })

  it('falls back to a generic message for a non-ApiError, non-Error value', () => {
    expect(formatApiError('boom')).toBe('Something went wrong.')
  })
})
