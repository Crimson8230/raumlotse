import type { Problem } from '../types/room'

export class ApiError extends Error {
  readonly status: number
  readonly problem?: Problem
  readonly retryAfterSeconds?: number

  constructor(status: number, problem?: Problem, retryAfterSeconds?: number) {
    super(problem?.detail ?? `Request failed with status ${status}`)
    this.status = status
    this.problem = problem
    this.retryAfterSeconds = problem?.retryAfterSeconds ?? retryAfterSeconds
  }
}

let csrfToken: string | undefined

export async function refreshCsrfToken(): Promise<void> {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new ApiError(response.status, await parseErrorBody(response))
  const payload = (await response.json()) as { token: string; headerName: string }
  csrfToken = payload.token
  csrfHeaderName = payload.headerName
}

let csrfHeaderName = 'X-CSRF-TOKEN'

export function clearCsrfToken(): void {
  csrfToken = undefined
}

async function parseErrorBody(response: Response): Promise<Problem | undefined> {
  try {
    return (await response.json()) as Problem
  } catch {
    return undefined
  }
}

/**
 * Human-readable error message for any error thrown by {@link apiRequest}, including the
 * backend's per-field validation messages (`Problem.errors[]`) when present, so an
 * administrator can see exactly which field failed and why (SC-005).
 */
export function formatApiError(err: unknown): string {
  if (err instanceof ApiError) {
    const detail = err.problem?.detail ?? err.message
    const fieldErrors = err.problem?.errors ?? []
    if (fieldErrors.length === 0) {
      return detail
    }
    const fieldList = fieldErrors.map((e) => `${e.field}: ${e.message}`).join('; ')
    return `${detail} (${fieldList})`
  }
  return err instanceof Error ? err.message : 'Something went wrong.'
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase()
  const unsafe = !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)
  if (unsafe && !csrfToken) await refreshCsrfToken()
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(unsafe && csrfToken ? { [csrfHeaderName]: csrfToken } : {}),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    if (response.status === 401 && path !== '/api/auth/login' && path !== '/api/auth/me') {
      clearCsrfToken()
      window.dispatchEvent(new Event('raumlotse:auth-expired'))
    }
    throw new ApiError(response.status, await parseErrorBody(response), Number(response.headers.get('Retry-After')) || undefined)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
