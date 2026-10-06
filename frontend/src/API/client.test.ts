import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiRequest, clearCsrfToken, formatApiError } from './client'

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

describe('apiRequest multipart bodies', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    clearCsrfToken()
  })

  it('does not force a JSON content type for form data so the browser sets the boundary', async () => {
    const fetchMock = vi.fn(async (url: string) =>
      url === '/api/auth/csrf'
        ? new Response(JSON.stringify({ token: 't', headerName: 'X-CSRF-TOKEN' }), { status: 200 })
        : new Response(JSON.stringify({ ok: true }), { status: 200 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/api/floors/f1/map', { method: 'PUT', body: new FormData() })

    const secondCall = fetchMock.mock.calls[1] as unknown as [string, RequestInit]
    const headers = secondCall[1].headers as Record<string, string>
    expect(headers['Content-Type']).toBeUndefined()
    expect(headers['X-CSRF-TOKEN']).toBe('t')
  })
})
