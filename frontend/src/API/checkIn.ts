import { apiRequest } from './client'
import type { CheckInMethod, CheckInPreview, CheckInResult } from '../types/checkIn'

export function getCheckInPreview(roomId: string): Promise<CheckInPreview> {
  return apiRequest<CheckInPreview>(`/api/rooms/${roomId}/check-in`)
}

export function checkIn(roomId: string, method: CheckInMethod): Promise<CheckInResult> {
  return apiRequest<CheckInResult>(`/api/rooms/${roomId}/check-in`, {
    method: 'POST', body: JSON.stringify({ method }),
  })
}
