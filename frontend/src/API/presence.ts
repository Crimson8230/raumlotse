import { apiRequest } from './client'

export interface PresenceEventResult {
  recorded: boolean
  lastPresenceAt: string | null
}

/** Simulates a motion-sensor event for a room (administrators only, feature 014). */
export function simulatePresence(roomId: string): Promise<PresenceEventResult> {
  return apiRequest<PresenceEventResult>(`/api/admin/rooms/${roomId}/presence-events`, { method: 'POST' })
}
