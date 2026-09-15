import type { Problem } from '../types/room'

export class ApiError extends Error {
  readonly status: number
  readonly problem?: Problem

  constructor(status: number, problem?: Problem) {
    super(problem?.detail ?? `Request failed with status ${status}`)
    this.status = status
    this.problem = problem
  }
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
  const response = await fetch(path, {
    ...init,
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    throw new ApiError(response.status, await parseErrorBody(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
