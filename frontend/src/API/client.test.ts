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
    expect(formatApiError('boom')).toBe('Etwas ist schiefgelaufen.')
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

describe('apiRequest administrator refusals', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    clearCsrfToken()
  })

  function stubStatus(status: number, code: string) {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(JSON.stringify({ title: 'x', status, detail: 'd', code }), { status })),
    )
  }

  it('asks every role consumer to re-read roles when the server refuses with ADMIN_REQUIRED', async () => {
    stubStatus(403, 'ADMIN_REQUIRED')
    const listener = vi.fn()
    window.addEventListener('raumlotse:roles-changed', listener)

    await expect(apiRequest('/api/rooms')).rejects.toBeInstanceOf(ApiError)

    expect(listener).toHaveBeenCalledTimes(1)
    window.removeEventListener('raumlotse:roles-changed', listener)
  })

  it('does not signal a roles change for other refusals', async () => {
    stubStatus(403, 'CSRF_INVALID')
    const listener = vi.fn()
    window.addEventListener('raumlotse:roles-changed', listener)

    await expect(apiRequest('/api/rooms')).rejects.toBeInstanceOf(ApiError)

    expect(listener).not.toHaveBeenCalled()
    window.removeEventListener('raumlotse:roles-changed', listener)
  })

  it('refreshes permissions for a functional 403 without treating private 404 as permission loss', async () => {
    const listener = vi.fn()
    window.addEventListener('raumlotse:roles-changed', listener)
    stubStatus(403, 'PERMISSION_REQUIRED')
    await expect(apiRequest('/api/rooms')).rejects.toMatchObject({ status: 403 })
    expect(listener).toHaveBeenCalledTimes(1)

    stubStatus(404, 'RESERVATION_NOT_FOUND')
    await expect(apiRequest('/api/reservations/hidden')).rejects.toMatchObject({ status: 404 })
    expect(listener).toHaveBeenCalledTimes(1)
    window.removeEventListener('raumlotse:roles-changed', listener)
  })

  it('signals expired authentication separately from permission changes', async () => {
    const rolesListener = vi.fn()
    const authListener = vi.fn()
    window.addEventListener('raumlotse:roles-changed', rolesListener)
    window.addEventListener('raumlotse:auth-expired', authListener)
    stubStatus(401, 'AUTHENTICATION_REQUIRED')

    await expect(apiRequest('/api/rooms')).rejects.toMatchObject({ status: 401 })

    expect(authListener).toHaveBeenCalledTimes(1)
    expect(rolesListener).not.toHaveBeenCalled()
    window.removeEventListener('raumlotse:roles-changed', rolesListener)
    window.removeEventListener('raumlotse:auth-expired', authListener)
  })
})
