import { apiRequest } from './client'
import type { CheckInSettings, CheckInSettingsUpdate } from '../types/checkInSettings'

export function getCheckInSettings(): Promise<CheckInSettings> {
  return apiRequest<CheckInSettings>('/api/admin/check-in-settings')
}

export function saveCheckInSettings(update: CheckInSettingsUpdate): Promise<CheckInSettings> {
  return apiRequest<CheckInSettings>('/api/admin/check-in-settings', { method: 'PUT', body: JSON.stringify(update) })
}
